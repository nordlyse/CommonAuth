package com.nordlyse.commonauth.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExchangePermissionSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExchangePermissionSetup.class);

    private final AuthSettings settings;
    private final AudienceCatalog catalog;
    private final ObjectMapper mapper;
    private final RestClient http;

    public ExchangePermissionSetup(AuthSettings settings, AudienceCatalog catalog, ObjectMapper mapper, RestClient http) {
        this.settings = settings;
        this.catalog = catalog;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int attempt = 1; attempt <= 6; attempt++) {
            try {
                addExchangePermissions();
                log.info("Token exchange permissions are ready");
                return;
            } catch (Exception ex) {
                log.warn("Token exchange permission setup attempt {} failed: {}", attempt, ex.getMessage());
                sleep();
            }
        }
        log.error("Token exchange permissions were not added. Exchange calls will report the identity provider error.");
    }

    private void addExchangePermissions() throws Exception {
        String adminToken = adminToken();
        String exchangeClientId = clientUuid(adminToken, settings.exchangeClientId());
        String realmMgmtId = clientUuid(adminToken, "realm-management");
        for (Audience audience : catalog.all()) {
            String targetId = clientUuid(adminToken, audience.id());
            allowExchange(adminToken, realmMgmtId, targetId, exchangeClientId, audience.id());
            log.info("Token exchange permission is ready for {}", audience.id());
        }
    }

    private void allowExchange(String adminToken, String realmMgmtId, String targetClientUuid, String exchangeClientUuid, String audienceId) throws Exception {
        HttpResult enabled = put(settings.adminRealmUrl() + "/clients/" + targetClientUuid + "/management/permissions", adminToken, "{\"enabled\":true}");
        if (enabled.status() < 200 || enabled.status() >= 300) {
            throw new IllegalStateException("Permission switch for " + audienceId + " returned " + enabled.status() + " " + clip(enabled.body()));
        }
        JsonNode scopes = mapper.readTree(enabled.body()).path("scopePermissions");
        String permissionId = scopes.path("token-exchange").asText("");
        if (permissionId.isBlank()) {
            throw new IllegalStateException("No token-exchange permission for " + audienceId);
        }
        String policyName = "exchange-to-" + audienceId;
        String policyId = findPolicyId(adminToken, realmMgmtId, policyName);
        if (policyId == null) {
            ObjectNode policy = JsonNodeFactory.instance.objectNode();
            policy.put("name", policyName);
            policy.put("logic", "POSITIVE");
            policy.put("decisionStrategy", "UNANIMOUS");
            policy.putArray("clients").add(exchangeClientUuid);
            HttpResult added = post(authz(realmMgmtId) + "/policy/client", adminToken, policy.toString());
            if (added.status() < 200 || added.status() >= 300) {
                throw new IllegalStateException("Policy for " + audienceId + " returned " + added.status() + " " + clip(added.body()));
            }
            policyId = mapper.readTree(added.body()).path("id").asText("");
            if (policyId.isBlank()) {
                throw new IllegalStateException("Policy for " + audienceId + " has no id");
            }
        }
        String permissionUrl = authz(realmMgmtId) + "/permission/scope/" + permissionId;
        HttpResult current = get(permissionUrl, adminToken);
        if (current.status() < 200 || current.status() >= 300) {
            throw new IllegalStateException("Permission read for " + audienceId + " returned " + current.status());
        }
        ObjectNode permission = (ObjectNode) mapper.readTree(current.body());
        ArrayNode policies = permission.withArray("policies");
        boolean present = false;
        for (JsonNode policy : policies) {
            if (policyId.equals(policy.asText())) {
                present = true;
            }
        }
        if (!present) {
            policies.add(policyId);
            HttpResult saved = put(permissionUrl, adminToken, permission.toString());
            if (saved.status() < 200 || saved.status() >= 300) {
                throw new IllegalStateException("Permission save for " + audienceId + " returned " + saved.status() + " " + clip(saved.body()));
            }
        }
    }

    private String findPolicyId(String adminToken, String realmMgmtId, String policyName) throws Exception {
        HttpResult listed = get(authz(realmMgmtId) + "/policy?name=" + policyName, adminToken);
        if (listed.status() < 200 || listed.status() >= 300) {
            return null;
        }
        JsonNode policies = mapper.readTree(listed.body());
        if (!policies.isArray()) {
            return null;
        }
        for (JsonNode policy : policies) {
            if (policyName.equals(policy.path("name").asText())) {
                String id = policy.path("id").asText("");
                return id.isBlank() ? null : id;
            }
        }
        return null;
    }

    private String clientUuid(String adminToken, String clientId) throws Exception {
        HttpResult listed = get(settings.adminRealmUrl() + "/clients?clientId=" + clientId, adminToken);
        if (listed.status() < 200 || listed.status() >= 300) {
            throw new IllegalStateException("Client lookup for " + clientId + " returned " + listed.status());
        }
        JsonNode clients = mapper.readTree(listed.body());
        if (!clients.isArray() || clients.isEmpty()) {
            throw new IllegalStateException("Client " + clientId + " is missing");
        }
        String id = clients.get(0).path("id").asText("");
        if (id.isBlank()) {
            throw new IllegalStateException("Client " + clientId + " has no id");
        }
        return id;
    }

    private String adminToken() throws Exception {
        String form = "grant_type=password&client_id=admin-cli&username="
                + settings.adminUser()
                + "&password="
                + settings.adminPassword();
        HttpResult result = post(settings.adminTokenUrl(), null, form);
        if (result.status() < 200 || result.status() >= 300) {
            throw new IllegalStateException("Admin sign-in returned " + result.status());
        }
        String token = mapper.readTree(result.body()).path("access_token").asText("");
        if (token.isBlank()) {
            throw new IllegalStateException("Admin sign-in returned no access token");
        }
        return token;
    }

    private String authz(String realmMgmtId) {
        return settings.adminRealmUrl() + "/clients/" + realmMgmtId + "/authz/resource-server";
    }

    private HttpResult get(String url, String token) {
        return exchange("GET", url, token, null);
    }

    private HttpResult post(String url, String token, String body) {
        return exchange("POST", url, token, body);
    }

    private HttpResult put(String url, String token, String body) {
        return exchange("PUT", url, token, body);
    }

    private HttpResult exchange(String method, String url, String token, String body) {
        RestClient.RequestBodySpec spec = http.method(org.springframework.http.HttpMethod.valueOf(method)).uri(url);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        spec.contentType(body != null && body.startsWith("{")
                ? MediaType.APPLICATION_JSON
                : MediaType.APPLICATION_FORM_URLENCODED);
        if (body != null) {
            spec.body(body);
        }
        return spec.exchange((request, response) -> new HttpResult(
                response.getStatusCode().value(),
                new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String clip(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static void sleep() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record HttpResult(int status, String body) {
    }
}
