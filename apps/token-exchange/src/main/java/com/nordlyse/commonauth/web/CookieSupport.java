package com.nordlyse.commonauth.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class CookieSupport {

    public static final String SESSION = "common_session";
    public static final String STATE = "common_login_state";

    private CookieSupport() {
    }

    public static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static String sessionCookie(String id) {
        return ResponseCookie.from(SESSION, id)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(8 * 60 * 60)
                .build()
                .toString();
    }

    public static String stateCookie(String state) {
        return ResponseCookie.from(STATE, state)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(10 * 60)
                .build()
                .toString();
    }

    public static String clear(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build()
                .toString();
    }

    public static void add(jakarta.servlet.http.HttpServletResponse response, String cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie);
    }
}
