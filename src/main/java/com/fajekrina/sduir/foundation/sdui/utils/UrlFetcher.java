package com.fajekrina.sduir.foundation.sdui.utils;

import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.properties.SduiProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/// Copies a picture from a link a maker pasted (server side, once). Guarded against SSRF:
/// https only, default port, no credentials in the URL, every address of the host must be
/// public (no loopback, private, link-local, CGNAT, multicast or unique-local ranges), no
/// redirects, short timeouts and a size cap. (Target: an egress proxy with an allow-list;
/// resolving and connecting separately leaves a DNS-rebinding window.)
@Component
public class UrlFetcher {
    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private final boolean allowPrivate;

    public UrlFetcher(SduiProperties properties) {
        this.allowPrivate = properties.getAssets().getUrlImport().isAllowPrivateHosts();
    }

    public byte[] fetch(String url, int maxBytes) {
        URI uri = check(url);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", "image/png,image/jpeg,image/webp,image/gif").GET().build();
            HttpResponse<InputStream> res = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                if (res.statusCode() / 100 == 3) throw SduiException.invalid("the link redirects; paste the picture's final address");
                if (res.statusCode() != 200) throw SduiException.invalid("the link answered HTTP " + res.statusCode());
                byte[] bytes = in.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) throw SduiException.invalid("the picture is larger than " + maxBytes / 1024 + " KB");
                return bytes;
            }
        } catch (IOException e) {
            throw SduiException.invalid("could not download the picture: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw SduiException.invalid("download interrupted");
        }
    }

    /// The link as a URI we may fetch, else 422.
    URI check(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw SduiException.invalid("not a link: " + url);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw SduiException.invalid("a picture link must use https");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw SduiException.invalid("the link has no host");
        if (uri.getRawUserInfo() != null) throw SduiException.invalid("the link must not carry credentials");
        if (uri.getPort() != -1 && uri.getPort() != 443) throw SduiException.invalid("the link must use the default https port");
        if (!allowPrivate) {
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(uri.getHost());
            } catch (UnknownHostException e) {
                throw SduiException.invalid("unknown host " + uri.getHost());
            }
            for (InetAddress a : addresses) if (!isPublic(a)) throw SduiException.invalid("the link points to a private address");
        }
        return uri;
    }

    static boolean isPublic(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) return false;
        byte[] b = a.getAddress();
        if (a instanceof Inet6Address) {
            if ((b[0] & 0xFE) == 0xFC) return false;                                    // fc00::/7 unique local
            boolean mapped = true;                                                       // ::ffff:a.b.c.d
            for (int i = 0; i < 10; i++) mapped &= b[i] == 0;
            if (mapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                try { return isPublic(InetAddress.getByAddress(java.util.Arrays.copyOfRange(b, 12, 16))); } catch (UnknownHostException e) { return false; }
            }
            return true;
        }
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        if (first == 0) return false;                                                    // 0.0.0.0/8
        if (first == 100 && second >= 64 && second <= 127) return false;                // 100.64.0.0/10 CGNAT
        if (first == 192 && second == 0 && (b[2] & 0xFF) == 0) return false;            // 192.0.0.0/24
        if (first == 198 && (second == 18 || second == 19)) return false;               // 198.18.0.0/15 benchmarking
        return first < 224;                                                              // multicast / reserved
    }
}
