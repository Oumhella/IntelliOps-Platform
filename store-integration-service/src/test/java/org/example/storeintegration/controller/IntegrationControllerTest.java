package org.example.storeintegration.controller;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IntegrationControllerTest {
    @Test
    void shopifyStateCookieIsBoundToSecureCallbackPath() {
        assertThat(IntegrationController.shopifyStateCookie("browser-state", 600).toString())
                .contains(IntegrationController.SHOPIFY_STATE_COOKIE + "=browser-state", "Path=/api/v1/integrations/oauth/shopify/callback", "Secure", "HttpOnly", "SameSite=Lax");
    }
}
