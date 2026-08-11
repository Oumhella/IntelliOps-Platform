package org.example.storeintegration.connector;
import org.example.storeintegration.domain.StorePlatform;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
@Component
public class ConnectorFactory {
    private final Map<StorePlatform, StoreConnector> connectors = new EnumMap<>(StorePlatform.class);
    public ConnectorFactory(List<StoreConnector> values) { values.forEach(value -> connectors.put(value.platform(), value)); }
    public StoreConnector require(StorePlatform platform) { StoreConnector value = connectors.get(platform); if (value == null) throw new IllegalStateException("No connector for " + platform); return value; }
}
