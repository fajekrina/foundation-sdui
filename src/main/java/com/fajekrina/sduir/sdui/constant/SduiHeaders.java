package com.fajekrina.sduir.sdui.constant;

/// The HTTP headers of the SDUI APIs (no Spring: `core` uses them too).
public final class SduiHeaders {
    private SduiHeaders() {}

    /// the app's platform and version, e.g. `android/5.4.0` (delivery picks the release it can show)
    public static final String CLIENT = "X-Sdui-Client";
    /// the page an action is called from
    public static final String PAGE = "X-Sdui-Page";
    /// the Studio user, trusted only with `sdui.actor.trust-header` (behind a BFF)
    public static final String ACTOR = "X-Sdui-Actor";
    /// a POST that must run once
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /// answered on a replayed POST
    public static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";
    /// the language the app wants (spec 2.8); delivery answers the page in it, or in the page's default language
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    /// the language delivery answered in
    public static final String CONTENT_LANGUAGE = "Content-Language";
}
