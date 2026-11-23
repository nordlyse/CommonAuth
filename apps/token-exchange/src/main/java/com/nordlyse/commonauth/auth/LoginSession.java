package com.nordlyse.commonauth.auth;

import java.util.HashMap;
import java.util.Map;

public final class LoginSession {

    public record HeldToken(String accessToken, long expiresAtEpochMs) {
    }

    private final String id;
    private String accessToken = "";
    private String refreshToken = "";
    private String idToken = "";
    private long accessExpiresAtEpochMs;
    private String displayName = "";
    private String email = "";
    private final Map<String, HeldToken> exchanged = new HashMap<>();

    public LoginSession(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public synchronized String accessToken() {
        return accessToken;
    }

    public synchronized String refreshToken() {
        return refreshToken;
    }

    public synchronized String idToken() {
        return idToken;
    }

    public synchronized long accessExpiresAtEpochMs() {
        return accessExpiresAtEpochMs;
    }

    public synchronized String displayName() {
        return displayName;
    }

    public synchronized String email() {
        return email;
    }

    public synchronized void addSignIn(String accessToken, String refreshToken, String idToken, long accessExpiresAtEpochMs, String displayName, String email) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.idToken = idToken == null ? "" : idToken;
        this.accessExpiresAtEpochMs = accessExpiresAtEpochMs;
        this.displayName = displayName == null || displayName.isBlank() ? "demo" : displayName;
        this.email = email == null ? "" : email;
        this.exchanged.clear();
    }

    public synchronized void addAccess(String accessToken, String refreshToken, long accessExpiresAtEpochMs) {
        this.accessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            this.refreshToken = refreshToken;
        }
        this.accessExpiresAtEpochMs = accessExpiresAtEpochMs;
        this.exchanged.clear();
    }

    public synchronized HeldToken exchangedFor(String audience) {
        return exchanged.get(audience);
    }

    public synchronized void addExchanged(String audience, HeldToken heldToken) {
        exchanged.put(audience, heldToken);
    }
}
