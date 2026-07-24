package org.example.paiment_service.gateway;

import org.example.paiment_service.entity.ModePaiement;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PaymentGatewayFactory {

    private final Map<ModePaiement, PaymentGatewayProvider> providers;

    public PaymentGatewayFactory(List<PaymentGatewayProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(PaymentGatewayProvider::getSupportedMode, Function.identity()));
    }

    public PaymentGatewayProvider getProvider(ModePaiement mode) {
        PaymentGatewayProvider provider = providers.get(mode);
        if (provider == null && mode != ModePaiement.CASH_ON_DELIVERY) {
            throw new IllegalArgumentException("Mode de paiement non supporté : " + mode);
        }
        return provider;
    }
}