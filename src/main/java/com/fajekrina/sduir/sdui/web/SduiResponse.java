package com.fajekrina.sduir.sdui.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/// A finished SDUI answer: status, headers and the body bytes exactly as they must reach the
/// client. Handlers build it; the service's controller returns {@link #toEntity()} as-is, so
/// the service's message converters, its ObjectMapper and its response wrappers never touch the
/// body (a signed envelope keeps its bytes, and so its signature).
public final class SduiResponse {
    private final int status;
    private final HttpHeaders headers = new HttpHeaders();
    private final byte[] body;

    private SduiResponse(int status, MediaType type, byte[] body) {
        this.status = status;
        this.body = body;
        if (type != null) headers.setContentType(type);
    }

    /// `200` with `body` as JSON (a String is taken as JSON text already)
    public static SduiResponse ok(Object body) { return json(200, body); }

    /// `status` with `body` as JSON (a String is taken as JSON text already)
    public static SduiResponse json(int status, Object body) {
        String text = body instanceof String s ? s : Json.write(body);
        return new SduiResponse(status, MediaType.APPLICATION_JSON, text.getBytes(StandardCharsets.UTF_8));
    }

    /// `status` with raw bytes of `type`
    public static SduiResponse bytes(int status, MediaType type, byte[] body) { return new SduiResponse(status, type, body); }

    /// `status` without a body (204, 304)
    public static SduiResponse empty(int status) { return new SduiResponse(status, null, null); }

    /// Stored bytes as-is (envelopes, manifests) with an ETag and a cache policy; `304` when
    /// `If-None-Match` matches.
    public static SduiResponse stored(byte[] bytes, String etag, String ifNoneMatch, CacheControl cache) {
        String quoted = "\"" + etag + "\"";
        SduiResponse out = matches(ifNoneMatch, quoted) ? empty(304) : new SduiResponse(200, MediaType.APPLICATION_JSON, bytes);
        return out.header(HttpHeaders.ETAG, quoted).cache(cache);
    }

    /// whether `If-None-Match` names `quotedEtag` (strong, weak or `*`)
    public static boolean matches(String ifNoneMatch, String quotedEtag) {
        return ifNoneMatch != null && (ifNoneMatch.equals(quotedEtag) || ifNoneMatch.equals("W/" + quotedEtag) || ifNoneMatch.equals("*"));
    }

    public SduiResponse header(String name, String value) {
        headers.add(name, value);
        return this;
    }

    public SduiResponse cache(CacheControl cache) {
        headers.setCacheControl(cache);
        return this;
    }

    public int status() { return status; }

    public HttpHeaders headers() { return headers; }

    /// the body bytes, or null when there is none
    public byte[] body() { return body; }

    /// the body as UTF-8 text ("" when there is none)
    public String text() { return body == null ? "" : new String(body, StandardCharsets.UTF_8); }

    /// What the controller returns: the bytes as they are.
    public ResponseEntity<byte[]> toEntity() {
        return ResponseEntity.status(HttpStatus.valueOf(status)).headers(headers).body(body);
    }
}
