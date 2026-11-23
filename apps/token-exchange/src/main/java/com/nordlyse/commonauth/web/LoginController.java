package com.nordlyse.commonauth.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import com.nordlyse.commonauth.auth.AuthSettings;
import com.nordlyse.commonauth.auth.KeycloakTokens;
import com.nordlyse.commonauth.auth.LoginSession;
import com.nordlyse.commonauth.auth.SessionStore;
import com.nordlyse.commonauth.auth.TokenExchangeException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    private final AuthSettings settings;
    private final SessionStore sessions;
    private final KeycloakTokens tokens;
    private final SecureRandom random = new SecureRandom();

    public LoginController(AuthSettings settings, SessionStore sessions, KeycloakTokens tokens) {
        this.settings = settings;
        this.sessions = sessions;
        this.tokens = tokens;
    }

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws java.io.IOException {
        String state = java.util.HexFormat.of().formatHex(randomBytes());
        CookieSupport.add(response, CookieSupport.stateCookie(state));
        String url = settings.browserAuthUrl()
                + "?client_id=" + encode(settings.portalClientId())
                + "&redirect_uri=" + encode(settings.redirectUri())
                + "&response_type=code"
                + "&scope=" + encode("openid profile email")
                + "&state=" + encode(state);
        response.sendRedirect(url);
    }

    @GetMapping("/login/callback")
    public void callback(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        String expected = CookieSupport.read(request, CookieSupport.STATE);
        String state = request.getParameter("state");
        String code = request.getParameter("code");
        CookieSupport.add(response, CookieSupport.clear(CookieSupport.STATE));
        if (expected == null || !expected.equals(state) || code == null || code.isBlank()) {
            response.sendRedirect("/?sign_in=failed");
            return;
        }
        LoginSession session = sessions.addSession();
        try {
            tokens.addCodeGrant(session, code);
        } catch (TokenExchangeException ex) {
            sessions.remove(session.id());
            response.sendRedirect("/?sign_in=failed");
            return;
        }
        CookieSupport.add(response, CookieSupport.sessionCookie(session.id()));
        response.sendRedirect("/");
    }

    @GetMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        String sessionId = CookieSupport.read(request, CookieSupport.SESSION);
        LoginSession session = sessions.find(sessionId);
        sessions.remove(sessionId);
        CookieSupport.add(response, CookieSupport.clear(CookieSupport.SESSION));
        String url = settings.browserLogoutUrl()
                + "?client_id=" + encode(settings.portalClientId())
                + "&post_logout_redirect_uri=" + encode(settings.publicUrl() + "/");
        if (session != null && !session.idToken().isBlank()) {
            url += "&id_token_hint=" + encode(session.idToken());
        }
        response.sendRedirect(url);
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return bytes;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
