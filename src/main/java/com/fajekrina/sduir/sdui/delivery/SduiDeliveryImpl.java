package com.fajekrina.sduir.sdui.delivery;

import com.fajekrina.sduir.sdui.constant.SduiHeaders;
import com.fajekrina.sduir.sdui.contract.SduiLocales;
import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.contract.SpecContract;
import com.fajekrina.sduir.sdui.core.ClientInfo;
import com.fajekrina.sduir.sdui.web.Json;
import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.web.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The built-in {@link SduiDelivery} (see there for what each handler answers).
public class SduiDeliveryImpl implements SduiDelivery {
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
    private static final CacheControl REVALIDATE = CacheControl.noCache().cachePublic();
    private static final String IF_NONE_MATCH = "If-None-Match";
    private final JdbcTemplate jdbc;
    private final SigningKeys keys;
    private final SduiErrors errors;
    private final String tpl;
    private final String val;
    private final boolean previousRelease;
    private final String defaultLanguage;

    /// `templateSchema` / `pageSchema`: where the template and page views are ("" = the connection's schema)
    public SduiDeliveryImpl(JdbcTemplate jdbc, SigningKeys keys, SduiErrors errors, String templateSchema, String pageSchema, String fallback) {
        this(jdbc, keys, errors, templateSchema, pageSchema, fallback, "id");
    }

    /// `defaultLanguage`: the language of a release that names none (spec 2.8, `sdui.language.default`)
    public SduiDeliveryImpl(JdbcTemplate jdbc, SigningKeys keys, SduiErrors errors, String templateSchema, String pageSchema, String fallback, String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
        this.jdbc = jdbc;
        this.keys = keys;
        this.errors = errors;
        this.tpl = prefix(templateSchema);
        this.val = prefix(pageSchema);
        if (!fallback.equals("no-content") && !fallback.equals("previous-release")) throw new IllegalArgumentException("sdui.delivery.fallback: no-content | previous-release");
        this.previousRelease = fallback.equals("previous-release");
    }

    @Override
    public SduiResponse template(String space, String key, int version, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space, key);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT owner, language, envelope FROM " + tpl + "sdui_template_release_read WHERE owner IN (?, 'sdui') AND template_key = ? AND version = ?"
                + " ORDER BY CASE WHEN owner = ? THEN 0 ELSE 1 END FETCH FIRST 1 ROWS ONLY", space, key, version, space);
            if (rows.isEmpty()) throw SduiProblem.notFound("template " + space + "/" + key + "@" + version + " not found");
            Map<String, Object> row = rows.get(0);
            Localized in = localized((String) row.get("ENVELOPE"), row.get("LANGUAGE"), request,
                "SELECT language, envelope FROM " + tpl + "sdui_template_release_locale_read WHERE owner = ? AND template_key = ? AND version = ?", row.get("OWNER"), key, version);
            return perLanguage(serve(in.envelope(), request, IMMUTABLE), in);
        });
    }

    @Override
    public SduiResponse spaces(HttpServletRequest request) {
        return errors.guard(() -> {
            var rows = jdbc.queryForList("SELECT m.merchant_id AS \"merchantId\", m.name AS \"name\" FROM " + val + "sdui_merchant_read m"
                + " WHERE m.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM " + val + "sdui_screen_read s WHERE s.merchant_id = m.merchant_id AND s.active_sequence IS NOT NULL)"
                + " ORDER BY m.merchant_id");
            return SduiResponse.ok(rows).cache(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic());
        });
    }

    @Override
    public SduiResponse screen(String space, String key, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space, key);
            ClientInfo client = ClientInfo.parse(request.getHeader(ClientInfo.HEADER));
            List<Map<String, Object>> active = jdbc.queryForList("SELECT r.sequence, r.template_key, r.template_version, r.language, r.envelope FROM " + val + "sdui_value_release_read r"
                + " JOIN " + val + "sdui_screen_read s ON s.merchant_id = r.merchant_id AND s.screen_key = r.screen_key AND s.active_sequence = r.sequence"
                + " WHERE r.merchant_id = ? AND r.screen_key = ?", space, key);
            if (active.isEmpty()) throw SduiProblem.notFound("screen " + space + "/" + key + " not found");
            Map<String, Object> release = active.get(0);
            if (!client.isNative() || carries(client, space, release)) return perClient(page(space, key, release, request, REVALIDATE));
            if (previousRelease) {
                for (Map<String, Object> older : jdbc.queryForList("SELECT sequence, template_key, template_version, language, envelope FROM " + val + "sdui_value_release_read"
                        + " WHERE merchant_id = ? AND screen_key = ? AND sequence < ? ORDER BY sequence DESC", space, key, release.get("SEQUENCE"))) {
                    if (carries(client, space, older)) return perClient(page(space, key, older, request, REVALIDATE));
                }
            }
            return perClient(SduiResponse.empty(204).cache(REVALIDATE));
        });
    }

    @Override
    public SduiResponse screenRelease(String space, String key, int sequence, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space, key);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT sequence, language, envelope FROM " + val + "sdui_value_release_read WHERE merchant_id = ? AND screen_key = ? AND sequence = ?",
                space, key, sequence);
            if (rows.isEmpty()) throw SduiProblem.notFound("screen " + space + "/" + key + "/v" + sequence + " not found");
            return page(space, key, rows.get(0), request, IMMUTABLE);
        });
    }

    @Override
    public SduiResponse manifest(String space, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space);
            return serve(one("SELECT envelope FROM " + val + "sdui_manifest_read WHERE merchant_id = ? ORDER BY sequence DESC FETCH FIRST 1 ROWS ONLY",
                "manifest " + space, space), request, REVALIDATE);
        });
    }

    @Override
    public SduiResponse asset(String id, HttpServletRequest request) {
        return errors.guard(() -> {
            if (!id.matches("^img_[0-9a-f]{32}$")) throw SduiProblem.badRequest("not an asset id: " + id);
            var rows = jdbc.queryForList("SELECT content_type, content_hash, content FROM " + val + "sdui_asset_read WHERE asset_id = ?", id);
            if (rows.isEmpty()) throw SduiProblem.notFound("asset " + id + " not found");
            String etag = "\"" + rows.get(0).get("CONTENT_HASH") + "\"";
            SduiResponse out = SduiResponse.matches(request.getHeader(IF_NONE_MATCH), etag) ? SduiResponse.empty(304)
                : SduiResponse.bytes(200, MediaType.parseMediaType((String) rows.get(0).get("CONTENT_TYPE")), (byte[]) rows.get(0).get("CONTENT"));
            return out.header("ETag", etag).cache(IMMUTABLE)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox");
        });
    }

    @Override
    public SduiResponse signingKeys(HttpServletRequest request) {
        return errors.guard(() -> {
            List<SigningKeys.Key> list = keys.keys();
            if (list.isEmpty()) throw SduiProblem.notFound("no signing key yet");
            return SduiResponse.ok(Map.of("keys", list.stream().map((k) -> Map.of("keyId", k.keyId(), "alg", "ed25519", "publicKey", k.publicKey(), "status", k.status())).toList()));
        });
    }

    /// whether the native app's version carries the template release of a page release (the
    /// space's own template of that key first, else the shared one)
    private boolean carries(ClientInfo client, String space, Map<String, Object> release) {
        List<String> minimum = jdbc.queryForList("SELECT minimum_client_version FROM " + tpl + "sdui_template_support_read"
                + " WHERE owner IN (?, 'sdui') AND template_key = ? AND version = ? AND platform = ? ORDER BY CASE WHEN owner = ? THEN 0 ELSE 1 END FETCH FIRST 1 ROWS ONLY",
            String.class, space, release.get("TEMPLATE_KEY"), release.get("TEMPLATE_VERSION"), client.platform(), space);
        return !minimum.isEmpty() && client.meets(minimum.get(0));
    }

    private static SduiResponse serve(String envelope, HttpServletRequest request, CacheControl cache) {
        byte[] bytes = envelope.getBytes(StandardCharsets.UTF_8);
        return SduiResponse.stored(bytes, Json.sha256(bytes), request.getHeader(IF_NONE_MATCH), cache);
    }

    /// a stored envelope in one language (spec 2.8)
    private record Localized(String envelope, String language) {}

    /// A release in the language the app asks for (`Accept-Language`): one of its further
    /// languages (`localeSql` → language, envelope), else its own (`ownLanguage`, or the default).
    private Localized localized(String envelope, Object ownLanguage, HttpServletRequest request, String localeSql, Object... args) {
        String own = ownLanguage == null ? defaultLanguage : (String) ownLanguage;
        String wanted = request.getHeader(SduiHeaders.ACCEPT_LANGUAGE);
        if (wanted == null || wanted.isBlank()) return new Localized(envelope, own);
        List<Map<String, Object>> rows = jdbc.queryForList(localeSql, args);
        if (rows.isEmpty()) return new Localized(envelope, own);
        List<String> available = new ArrayList<>(List.of(own));
        rows.forEach((r) -> available.add((String) r.get("LANGUAGE")));
        String chosen = SduiLocales.pick(wanted, available, own);
        for (Map<String, Object> r : rows) if (r.get("LANGUAGE").equals(chosen)) return new Localized((String) r.get("ENVELOPE"), chosen);
        return new Localized(envelope, own);
    }

    /// one page release in the asking app's language
    private SduiResponse page(String space, String key, Map<String, Object> release, HttpServletRequest request, CacheControl cache) {
        Localized in = localized((String) release.get("ENVELOPE"), release.get("LANGUAGE"), request,
            "SELECT language, envelope FROM " + val + "sdui_value_release_locale_read WHERE merchant_id = ? AND screen_key = ? AND sequence = ?", space, key, release.get("SEQUENCE"));
        return perLanguage(serve(in.envelope(), request, cache), in);
    }

    /// the answer depends on the asked language: caches keep one per `Accept-Language`
    private static SduiResponse perLanguage(SduiResponse out, Localized in) {
        return out.header(SduiHeaders.CONTENT_LANGUAGE, in.language()).header("Vary", SduiHeaders.ACCEPT_LANGUAGE);
    }

    /// the answer depends on the asking app: caches keep one per `X-Sdui-Client`
    private static SduiResponse perClient(SduiResponse out) { return out.header("Vary", ClientInfo.HEADER); }

    private String one(String sql, String what, Object... args) {
        List<String> rows = jdbc.queryForList(sql, String.class, args);
        if (rows.isEmpty()) throw SduiProblem.notFound(what + " not found");
        return rows.get(0);
    }

    private static void ids(String... values) {
        for (String v : values) if (!SpecContract.isIdentifier(v)) throw SduiProblem.badRequest("not an identifier: " + v);
    }

    private static String prefix(String schema) {
        if (schema == null || schema.isBlank()) return "";
        if (!schema.matches("^[A-Za-z][A-Za-z0-9_]*$")) throw new IllegalArgumentException("bad schema name " + schema);
        return schema + ".";
    }
}
