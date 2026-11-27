package com.fajekrina.sduir.foundation.sdui.service.template;

import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;

/// The template half of the Studio API, as handlers: the service's controller maps each route of
/// the Studio routes (foundation-sdui docs/IMPLEMENTASI.md §7) to one method and returns its answer as-is. The caller comes from the
/// service's {@link ActorResolver}.
/// - the `template…` operations without `{space}` work on the shared templates (every Studio user);
/// - `templateCatalog`, `templateStatus`, `templateReleased`, `templateDraftOf` ({space}) read the templates a space uses: its own older ones first,
///   then the shared ones.
///
/// Built in: {@link SduiTemplateAuthoringImpl}. A service with its own bean of this type replaces it.
public interface SduiTemplateAuthoring {
    /// operation `templateList`, GET
    SduiResponse list(HttpServletRequest request);

    /// operation `templateGet`, GET
    SduiResponse get(String key, HttpServletRequest request);

    /// operation `templateDraft`, PUT, body `{ "payload": <template document>, "lockVersion": n }`
    /// (no lockVersion for a new template)
    SduiResponse saveDraft(String key, HttpServletRequest request);

    /// operation `templatePublish`, POST: publish the draft now (no review step); answers the template.
    SduiResponse publish(String key, HttpServletRequest request);

    /// operation `templateDeprecate`, PUT, body `{ "reason": "…", "replacedBy": "<template key>" }`
    /// (replacedBy optional). Again on a deprecated template: changes the reason / replacement.
    SduiResponse deprecate(String key, HttpServletRequest request);

    /// operation `templateRestore`, DELETE: take the deprecation back.
    SduiResponse restore(String key, HttpServletRequest request);

    /// operation `templateSupport`, GET: which app versions carry release
    /// `key@version`, `[{ platform, minimumClientVersion, … }]`.
    SduiResponse support(String key, int version, HttpServletRequest request);

    /// operation `templateSupportSet`, PUT, body
    /// `[{ "platform": "android", "minimumClientVersion": "5.4.0" }, …]`: replaces them.
    SduiResponse setSupport(String key, int version, HttpServletRequest request);

    /// operation `templateCatalog`, GET (`?all=true`: every released version)
    SduiResponse catalog(String owner, HttpServletRequest request);

    /// operation `templateStatus`, GET: whether a template may start a new page,
    /// `{ key, activeVersion, deprecated, deprecationReason, replacedBy }`.
    SduiResponse status(String owner, String key, HttpServletRequest request);

    /// operation `templateReleased`, GET: a released template document.
    SduiResponse released(String owner, String key, int version, HttpServletRequest request);

    /// operation `templateDraftOf`, GET: the template's current draft document (pages
    /// validate a content draft against it before the template's first release, or while a newer
    /// version is drafted).
    SduiResponse draft(String owner, String key, HttpServletRequest request);
}
