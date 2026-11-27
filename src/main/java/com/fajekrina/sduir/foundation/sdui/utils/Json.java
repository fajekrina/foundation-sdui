package com.fajekrina.sduir.foundation.sdui.utils;

import com.fajekrina.sduir.foundation.sdui.contract.utils.SduiJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/// JSON for the endpoints: the contract's rules ({@link SduiJson}) plus responses written as
/// text, so the bytes leave the service exactly as produced.
public final class Json {
    public static final ObjectMapper MAPPER = SduiJson.MAPPER;

    private Json() {}

    public static JsonNode parse(String text) { return SduiJson.parse(text); }
    public static JsonNode parse(byte[] bytes) { return SduiJson.parse(bytes); }
    public static ObjectNode object() { return SduiJson.object(); }
    public static String write(Object value) { return SduiJson.write(value); }
    public static byte[] canonical(JsonNode node) { return SduiJson.canonical(node); }
    public static String sha256(byte[] bytes) { return SduiJson.sha256(bytes); }

    public static ResponseEntity<String> ok(Object body) { return status(HttpStatus.OK, body); }

    public static ResponseEntity<String> status(HttpStatus status, Object body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
            .body(body instanceof String s ? s : write(body));
    }

    /// Stored bytes as-is (envelopes, manifests) with an ETag and a cache policy; `304`
    /// when `If-None-Match` matches.
    public static ResponseEntity<String> stored(byte[] bytes, String etag, String ifNoneMatch, CacheControl cache) {
        String quoted = "\"" + etag + "\"";
        if (ifNoneMatch != null && (ifNoneMatch.equals(quoted) || ifNoneMatch.equals("W/" + quoted) || ifNoneMatch.equals("*"))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(quoted).cacheControl(cache).build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).eTag(quoted).cacheControl(cache)
            .body(new String(bytes, StandardCharsets.UTF_8));
    }
}
