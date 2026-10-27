package com.company.payment.common.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where each client may send the shopper back after a 3DS challenge ("app.security.return-urls":
 * "client1:https://shop.example.com/checkout/,client2:https://other.example.com/"). A client may have
 * several entries. Without this check, returnUrl would be an open redirect on our domain.
 *
 * A returnUrl is allowed when scheme, host and port equal one of the client's entries and its
 * (normalized) path starts with the entry's path: "https://shop.example.com/checkout/" allows
 * ".../checkout/done?order=1" but not ".../admin" or "https://shop.example.com.evil.com/".
 */
public class ReturnUrlAllowlist {

    private final Map<String, List<URI>> entriesByClient;

    ReturnUrlAllowlist(Map<String, List<URI>> entriesByClient) {
        this.entriesByClient = entriesByClient;
    }

    /** "client:url,client:url" (may be empty). Fails at startup on a bad value. */
    static ReturnUrlAllowlist parse(String value) {
        Map<String, List<URI>> result = new LinkedHashMap<>();
        if (value != null && !value.isBlank()) {
            for (String entry : value.split(",")) {
                String[] parts = entry.trim().split(":", 2);
                URI uri = parts.length == 2 ? toUri(parts[1].trim()) : null;
                if (parts[0].isBlank() || uri == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                    throw new IllegalStateException("app.security.return-urls must be 'client:http(s)://host/path/'"
                            + " entries without query or fragment, separated by commas: " + entry.trim());
                }
                result.computeIfAbsent(parts[0].trim(), client -> new ArrayList<>()).add(uri);
            }
        }
        return new ReturnUrlAllowlist(result);
    }

    public boolean isAllowed(String clientId, String returnUrl) {
        URI url = toUri(returnUrl);
        if (url == null || url.getRawUserInfo() != null) {
            return false;
        }
        return entriesByClient.getOrDefault(clientId, List.of()).stream().anyMatch(entry -> matches(entry, url));
    }

    private static boolean matches(URI entry, URI url) {
        if (!entry.getScheme().equalsIgnoreCase(url.getScheme())
                || !entry.getHost().equalsIgnoreCase(url.getHost())
                || port(entry) != port(url)) {
            return false;
        }
        String allowed = path(entry);
        String path = path(url);
        return allowed.endsWith("/") ? path.startsWith(allowed) : path.equals(allowed) || path.startsWith(allowed + "/");
    }

    /** Absolute http(s) URL with a host, normalized ("/a/../b" -> "/b"); null if it is not one. */
    private static URI toUri(String value) {
        if (value == null) {
            return null;
        }
        try {
            URI uri = new URI(value).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null ? uri : null;
        } catch (java.net.URISyntaxException ex) {
            return null;
        }
    }

    private static int port(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private static String path(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.isEmpty() ? "/" : path;
    }
}
