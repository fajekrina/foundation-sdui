package com.fajekrina.sduir.foundation.sdui.service.action.impl;

import com.fajekrina.sduir.foundation.sdui.model.dto.SduiContext;
import com.fajekrina.sduir.foundation.sdui.service.action.ActionRegistry;
import com.fajekrina.sduir.foundation.sdui.service.action.SduiActionDispatcher;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SpecContract;
import com.fajekrina.sduir.foundation.sdui.contract.model.ClientInfo;
import com.fajekrina.sduir.foundation.sdui.utils.Json;
import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import com.fajekrina.sduir.foundation.sdui.service.idempotency.SduiIdempotency;
import com.fajekrina.sduir.foundation.sdui.utils.SduiRequests;
import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;

/// The built-in {@link SduiActionDispatcher} (see there for what each handler answers).
public class SduiActionDispatcherImpl implements SduiActionDispatcher {
    private final ActionRegistry registry;
    private final SduiErrorGuard errors;
    private final SduiIdempotency idempotency;

    public SduiActionDispatcherImpl(ActionRegistry registry, SduiErrorGuard errors, SduiIdempotency idempotency) {
        this.registry = registry;
        this.errors = errors;
        this.idempotency = idempotency;
    }

    @Override
    public SduiResponse names() { return errors.guard(() -> SduiResponse.ok(registry.names())); }

    @Override
    public SduiResponse dispatch(String name, HttpServletRequest request, String customerId) {
        return errors.guard(() -> {
            String method = request.getMethod();
            ActionRegistry.Entry entry = SpecContract.isIdentifier(name) ? registry.get(name) : null;
            if (entry == null) throw SduiException.notFound("no action or source named " + name);
            if (!entry.method().equals(method)) throw SduiException.methodNotAllowed(name + " is " + entry.method());
            String key = request.getHeader(SduiIdempotency.HEADER);
            if (entry.idempotent() && method.equals("POST") && (key == null || key.isBlank())) throw SduiException.badRequest(name + " needs an Idempotency-Key");
            String page = request.getHeader(PAGE_HEADER);
            if (page != null && !SpecContract.isIdentifier(page)) throw SduiException.badRequest(PAGE_HEADER + " must name the page that asks");
            SduiContext context = new SduiContext(name, page, ClientInfo.parse(request.getHeader(ClientInfo.HEADER)), customerId, key);
            if (entry.customer()) context.requireCustomer();
            return idempotency.run(request, () -> {
                Object answer = entry.handler().handle(read(input(request, method), entry.requestType()), context);
                return SduiResponse.ok(answer == null ? Json.object() : answer);
            });
        });
    }

    private static JsonNode input(HttpServletRequest request, String method) {
        if (!method.equals("GET")) return SduiRequests.json(request, false);
        ObjectNode query = Json.object();
        request.getParameterMap().forEach((k, v) -> query.put(k, v.length == 0 ? "" : v[0]));
        return query;
    }

    private static Object read(JsonNode input, Class<?> type) {
        if (type.isInstance(input) || type == Object.class) return input;
        try {
            return Json.MAPPER.treeToValue(input, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw SduiException.invalid("the request does not fit: " + (e instanceof JsonProcessingException j ? j.getOriginalMessage() : e.getMessage()));
        }
    }
}
