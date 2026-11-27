package com.fajekrina.sduir.foundation.sdui.service.delivery;

import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;

/// The client API (what the engine's loaders fetch) as handlers, read-only, from the `*_read`
/// views: the service's controller maps each client route (foundation-sdui docs/IMPLEMENTASI.md §7) to one method and
/// returns its answer as-is. The handlers read `If-None-Match` and `X-Sdui-Client` from the
/// request themselves. Stored envelopes are served byte-for-byte; ETag = SHA-256 of those bytes.
///
/// A native app (`X-Sdui-Client: android/5.4.0`) builds every template itself, so the active
/// page is served only when the app's version carries the page's template release
/// (`sdui_template_support`); otherwise the newest earlier release it can show
/// (`sdui.delivery.fallback=previous-release`) or `204` (`no-content`: the app shows its own UI).
///
/// Spec 2.8: a template or page released in several languages is answered in the one the app asks
/// for (`Accept-Language`), else in its own language; `Content-Language` names it, `Vary: Accept-Language`.
///
/// Built in: {@link SduiDeliveryImpl}. A service with its own bean of this type replaces it.
public interface SduiDelivery {
    /// operation `template`, GET: a release of a template a space uses — the
    /// space's own (older) template of that key, else the shared one (owner `sdui`) — immutable.
    SduiResponse template(String space, String key, int version, HttpServletRequest request);

    /// operation `spaces`, GET: the spaces that have at least one published page, `[{ merchantId, name }]`.
    SduiResponse spaces(HttpServletRequest request);

    /// operation `screen`, GET: the active page — revalidated (`If-None-Match` → 304) —
    /// or, for a native app whose version does not carry its template release, the fallback.
    SduiResponse screen(String space, String key, HttpServletRequest request);

    /// operation `screenRelease`, GET: a page release named by the manifest — immutable.
    SduiResponse screenRelease(String space, String key, int sequence, HttpServletRequest request);

    /// operation `manifest`, GET: the latest signed manifest of a space — short-lived, revalidated.
    SduiResponse manifest(String space, HttpServletRequest request);

    /// operation `asset`, GET: a picture of an image component (`img_…`) once a published page
    /// uses it. Content-addressed, so immutable; served as an inert image (`nosniff`, a CSP that
    /// runs nothing), never as HTML.
    SduiResponse asset(String id, HttpServletRequest request);

    /// operation `signingKeys`, GET: the verification keys (raw 32-byte Ed25519, base64) — what the app pins.
    SduiResponse signingKeys(HttpServletRequest request);
}
