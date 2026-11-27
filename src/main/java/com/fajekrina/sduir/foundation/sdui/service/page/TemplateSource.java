package com.fajekrina.sduir.foundation.sdui.service.page;

import com.fasterxml.jackson.databind.JsonNode;

/// Where the pages read the template releases they are made from. Port: when the same service
/// also runs the template authoring, the default ({@link LocalTemplateSource}) reads it directly;
/// a pages-only service registers a bean that asks the template service.
public interface TemplateSource {
    /// released template document `key@version` of space `owner` (its own older template of that
    /// key, else the shared one); `SduiException.notFound` when it is not released
    JsonNode release(String owner, String key, int version);

    /// the template's current draft document (never cached: drafts change)
    JsonNode draft(String owner, String key);

    /// `{ key, activeVersion, deprecated, deprecationReason, replacedBy }` — mutable, never cached
    JsonNode status(String owner, String key);

    /// The template a value draft is written for: release `version`, or — when that version is
    /// not released yet (a new template, or the next version being drafted) — the template's
    /// draft, if the draft is exactly that version.
    default JsonNode releaseOrDraft(String owner, String key, int version) {
        try {
            return release(owner, key, version);
        } catch (com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException notReleased) {
            if (notReleased.fault() != com.fajekrina.sduir.foundation.sdui.contract.exception.SduiFault.NOT_FOUND) throw notReleased;
            JsonNode draft = draft(owner, key);
            if (draft.path("template").path("version").asInt() != version) throw notReleased;
            return draft;
        }
    }
}
