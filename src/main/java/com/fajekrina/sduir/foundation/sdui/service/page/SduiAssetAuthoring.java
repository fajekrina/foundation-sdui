package com.fajekrina.sduir.foundation.sdui.service.page;

import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;

/// The pictures of a space's pages, as handlers (Studio API): the service's controller maps each
/// route to one method and returns its answer as-is.
///
/// Built in: {@link SduiAssetAuthoringImpl}. A service with its own bean of this type replaces it.
public interface SduiAssetAuthoring {
    /// operation `assetCreate`, POST: store a picture for an image component, body
    /// `{ "filename", "data": <base64> }` (upload) or `{ "url": "https://…" }` (copied once).
    /// Answers `201 { name, contentType, size, … }`; `name` goes into `image.name`.
    SduiResponse create(String space, HttpServletRequest request);

    /// operation `assetList`, GET
    SduiResponse list(String space, HttpServletRequest request);

    /// operation `assetContent`, GET: the picture's bytes for the Studio preview (drafts
    /// included; space scoped, not cached).
    SduiResponse content(String space, String id, HttpServletRequest request);
}
