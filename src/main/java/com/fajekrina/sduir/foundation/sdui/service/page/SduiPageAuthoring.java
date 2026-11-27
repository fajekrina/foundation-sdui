package com.fajekrina.sduir.foundation.sdui.service.page;

import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;

/// The page half of the Studio API (page spaces and value sets), as handlers: the service's
/// controller maps each Studio route (foundation-sdui docs/IMPLEMENTASI.md §7) to one method and returns its answer
/// as-is. The caller comes from the service's {@link ActorResolver}; its spaces bound what it
/// reaches.
///
/// Built in: {@link SduiPageAuthoringImpl}. A service with its own bean of this type replaces it.
public interface SduiPageAuthoring {
    /// operation `spaceList`, GET: the spaces the caller reaches
    SduiResponse spaces(HttpServletRequest request);

    /// operation `spaceCreate`, POST, body `{ "merchantId": "wondr", "name": "wondr by BNI" }` (name optional):
    /// 201 when new, 200 when it exists.
    SduiResponse createSpace(HttpServletRequest request);

    /// operation `pageList`, GET: every page of the spaces the caller reaches (`?template=<key>`: only the pages made from it)
    SduiResponse pages(HttpServletRequest request);

    /// operation `screenList`, GET
    SduiResponse screens(String space, HttpServletRequest request);

    /// operation `screenGet`, GET
    SduiResponse screen(String space, String key, HttpServletRequest request);

    /// operation `screenDraft`, PUT, body `{ "payload": <value set document>, "lockVersion": n }`
    /// (no lockVersion for a new screen)
    SduiResponse saveDraft(String space, String key, HttpServletRequest request);

    /// operation `screenPublish`, POST: publish the draft now (no review step);
    /// answers the screen. Body `{ "templateVersion": n }` (optional): carry the content over to
    /// that template release first, creating the page if there is none yet — the answer then also
    /// lists what was `dropped`.
    SduiResponse publish(String space, String key, HttpServletRequest request);

    /// operation `manifestGet`, GET: the latest manifest
    SduiResponse manifest(String space, HttpServletRequest request);
}
