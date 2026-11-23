package com.nordlyse.commonauth.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthSettings {

    private final String publicUrl;
    private final String keycloakInternalUrl;
    private final String realm;
    private final String portalClientId;
    private final String portalClientSecret;
    private final String exchangeClientId;
    private final String exchangeClientSecret;
    private final String adminUser;
    private final String adminPassword;

    public AuthSettings(
            @Value("${commonauth.public-url}") String publicUrl,
            @Value("${commonauth.keycloak-internal-url}") String keycloakInternalUrl,
            @Value("${commonauth.realm}") String realm,
            @Value("${commonauth.portal-client-id}") String portalClientId,
            @Value("${commonauth.portal-client-secret}") String portalClientSecret,
            @Value("${commonauth.exchange-client-id}") String exchangeClientId,
            @Value("${commonauth.exchange-client-secret}") String exchangeClientSecret,
            @Value("${commonauth.admin-user}") String adminUser,
            @Value("${commonauth.admin-password}") String adminPassword) {
        this.publicUrl = stripSlash(publicUrl);
        this.keycloakInternalUrl = stripSlash(keycloakInternalUrl);
        this.realm = realm;
        this.portalClientId = portalClientId;
        this.portalClientSecret = portalClientSecret;
        this.exchangeClientId = exchangeClientId;
        this.exchangeClientSecret = exchangeClientSecret;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    public String publicUrl() {
        return publicUrl;
    }

    public String realm() {
        return realm;
    }

    public String portalClientId() {
        return portalClientId;
    }

    public String portalClientSecret() {
        return portalClientSecret;
    }

    public String exchangeClientId() {
        return exchangeClientId;
    }

    public String exchangeClientSecret() {
        return exchangeClientSecret;
    }

    public String adminUser() {
        return adminUser;
    }

    public String adminPassword() {
        return adminPassword;
    }

    public String redirectUri() {
        return publicUrl + "/login/callback";
    }

    public String browserAuthUrl() {
        return publicUrl + "/auth/realms/" + realm + "/protocol/openid-connect/auth";
    }

    public String browserLogoutUrl() {
        return publicUrl + "/auth/realms/" + realm + "/protocol/openid-connect/logout";
    }

    public String internalIssuer() {
        return keycloakInternalUrl + "/realms/" + realm;
    }

    public String internalTokenUrl() {
        return internalIssuer() + "/protocol/openid-connect/token";
    }

    public String internalUserInfoUrl() {
        return internalIssuer() + "/protocol/openid-connect/userinfo";
    }

    public String adminTokenUrl() {
        return keycloakInternalUrl + "/realms/master/protocol/openid-connect/token";
    }

    public String adminRealmUrl() {
        return keycloakInternalUrl + "/admin/realms/" + realm;
    }

    private static String stripSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
