package com.fajekrina.sduir.sdui.web;

import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/// Reading a request in a handler: the body once (kept on the request, so idempotency and the
/// handler see the same bytes), headers by name.
public final class SduiRequests {
    private static final String BODY = SduiRequests.class.getName() + ".body";

    private SduiRequests() {}

    /// the body bytes (empty when there is none)
    public static byte[] body(HttpServletRequest request) {
        if (request.getAttribute(BODY) instanceof byte[] kept) return kept;
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            request.setAttribute(BODY, bytes);
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String text(HttpServletRequest request) { return new String(body(request), StandardCharsets.UTF_8); }

    /// the body as JSON; an empty body is `{}`, or a 400 when `required`
    public static JsonNode json(HttpServletRequest request, boolean required) {
        String text = text(request);
        if (text.isBlank()) {
            if (required) throw SduiProblem.badRequest("a request body is required");
            return Json.object();
        }
        return Json.parse(text);
    }
}
