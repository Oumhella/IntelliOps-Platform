package org.example.notification_service.strategy;


import org.example.notification_service.entity.TypeNotification;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationChannelFactory {

    private final Map<TypeNotification, NotificationChannelStrategy> strategies;

    public NotificationChannelFactory(List<NotificationChannelStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(NotificationChannelStrategy::getType, Function.identity()));
    }

    public NotificationChannelStrategy getStrategy(TypeNotification type) {
        NotificationChannelStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported notification channel: " + type);
        }
        return strategy;
    }
}