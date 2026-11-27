package com.fajekrina.sduir.foundation.sdui.service.template.impl;

import com.fajekrina.sduir.foundation.sdui.service.template.SduiTemplateAuthoring;
import com.fajekrina.sduir.foundation.sdui.service.template.TemplateService;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SpecContract;
import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;
import com.fajekrina.sduir.foundation.sdui.security.ActorResolver;
import com.fajekrina.sduir.foundation.sdui.utils.Json;
import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import com.fajekrina.sduir.foundation.sdui.service.idempotency.SduiIdempotency;
import com.fajekrina.sduir.foundation.sdui.utils.SduiRequests;
import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;

import java.util.function.Supplier;

/// The built-in {@link SduiTemplateAuthoring} (see there for what each handler answers).
public class SduiTemplateAuthoringImpl implements SduiTemplateAuthoring {
    private final TemplateService templates;
    private final ActorResolver actors;
    private final SduiErrorGuard errors;
    private final SduiIdempotency idempotency;

    public SduiTemplateAuthoringImpl(TemplateService templates, ActorResolver actors, SduiErrorGuard errors, SduiIdempotency idempotency) {
        this.templates = templates;
        this.actors = actors;
        this.errors = errors;
        this.idempotency = idempotency;
    }

    @Override
    public SduiResponse list(HttpServletRequest request) {
        return read(() -> {
            actors.current().require(SduiActor.TEMPLATE_ADMIN);
            return templates.list(TemplateService.SHARED);
        });
    }

    @Override
    public SduiResponse get(String key, HttpServletRequest request) {
        return read(() -> {
            actors.current().require(SduiActor.TEMPLATE_ADMIN);
            return templates.get(TemplateService.SHARED, key);
        });
    }

    @Override
    public SduiResponse saveDraft(String key, HttpServletRequest request) {
        return write(request, () -> {
            JsonNode json = SduiRequests.json(request, true);
            Integer lock = json.hasNonNull("lockVersion") ? json.path("lockVersion").asInt() : null;
            return templates.saveDraft(key, json.path("payload").deepCopy(), lock, actors.current());
        });
    }

    @Override
    public SduiResponse publish(String key, HttpServletRequest request) {
        return write(request, () -> templates.publish(key, actors.current()));
    }

    @Override
    public SduiResponse deprecate(String key, HttpServletRequest request) {
        return write(request, () -> {
            JsonNode body = SduiRequests.json(request, false);
            return templates.deprecate(key, note(body, "reason"), note(body, "replacedBy"), actors.current());
        });
    }

    @Override
    public SduiResponse restore(String key, HttpServletRequest request) {
        return write(request, () -> templates.restore(key, actors.current()));
    }

    @Override
    public SduiResponse support(String key, int version, HttpServletRequest request) {
        return read(() -> {
            actors.current().require(SduiActor.ADMIN_OR_SERVICE);
            return templates.support(key, version);
        });
    }

    @Override
    public SduiResponse setSupport(String key, int version, HttpServletRequest request) {
        return write(request, () -> templates.setSupport(key, version, SduiRequests.json(request, true), actors.current()));
    }

    @Override
    public SduiResponse catalog(String owner, HttpServletRequest request) {
        return read(() -> {
            reader(owner);
            return templates.catalog(owner, "true".equalsIgnoreCase(request.getParameter("all")));
        });
    }

    @Override
    public SduiResponse status(String owner, String key, HttpServletRequest request) {
        return read(() -> {
            reader(owner);
            return templates.status(owner, key);
        });
    }

    @Override
    public SduiResponse released(String owner, String key, int version, HttpServletRequest request) {
        return read(() -> {
            reader(owner);
            return templates.releasedPayload(owner, key, version);
        });
    }

    @Override
    public SduiResponse draft(String owner, String key, HttpServletRequest request) {
        return read(() -> {
            reader(owner);
            return templates.draftPayload(owner, key);
        });
    }

    private SduiResponse read(Supplier<Object> work) {
        return errors.guard(() -> SduiResponse.ok(work.get()));
    }

    private SduiResponse write(HttpServletRequest request, Supplier<Object> work) {
        return errors.guard(() -> idempotency.run(request, () -> SduiResponse.ok(work.get())));
    }

    /// a user reads its own pages' templates (owner = its own id) and the shared ones (`sdui`)
    private void reader(String owner) {
        if (!SpecContract.isIdentifier(owner)) throw SduiException.badRequest("owner must be a merchant id");
        SduiActor actor = actors.current().require(SduiActor.ADMIN_OR_SERVICE);
        if (!owner.equals(TemplateService.SHARED)) actor.requireMerchant(owner);
    }

    private static String note(JsonNode body, String field) {
        JsonNode node = body.path(field);
        return node.isTextual() ? node.asText() : null;
    }
}
