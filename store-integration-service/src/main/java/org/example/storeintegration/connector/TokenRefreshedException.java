package org.example.storeintegration.connector;

public class TokenRefreshedException extends RuntimeException {
    private final ShopifyConnector.ExchangeResult result;

    public TokenRefreshedException(String message, ShopifyConnector.ExchangeResult result) {
        super(message);
        this.result = result;
    }

    public ShopifyConnector.ExchangeResult refreshed() {
        return result;
    }
}
