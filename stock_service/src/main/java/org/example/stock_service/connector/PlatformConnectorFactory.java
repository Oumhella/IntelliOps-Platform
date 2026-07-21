package org.example.stock_service.connector;

import org.example.stock_service.entity.TypePlateforme;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PlatformConnectorFactory {

    private final Map<TypePlateforme, ExternalPlatformConnector> connectors;

    public PlatformConnectorFactory(List<ExternalPlatformConnector> connectorList) {
        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(ExternalPlatformConnector::getSupportedPlatform, Function.identity()));
    }

    public ExternalPlatformConnector getConnector(TypePlateforme plateforme) {
        ExternalPlatformConnector connector = connectors.get(plateforme);
        if (connector == null) {
            throw new IllegalArgumentException("Plateforme non supportée : " + plateforme);
        }
        return connector;
    }
}