package com.fajekrina.sduir.sdui.core;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The app that asks, from `X-Sdui-Client: <platform>/<app version>[; spec=<spec version>]`
/// (e.g. `android/5.4.0; spec=2.7`). Native apps (android, ios, mpaas) build every template
/// themselves, so delivery only gives them a page whose template release their version carries;
/// the web (and a request without the header) renders any release.
public record ClientInfo(String platform, String appVersion, String spec) {
    public static final String HEADER = com.fajekrina.sduir.sdui.constant.SduiHeaders.CLIENT;
    public static final Set<String> NATIVE = Set.of("android", "ios", "mpaas");
    private static final Pattern FORMAT = Pattern.compile("^\\s*([a-z]+)/([0-9]+(?:\\.[0-9]+)*)\\s*(?:;\\s*spec=([0-9]+(?:\\.[0-9]+)*))?\\s*$");
    private static final ClientInfo UNKNOWN = new ClientInfo(null, null, null);

    /// The client of a header value; an absent or unreadable header is an unknown client
    /// (treated like the web: it gets the active release).
    public static ClientInfo parse(String header) {
        if (header == null) return UNKNOWN;
        Matcher m = FORMAT.matcher(header.toLowerCase(Locale.ROOT));
        return m.matches() ? new ClientInfo(m.group(1), m.group(2), m.group(3)) : UNKNOWN;
    }

    /// whether the app builds templates natively (and so needs a release its version carries)
    public boolean isNative() { return platform != null && NATIVE.contains(platform); }

    /// whether this app's version is at least `minimum`
    public boolean meets(String minimum) { return appVersion != null && compare(appVersion, minimum) >= 0; }

    /// Numeric dotted versions: `1.2.10` > `1.2.9`, `1.2` = `1.2.0`.
    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length ? Long.parseLong(x[i]) : 0;
            long q = i < y.length ? Long.parseLong(y[i]) : 0;
            if (p != q) return Long.compare(p, q);
        }
        return 0;
    }

    /// a version string the support table accepts
    public static boolean isVersion(String v) { return v != null && v.matches("^[0-9]+(\\.[0-9]+){0,3}$"); }
}
