package com.fajekrina.sduir.foundation.sdui.repository;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/// What delivery reads: the `*_read` views of the template and page tables (the template views
/// in `templateSchema`, the page views in `pageSchema`; "" = the connection's schema). Reads only.
public class DeliveryRepository {
    private final JdbcTemplate jdbc;
    private final String tpl;
    private final String val;

    public DeliveryRepository(JdbcTemplate jdbc, String templateSchema, String pageSchema) {
        this.jdbc = jdbc;
        this.tpl = prefix(templateSchema);
        this.val = prefix(pageSchema);
    }

    /// `{ OWNER, LANGUAGE, ENVELOPE }` of template release `key@version` — the space's own, else
    /// the shared one (`shared`) — or null
    public Map<String, Object> templateRelease(String space, String shared, String key, int version) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT owner, language, envelope FROM " + tpl + "sdui_template_release_read WHERE owner IN (?, ?) AND template_key = ? AND version = ?"
            + " ORDER BY CASE WHEN owner = ? THEN 0 ELSE 1 END FETCH FIRST 1 ROWS ONLY", space, shared, key, version, space);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /// `[{ LANGUAGE, ENVELOPE }]`: the further languages of a template release
    public List<Map<String, Object>> templateLocales(Object owner, String key, int version) {
        return jdbc.queryForList("SELECT language, envelope FROM " + tpl + "sdui_template_release_locale_read WHERE owner = ? AND template_key = ? AND version = ?", owner, key, version);
    }

    /// the minimum app version on `platform` that carries template release `key@version` (the
    /// space's own template first, else the shared one), or null
    public String minimumClientVersion(String space, String shared, Object key, Object version, String platform) {
        List<String> rows = jdbc.queryForList("SELECT minimum_client_version FROM " + tpl + "sdui_template_support_read"
                + " WHERE owner IN (?, ?) AND template_key = ? AND version = ? AND platform = ? ORDER BY CASE WHEN owner = ? THEN 0 ELSE 1 END FETCH FIRST 1 ROWS ONLY",
            String.class, space, shared, key, version, platform, space);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /// `[{ merchantId, name }]`: active spaces with at least one live page
    public List<Map<String, Object>> liveSpaces() {
        return jdbc.queryForList("SELECT m.merchant_id AS \"merchantId\", m.name AS \"name\" FROM " + val + "sdui_merchant_read m"
            + " WHERE m.status = 'ACTIVE' AND EXISTS (SELECT 1 FROM " + val + "sdui_screen_read s WHERE s.merchant_id = m.merchant_id AND s.active_sequence IS NOT NULL)"
            + " ORDER BY m.merchant_id");
    }

    /// `{ SEQUENCE, TEMPLATE_KEY, TEMPLATE_VERSION, LANGUAGE, ENVELOPE }` of a page's live release, or null
    public Map<String, Object> liveRelease(String space, String key) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.sequence, r.template_key, r.template_version, r.language, r.envelope FROM " + val + "sdui_value_release_read r"
            + " JOIN " + val + "sdui_screen_read s ON s.merchant_id = r.merchant_id AND s.screen_key = r.screen_key AND s.active_sequence = r.sequence"
            + " WHERE r.merchant_id = ? AND r.screen_key = ?", space, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /// a page's releases before `sequence`, newest first (same columns as {@link #liveRelease})
    public List<Map<String, Object>> releasesBefore(String space, String key, Object sequence) {
        return jdbc.queryForList("SELECT sequence, template_key, template_version, language, envelope FROM " + val + "sdui_value_release_read"
            + " WHERE merchant_id = ? AND screen_key = ? AND sequence < ? ORDER BY sequence DESC", space, key, sequence);
    }

    /// `{ SEQUENCE, LANGUAGE, ENVELOPE }` of page release `sequence`, or null
    public Map<String, Object> release(String space, String key, int sequence) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT sequence, language, envelope FROM " + val + "sdui_value_release_read WHERE merchant_id = ? AND screen_key = ? AND sequence = ?",
            space, key, sequence);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /// `[{ LANGUAGE, ENVELOPE }]`: the further languages of a page release
    public List<Map<String, Object>> pageLocales(String space, String key, Object sequence) {
        return jdbc.queryForList("SELECT language, envelope FROM " + val + "sdui_value_release_locale_read WHERE merchant_id = ? AND screen_key = ? AND sequence = ?", space, key, sequence);
    }

    /// the envelope of a space's newest manifest, or null
    public String latestManifest(String space) {
        List<String> rows = jdbc.queryForList("SELECT envelope FROM " + val + "sdui_manifest_read WHERE merchant_id = ? ORDER BY sequence DESC FETCH FIRST 1 ROWS ONLY", String.class, space);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /// `{ CONTENT_TYPE, CONTENT_HASH, CONTENT }` of a published picture, or null
    public Map<String, Object> asset(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT content_type, content_hash, content FROM " + val + "sdui_asset_read WHERE asset_id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static String prefix(String schema) {
        if (schema == null || schema.isBlank()) return "";
        if (!schema.matches("^[A-Za-z][A-Za-z0-9_]*$")) throw new IllegalArgumentException("bad schema name " + schema);
        return schema + ".";
    }
}
