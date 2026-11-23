package com.nordlyse.commonauth.web;

import java.util.Map;

import com.nordlyse.commonauth.auth.Audience;
import com.nordlyse.commonauth.auth.AudienceCatalog;
import com.nordlyse.commonauth.auth.KeycloakTokens;
import com.nordlyse.commonauth.auth.LoginSession;
import com.nordlyse.commonauth.auth.SessionStore;
import com.nordlyse.commonauth.auth.TokenExchangeException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class ExchangeController {

    private static final Logger log = LoggerFactory.getLogger(ExchangeController.class);

    private final SessionStore sessions;
    private final AudienceCatalog catalog;
    private final KeycloakTokens tokens;
    private final RestClient http;

    public ExchangeController(SessionStore sessions, AudienceCatalog catalog, KeycloakTokens tokens, RestClient http) {
        this.sessions = sessions;
        this.catalog = catalog;
        this.tokens = tokens;
        this.http = http;
    }

    @GetMapping("/exchange/authorize")
    public ResponseEntity<Void> authorize(HttpServletRequest request) {
        LoginSession session = sessions.find(CookieSupport.read(request, CookieSupport.SESSION));
        if (session == null) {
            return ResponseEntity.status(401).build();
        }
        String uri = request.getHeader("X-Forwarded-Uri");
        if (uri == null || uri.isBlank()) {
            uri = request.getHeader("X-Forwarded-Prefix");
        }
        Audience audience = catalog.findByRequestPath(uri).orElse(null);
        if (audience == null) {
            return ResponseEntity.ok().build();
        }
        try {
            String accessToken = tokens.tokenFor(session, audience.id());
            return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).build();
        } catch (TokenExchangeException ex) {
            log.warn("Token exchange for {} failed: {}", audience.id(), ex.getMessage());
            return ResponseEntity.ok().build();
        }
    }

    @PostMapping("/exchange/audiences/{audience}")
    public ResponseEntity<Map<String, Object>> warm(@PathVariable String audience, HttpServletRequest request) {
        LoginSession session = sessions.find(CookieSupport.read(request, CookieSupport.SESSION));
        if (session == null) {
            return ResponseEntity.status(401).body(Map.of("ready", false, "detail", "Sign in first."));
        }
        if (catalog.find(audience).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("ready", false, "detail", "Unknown system."));
        }
        try {
            tokens.tokenFor(session, audience);
            return ResponseEntity.ok(Map.of("ready", true, "audience", audience));
        } catch (TokenExchangeException ex) {
            return ResponseEntity.status(502).body(Map.of("ready", false, "audience", audience, "detail", ex.getMessage()));
        }
    }

    @PostMapping("/exchange/forward")
    public ResponseEntity<Map<String, Object>> forward(@RequestBody ForwardRequest forward, HttpServletRequest request) {
        LoginSession session = sessions.find(CookieSupport.read(request, CookieSupport.SESSION));
        if (session == null) {
            return ResponseEntity.status(401).body(Map.of("detail", "Sign in first."));
        }
        Audience audience = catalog.find(forward.audience()).orElse(null);
        if (audience == null) {
            return ResponseEntity.status(404).body(Map.of("detail", "Unknown system."));
        }
        String method = forward.method() == null ? "GET" : forward.method().toUpperCase();
        if (!method.equals("GET") && !method.equals("POST") && !method.equals("PUT") && !method.equals("PATCH") && !method.equals("DELETE")) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Method is not allowed."));
        }
        String path = forward.path() == null ? "" : forward.path();
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Path must stay inside the system."));
        }
        try {
            String accessToken = tokens.tokenFor(session, audience.id());
            String base = audience.internalBase();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String url = base + path;
            RestClient.RequestBodySpec spec = http.method(org.springframework.http.HttpMethod.valueOf(method)).uri(url);
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            if (forward.body() != null) {
                spec.contentType(MediaType.APPLICATION_JSON);
                spec.body(forward.body());
            }
            return spec.exchange((req, response) -> {
                String responseBody = new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (responseBody.length() > 200_000) {
                    responseBody = responseBody.substring(0, 200_000);
                }
                return ResponseEntity.ok(Map.of(
                        "status", response.getStatusCode().value(),
                        "body", responseBody));
            });
        } catch (TokenExchangeException ex) {
            return ResponseEntity.status(502).body(Map.of("detail", ex.getMessage()));
        }
    }

    public record ForwardRequest(String audience, String method, String path, String body) {
    }
}
