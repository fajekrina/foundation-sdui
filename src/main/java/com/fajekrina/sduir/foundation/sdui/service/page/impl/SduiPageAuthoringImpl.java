package com.fajekrina.sduir.foundation.sdui.service.page.impl;

import com.fajekrina.sduir.foundation.sdui.service.page.Merchants;
import com.fajekrina.sduir.foundation.sdui.service.page.SduiPageAuthoring;
import com.fajekrina.sduir.foundation.sdui.service.page.ValueService;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.security.ActorResolver;
import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import com.fajekrina.sduir.foundation.sdui.service.idempotency.SduiIdempotency;
import com.fajekrina.sduir.foundation.sdui.utils.SduiRequests;
import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

/// The built-in {@link SduiPageAuthoring} (see there for what each handler answers).
public class SduiPageAuthoringImpl implements SduiPageAuthoring {
    private final ValueService values;
    private final Merchants merchants;
    private final ActorResolver actors;
    private final SduiErrorGuard errors;
    private final SduiIdempotency idempotency;

    public SduiPageAuthoringImpl(ValueService values, Merchants merchants, ActorResolver actors, SduiErrorGuard errors, SduiIdempotency idempotency) {
        this.values = values;
        this.merchants = merchants;
        this.actors = actors;
        this.errors = errors;
        this.idempotency = idempotency;
    }

    @Override
    public SduiResponse spaces(HttpServletRequest request) {
        return errors.guard(() -> SduiResponse.ok(values.merchants(actors.current())));
    }

    @Override
    public SduiResponse createSpace(HttpServletRequest request) {
        return errors.guard(() -> idempotency.run(request, () -> {
            JsonNode json = SduiRequests.json(request, true);
            String id = json.path("merchantId").asText("").trim();
            boolean created = merchants.create(id, json.path("name").isTextual() ? json.path("name").asText() : null, actors.current());
            Object space = values.merchants(actors.current()).stream().filter((m) -> id.equals(m.get("merchantId"))).findFirst().orElseThrow();
            return SduiResponse.json(created ? 201 : 200, space);
        }));
    }

    @Override
    public SduiResponse pages(HttpServletRequest request) {
        String template = request.getParameter("template");
        return read(() -> template == null || template.isBlank() ? values.allPages(actors.current()) : values.pagesOf(template, actors.current()));
    }

    @Override
    public SduiResponse screens(String space, HttpServletRequest request) {
        return read(() -> values.list(space, actors.current()));
    }

    @Override
    public SduiResponse screen(String space, String key, HttpServletRequest request) {
        return read(() -> values.get(space, key, actors.current()));
    }

    @Override
    public SduiResponse saveDraft(String space, String key, HttpServletRequest request) {
        return write(request, () -> {
            JsonNode json = SduiRequests.json(request, true);
            Integer lock = json.hasNonNull("lockVersion") ? json.path("lockVersion").asInt() : null;
            return values.saveDraft(space, key, json.path("payload").deepCopy(), lock, actors.current());
        });
    }

    @Override
    public SduiResponse publish(String space, String key, HttpServletRequest request) {
        return write(request, () -> {
            JsonNode json = SduiRequests.json(request, false);
            if (json.hasNonNull("templateVersion")) {
                JsonNode v = json.path("templateVersion");
                if (!v.canConvertToInt() || v.asInt() < 1) throw SduiException.badRequest("templateVersion must be a positive integer");
                return values.publishAt(space, key, v.asInt(), actors.current());
            }
            return values.publish(space, key, actors.current());
        });
    }

    @Override
    public SduiResponse manifest(String space, HttpServletRequest request) {
        return read(() -> values.manifest(space, actors.current()));
    }

    private SduiResponse read(Supplier<Object> work) {
        return errors.guard(() -> SduiResponse.ok(work.get()));
    }

    private SduiResponse write(HttpServletRequest request, Supplier<Object> work) {
        return errors.guard(() -> idempotency.run(request, () -> SduiResponse.ok(work.get())));
    }
}
