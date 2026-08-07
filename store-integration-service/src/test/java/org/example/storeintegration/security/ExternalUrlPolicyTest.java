package org.example.storeintegration.security;

import org.example.storeintegration.config.IntegrationProperties;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalUrlPolicyTest {
    private final ExternalUrlPolicy policy = new ExternalUrlPolicy(
            new IntegrationProperties("https://api.example.com", "https://app.example.com", false, 60, "MAD", null, "unused"));

    @Test
    void normalizesPermanentShopifyDomain() {
        assertThat(policy.requireShopifyStore("HTTPS://Demo-Shop.MyShopify.com/"))
                .isEqualTo(URI.create("https://demo-shop.myshopify.com"));
    }

    @Test
    void rejectsNonShopifyAndPrivateWooCommerceOrigins() {
        assertThatThrownBy(() -> policy.requireShopifyStore("https://merchant.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requirePublicHttpsStore("http://shop.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requirePublicHttpsStore("https://127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
