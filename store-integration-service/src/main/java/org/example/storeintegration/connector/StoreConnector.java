package org.example.storeintegration.connector;

import org.example.storeintegration.domain.StorePlatform;
import org.example.storeintegration.dto.IntegrationDtos.ExternalProduct;
import org.example.storeintegration.security.CredentialCipher.StoreCredentials;
import java.net.URI;
import java.util.List;

public interface StoreConnector {
    StorePlatform platform();
    void verifyConnection(URI storeUrl, StoreCredentials credentials);
    List<ExternalProduct> listProducts(URI storeUrl, StoreCredentials credentials);
    void registerOrderWebhook(URI storeUrl, StoreCredentials credentials, String deliveryUrl);
}
