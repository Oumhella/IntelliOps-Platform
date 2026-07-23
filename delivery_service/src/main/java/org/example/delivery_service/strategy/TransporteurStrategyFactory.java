package org.example.delivery_service.strategy;

import org.example.delivery_service.entity.TypeTransporteur;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TransporteurStrategyFactory {

    private final Map<TypeTransporteur, TransporteurStrategy> strategies;

    public TransporteurStrategyFactory(List<TransporteurStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(TransporteurStrategy::getType, Function.identity()));
    }

    public TransporteurStrategy getStrategy(TypeTransporteur type) {
        TransporteurStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported shipping strategy: " + type);
        }
        return strategy;
    }}
