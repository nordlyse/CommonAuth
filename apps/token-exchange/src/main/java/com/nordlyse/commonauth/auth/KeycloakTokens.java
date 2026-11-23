package com.nordlyse.commonauth.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class KeycloakTokens {

    private final AuthSettings settings;
    private final ObjectMapper mapper;
    private final RestClient http;

    public KeycloakTokens(AuthSettings settings, ObjectMapper mapper, RestClient http) {
        this.settings = settings;
        this.mapper = mapper;
        this.http = http;
    }

    public void addCodeGrant(LoginSession session, String code) {
        JsonNode token = postForm(settings.internalTokenUrl(), form(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", settings.redirectUri(),
                "client_id", settings.portalClientId(),
                "client_secret", settings.portalClientSecret()));
        String accessToken = requiredText(token, "access_token");
        long expiresAt = expiresAt(token);
        Account account = account(accessToken);
        synchronized (session) {
            session.addSignIn(accessToken, token.path("refresh_token").asText(""), token.path("id_token").asText(""), expiresAt, account.name(), account.email());
        }
    }

    public String tokenFor(LoginSession session, String audience) {
        synchronized (session) {
            refreshIfDue(session);
            LoginSession.HeldToken held = session.exchangedFor(audience);
            long now = System.currentTimeMillis();
            if (held != null && held.expiresAtEpochMs() > now + 15_000) {
                return held.accessToken();
            }
            JsonNode exchanged = postForm(settings.internalTokenUrl(), form(
                    "grant_type", "urn:ietf:params:oauth:grant-type:token-exchange",
                    "subject_token", session.accessToken(),
                    "subject_token_type", "urn:ietf:params:oauth:token-type:access_token",
                    "requested_token_type", "urn:ietf:params:oauth:token-type:access_token",
                    "audience", audience,
                    "client_id", settings.exchangeClientId(),
                    "client_secret", settings.exchangeClientSecret()));
            String accessToken = requiredText(exchanged, "access_token");
            session.addExchanged(audience, new LoginSession.HeldToken(accessToken, expiresAt(exchanged)));
            return accessToken;
        }
    }

    private void refreshIfDue(LoginSession session) {
        if (session.accessExpiresAtEpochMs() > System.currentTimeMillis() + 15_000) {
            return;
        }
        if (session.refreshToken().isBlank()) {
            throw new TokenExchangeException("The sign-in has expired.");
        }
        JsonNode token = postForm(settings.internalTokenUrl(), form(
                "grant_type", "refresh_token",
                "refresh_token", session.refreshToken(),
                "client_id", settings.portalClientId(),
                "client_secret", settings.portalClientSecret()));
        session.addAccess(requiredText(token, "access_token"), token.path("refresh_token").asText(""), expiresAt(token));
    }

    private Account account(String accessToken) {
        try {
            String body = http.get()
                    .uri(settings.internalUserInfoUrl())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            JsonNode user = mapper.readTree(body == null ? "{}" : body);
            String name = user.path("preferred_username").asText("");
            if (name.isBlank()) {
                name = user.path("name").asText("");
            }
            return new Account(name, user.path("email").asText(""));
        } catch (Exception ex) {
            return new Account("", "");
        }
    }

    private record Account(String name, String email) {
    }

    private JsonNode postForm(String url, String form) {
        try {
            String body = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException ex) {
            throw new TokenExchangeException(errorDetail(ex.getResponseBodyAsString()));
        } catch (TokenExchangeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TokenExchangeException("The identity provider did not answer.");
        }
    }

    private static String errorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "The identity provider rejected the token exchange.";
        }
        String trimmed = body.length() > 400 ? body.substring(0, 400) : body;
        return trimmed;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new TokenExchangeException("The identity provider returned no access token.");
        }
        return value;
    }

    private static long expiresAt(JsonNode token) {
        long seconds = token.path("expires_in").asLong(60);
        return System.currentTimeMillis() + seconds * 1000;
    }

    private static String form(String... pairs) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                builder.append('&');
            }
            builder.append(encode(pairs[i])).append('=').append(encode(pairs[i + 1]));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
