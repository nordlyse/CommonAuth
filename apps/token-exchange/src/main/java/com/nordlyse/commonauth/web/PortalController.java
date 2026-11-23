package com.nordlyse.commonauth.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.nordlyse.commonauth.auth.AudienceCatalog;
import com.nordlyse.commonauth.auth.LoginSession;
import com.nordlyse.commonauth.auth.SessionStore;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PortalController {

    private final SessionStore sessions;
    private final AudienceCatalog catalog;

    public PortalController(SessionStore sessions, AudienceCatalog catalog) {
        this.sessions = sessions;
        this.catalog = catalog;
    }

    @GetMapping("/")
    public String home() {
        return "forward:/home.html";
    }

    @GetMapping("/session")
    @ResponseBody
    public Map<String, Object> session(HttpServletRequest request) {
        LoginSession session = sessions.find(CookieSupport.read(request, CookieSupport.SESSION));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signedIn", session != null);
        body.put("name", session == null ? "" : session.displayName());
        body.put("email", session == null ? "" : session.email());
        body.put("audiences", catalog.all().stream().map(audience -> Map.of(
                "id", audience.id(),
                "label", audience.label(),
                "path", audience.path()
        )).toList());
        return body;
    }
}
