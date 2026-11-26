package com.fajekrina.sduir.sdui.authoring.template;

import com.fajekrina.sduir.sdui.web.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;

/// The template half of the Studio API, as handlers: the service's controller maps each route of
/// the Studio routes (foundation-sdui docs/IMPLEMENTASI.md §7) to one method and returns its answer as-is. The caller comes from the
/// service's {@link ActorResolver}.
/// - `/merchant/templates/**` works on the shared templates (every Studio user);
/// - `/catalog/templates/{owner}/**` reads the templates a space uses: its own older ones first,
///   then the shared ones.
///
/// Built in: {@link SduiTemplateAuthoringImpl}. A service with its own bean of this type replaces it.
public interface SduiTemplateAuthoring {
    /// `GET /merchant/templates`
    SduiResponse list(HttpServletRequest request);

    /// `GET /merchant/templates/{key}`
    SduiResponse get(String key, HttpServletRequest request);

    /// `PUT /merchant/templates/{key}/draft`, body `{ "payload": <template document>, "lockVersion": n }`
    /// (no lockVersion for a new template)
    SduiResponse saveDraft(String key, HttpServletRequest request);

    /// `POST /merchant/templates/{key}/publish`: publish the draft now (no review step); answers the template.
    SduiResponse publish(String key, HttpServletRequest request);

    /// `PUT /merchant/templates/{key}/deprecation`, body `{ "reason": "…", "replacedBy": "<template key>" }`
    /// (replacedBy optional). Again on a deprecated template: changes the reason / replacement.
    SduiResponse deprecate(String key, HttpServletRequest request);

    /// `DELETE /merchant/templates/{key}/deprecation`: take the deprecation back.
    SduiResponse restore(String key, HttpServletRequest request);

    /// `GET /merchant/templates/{key}/v{version}/support`: which app versions carry release
    /// `key@version`, `[{ platform, minimumClientVersion, … }]`.
    SduiResponse support(String key, int version, HttpServletRequest request);

    /// `PUT /merchant/templates/{key}/v{version}/support`, body
    /// `[{ "platform": "android", "minimumClientVersion": "5.4.0" }, …]`: replaces them.
    SduiResponse setSupport(String key, int version, HttpServletRequest request);

    /// `GET /catalog/templates/{owner}` (`?all=true`: every released version)
    SduiResponse catalog(String owner, HttpServletRequest request);

    /// `GET /catalog/templates/{owner}/{key}`: whether a template may start a new page,
    /// `{ key, activeVersion, deprecated, deprecationReason, replacedBy }`.
    SduiResponse status(String owner, String key, HttpServletRequest request);

    /// `GET /catalog/templates/{owner}/{key}/v{version}`: a released template document.
    SduiResponse released(String owner, String key, int version, HttpServletRequest request);

    /// `GET /catalog/templates/{owner}/{key}/draft`: the template's current draft document (pages
    /// validate a content draft against it before the template's first release, or while a newer
    /// version is drafted).
    SduiResponse draft(String owner, String key, HttpServletRequest request);
}
