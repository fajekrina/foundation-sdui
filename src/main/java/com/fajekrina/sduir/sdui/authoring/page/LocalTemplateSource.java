package com.fajekrina.sduir.sdui.authoring.page;

import com.fajekrina.sduir.sdui.authoring.template.TemplateService;
import com.fajekrina.sduir.sdui.web.Json;
import com.fasterxml.jackson.databind.JsonNode;

/// The default {@link TemplateSource} when templates and pages run in one service.
public class LocalTemplateSource implements TemplateSource {
    private final TemplateService templates;

    public LocalTemplateSource(TemplateService templates) { this.templates = templates; }

    @Override
    public JsonNode release(String owner, String key, int version) { return Json.parse(templates.releasedPayload(owner, key, version)); }

    @Override
    public JsonNode draft(String owner, String key) { return Json.parse(templates.draftPayload(owner, key)); }

    @Override
    public JsonNode status(String owner, String key) { return Json.MAPPER.valueToTree(templates.status(owner, key)); }
}
