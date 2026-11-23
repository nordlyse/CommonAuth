package com.nordlyse.commonauth.auth;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class SessionStore {

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, LoginSession> sessions = new ConcurrentHashMap<>();

    public LoginSession addSession() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String id = java.util.HexFormat.of().formatHex(bytes);
        LoginSession session = new LoginSession(id);
        sessions.put(id, session);
        return session;
    }

    public LoginSession find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return sessions.get(id);
    }

    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }
}
