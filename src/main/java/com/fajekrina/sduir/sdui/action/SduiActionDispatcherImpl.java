package com.fajekrina.sduir.sdui.action;

import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.contract.SpecContract;
import com.fajekrina.sduir.sdui.core.ClientInfo;
import com.fajekrina.sduir.sdui.web.Json;
import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.web.SduiIdempotency;
import com.fajekrina.sduir.sdui.web.SduiRequests;
import com.fajekrina.sduir.sdui.web.SduiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;

/// The built-in {@link SduiActionDispatcher} (see there for what each handler answers).
public class SduiActionDispatcherImpl implements SduiActionDispatcher {
    private final ActionRegistry registry;
    private final SduiErrors errors;
    private final SduiIdempotency idempotency;

    public SduiActionDispatcherImpl(ActionRegistry registry, SduiErrors errors, SduiIdempotency idempotency) {
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
            if (entry == null) throw SduiProblem.notFound("no action or source named " + name);
            if (!entry.method().equals(method)) throw SduiProblem.methodNotAllowed(name + " is " + entry.method());
            String key = request.getHeader(SduiIdempotency.HEADER);
            if (entry.idempotent() && method.equals("POST") && (key == null || key.isBlank())) throw SduiProblem.badRequest(name + " needs an Idempotency-Key");
            String page = request.getHeader(PAGE_HEADER);
            if (page != null && !SpecContract.isIdentifier(page)) throw SduiProblem.badRequest(PAGE_HEADER + " must name the page that asks");
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
            throw SduiProblem.invalid("the request does not fit: " + (e instanceof JsonProcessingException j ? j.getOriginalMessage() : e.getMessage()));
        }
    }
}
