package com.nordlyse.commonauth.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class AudienceCatalog {

    private final List<Audience> audiences = List.of(
            new Audience("sharkord", "Sharkord", "/sharkord/", "http://sharkord:4991"),
            new Audience("crm", "CRM", "/crm/", "http://crm:3001"),
            new Audience("clearflask", "ClearFlask", "/clearflask/", "http://clearflask-server:8080"),
            new Audience("alexandrie", "Alexandrie", "/alexandrie/", "http://alexandrie-backend:8201"),
            new Audience("mentingo", "Mentingo", "/mentingo/", "http://mentingo-api:3000")
    );

    public List<Audience> all() {
        return audiences;
    }

    public Optional<Audience> find(String id) {
        return audiences.stream().filter(audience -> audience.id().equals(id)).findFirst();
    }

    public Optional<Audience> findByRequestPath(String uri) {
        String path = uri == null ? "" : uri;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.startsWith("/crm-api") || path.startsWith("/crm")) {
            return find("crm");
        }
        if (path.startsWith("/mentingo-api") || path.startsWith("/mentingo")) {
            return find("mentingo");
        }
        if (path.startsWith("/alexandrie-api") || path.startsWith("/alexandrie")) {
            return find("alexandrie");
        }
        if (path.startsWith("/clearflask")) {
            return find("clearflask");
        }
        if (path.startsWith("/sharkord")) {
            return find("sharkord");
        }
        return Optional.empty();
    }
}
