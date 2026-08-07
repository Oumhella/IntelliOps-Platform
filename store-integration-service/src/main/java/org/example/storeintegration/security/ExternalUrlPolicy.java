package org.example.storeintegration.security;

import org.example.storeintegration.config.IntegrationProperties;
import org.springframework.stereotype.Component;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Component
public class ExternalUrlPolicy {
    private final boolean allowPrivateHosts;
    public ExternalUrlPolicy(IntegrationProperties properties) { this.allowPrivateHosts = properties.allowPrivateStoreHosts(); }

    public URI requirePublicHttpsStore(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Store URL must be a plain HTTPS origin without credentials, port, query, or fragment.");
            }
            verifyHost(uri.getHost());
            return URI.create("https://" + uri.getHost().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalArgumentException("Store URL is not valid.", exception); }
    }

    public URI requireShopifyStore(String value) {
        String candidate = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        candidate = candidate.replaceFirst("^https://", "");
        candidate = candidate.replaceFirst("/$", "");
        if (!candidate.matches("[a-z0-9][a-z0-9-]*\\.myshopify\\.com")) {
            throw new IllegalArgumentException("Use the permanent shop-name.myshopify.com domain.");
        }
        return URI.create("https://" + candidate);
    }

    public void verifyHost(String host) throws Exception {
        if (allowPrivateHosts) return;
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocalV6(address)) {
                throw new IllegalArgumentException("Private, loopback, link-local, and reserved store hosts are not allowed.");
            }
        }
    }

    private boolean isUniqueLocalV6(InetAddress address) {
        return address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc;
    }
}
