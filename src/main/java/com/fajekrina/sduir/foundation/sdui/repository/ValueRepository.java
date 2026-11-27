package com.fajekrina.sduir.foundation.sdui.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The page tables (`sdui_screen`, `sdui_value_revision`, `sdui_value_release`,
/// `sdui_value_release_locale`, `sdui_manifest`): reads and writes only, no decision — what may
/// be written is `ValueService`'s.
@Repository
public class ValueRepository {
    private static final String UNPUBLISHED = "CASE WHEN s.head_revision_id IS NULL OR r.sequence IS NOT NULL THEN FALSE ELSE TRUE END AS \"unpublished\"";

    private final JdbcTemplate jdbc;

    public ValueRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /// a kanal's pages with their head state, by key
    public List<Map<String, Object>> list(String mid) {
        return jdbc.queryForList("""
            SELECT s.screen_key AS "key", s.template_key AS "templateKey", s.template_version AS "templateVersion", s.status AS "status",
                   s.active_sequence AS "activeSequence", s.lock_version AS "lockVersion", s.updated_at AS "updatedAt",
                   """ + UNPUBLISHED + """
            FROM sdui_screen s LEFT JOIN sdui_value_release r ON r.revision_id = s.head_revision_id
            WHERE s.merchant_id = ? ORDER BY s.screen_key""", mid);
    }

    /// every page made from template `templateKey`, of every kanal
    public List<Map<String, Object>> pagesOf(String templateKey) {
        return jdbc.queryForList("""
            SELECT s.merchant_id AS "kanal", s.screen_key AS "key", s.template_key AS "templateKey", s.template_version AS "templateVersion",
                   s.active_sequence AS "activeSequence", s.status AS "status",
                   """ + UNPUBLISHED + """
            FROM sdui_screen s LEFT JOIN sdui_value_release r ON r.revision_id = s.head_revision_id
            WHERE s.template_key = ? ORDER BY s.merchant_id, s.screen_key""", templateKey);
    }

    /// every page of every kanal
    public List<Map<String, Object>> allPages() {
        return jdbc.queryForList("""
            SELECT s.merchant_id AS "kanal", s.screen_key AS "key", s.template_key AS "templateKey", s.template_version AS "templateVersion",
                   s.active_sequence AS "activeSequence", s.status AS "status", s.updated_at AS "updatedAt",
                   """ + UNPUBLISHED + """
            FROM sdui_screen s LEFT JOIN sdui_value_release r ON r.revision_id = s.head_revision_id
            ORDER BY s.merchant_id, s.screen_key""");
    }

    /// one page row (`key`, `templateKey`, `templateVersion`, `status`, `headRevisionId`,
    /// `activeSequence`, `lockVersion`, `updatedAt`), or null
    public Map<String, Object> find(String mid, String key) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT screen_key AS "key", template_key AS "templateKey", template_version AS "templateVersion", status AS "status",
                   head_revision_id AS "headRevisionId", active_sequence AS "activeSequence", lock_version AS "lockVersion", updated_at AS "updatedAt"
            FROM sdui_screen WHERE merchant_id = ? AND screen_key = ?""", mid, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /// `{ LOCK_VERSION, TEMPLATE_KEY }` of a page, or null
    public Map<String, Object> lockAndTemplate(String mid, String key) {
        return jdbc.queryForList("SELECT lock_version, template_key FROM sdui_screen WHERE merchant_id = ? AND screen_key = ?", mid, key)
            .stream().findFirst().orElse(null);
    }

    /// `{ TEMPLATE_KEY, HEAD_REVISION_ID, LOCK_VERSION }` of a page, or null
    public Map<String, Object> head(String mid, String key) {
        return jdbc.queryForList("SELECT template_key, head_revision_id, lock_version FROM sdui_screen WHERE merchant_id = ? AND screen_key = ?", mid, key)
            .stream().findFirst().orElse(null);
    }

    /// a kanal's page keys equal to `key` but for letter case
    public List<String> keysIgnoringCase(String mid, String key) {
        return jdbc.queryForList("SELECT screen_key FROM sdui_screen WHERE merchant_id = ? AND LOWER(screen_key) = LOWER(?)", String.class, mid, key);
    }

    public void insert(String mid, String key, String templateKey, int templateVersion) {
        jdbc.update("INSERT INTO sdui_screen (merchant_id, screen_key, template_key, template_version) VALUES (?, ?, ?, ?)", mid, key, templateKey, templateVersion);
    }

    public int nextRevisionNo(String mid, String key) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(revision_no), 0) + 1 FROM sdui_value_revision WHERE merchant_id = ? AND screen_key = ?", Integer.class, mid, key);
    }

    /// Append revision `revisionNo` and answer its id.
    public long appendRevision(String mid, String key, int revisionNo, String payload, String contentHash, String createdBy) {
        jdbc.update("INSERT INTO sdui_value_revision (merchant_id, screen_key, revision_no, payload, content_hash, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            mid, key, revisionNo, payload, contentHash, createdBy);
        return jdbc.queryForObject("SELECT revision_id FROM sdui_value_revision WHERE merchant_id = ? AND screen_key = ? AND revision_no = ?", Long.class, mid, key, revisionNo);
    }

    public String revisionPayload(Object revisionId) {
        return jdbc.queryForObject("SELECT payload FROM sdui_value_revision WHERE revision_id = ?", String.class, revisionId);
    }

    /// point the page at its new head revision (and template release)
    public void setHead(String mid, String key, long revisionId, String templateKey, int templateVersion) {
        jdbc.update("UPDATE sdui_screen SET head_revision_id = ?, template_key = ?, template_version = ?, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE merchant_id = ? AND screen_key = ?",
            revisionId, templateKey, templateVersion, mid, key);
    }

    /// whether revision `revisionId` is already a release
    public boolean isReleased(Object revisionId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_value_release WHERE revision_id = ?", Integer.class, revisionId);
        return n != null && n > 0;
    }

    public int nextSequence(String mid, String key) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) + 1 FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ?", Integer.class, mid, key);
    }

    public void insertRelease(String mid, String key, int sequence, long revisionId, String templateKey, int templateVersion, String payload,
                              String envelope, String contentHash, String keyId, String publishedBy, String language) {
        jdbc.update("""
            INSERT INTO sdui_value_release (merchant_id, screen_key, sequence, revision_id, template_key, template_version, payload, envelope, content_hash, key_id, published_by, language)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            mid, key, sequence, revisionId, templateKey, templateVersion, payload, envelope, contentHash, keyId, publishedBy, language);
    }

    public void insertReleaseLocale(String mid, String key, int sequence, String language, String envelope, String contentHash, String keyId) {
        jdbc.update("INSERT INTO sdui_value_release_locale (merchant_id, screen_key, sequence, language, envelope, content_hash, key_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            mid, key, sequence, language, envelope, contentHash, keyId);
    }

    /// make release `sequence` the live one
    public void activate(String mid, String key, int sequence) {
        jdbc.update("UPDATE sdui_screen SET active_sequence = ?, status = 'PUBLISHED', lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE merchant_id = ? AND screen_key = ?",
            sequence, mid, key);
    }

    public boolean releaseExists(String mid, String key, int sequence) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ? AND sequence = ?", Integer.class, mid, key, sequence);
        return n != null && n > 0;
    }

    /// one release with its payload as stored text, or null
    public Map<String, Object> release(String mid, String key, int sequence) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT screen_key AS "key", sequence AS "sequence", template_key AS "templateKey", template_version AS "templateVersion",
                   content_hash AS "contentHash", key_id AS "keyId", published_by AS "publishedBy", released_at AS "releasedAt", payload AS "payload"
            FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ? AND sequence = ?""", mid, key, sequence);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    /// the releases of a page, newest first (no payload)
    public List<Map<String, Object>> releases(String mid, String key) {
        return jdbc.queryForList("""
            SELECT sequence AS "sequence", template_key AS "templateKey", template_version AS "templateVersion", published_by AS "publishedBy", released_at AS "releasedAt"
            FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ? ORDER BY sequence DESC""", mid, key);
    }

    /// `screen key -> active sequence` of a kanal's live pages
    public Map<String, Integer> activeSequences(String mid) {
        Map<String, Integer> out = new LinkedHashMap<>();
        jdbc.queryForList("SELECT screen_key, active_sequence FROM sdui_screen WHERE merchant_id = ? AND active_sequence IS NOT NULL", mid)
            .forEach((r) -> out.put((String) r.get("SCREEN_KEY"), ((Number) r.get("ACTIVE_SEQUENCE")).intValue()));
        return out;
    }

    public void insertManifest(String mid, long sequence, String body, String envelope, String etag, String keyId) {
        jdbc.update("INSERT INTO sdui_manifest (merchant_id, sequence, body, envelope, etag, key_id) VALUES (?, ?, ?, ?, ?, ?)",
            mid, sequence, body, envelope, etag, keyId);
    }

    /// `{ sequence, body }` of a kanal's newest manifest, or null
    public Map<String, Object> latestManifest(String mid) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT sequence AS \"sequence\", body AS \"body\" FROM sdui_manifest WHERE merchant_id = ? ORDER BY sequence DESC FETCH FIRST 1 ROWS ONLY", mid);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM sdui_screen", Integer.class); }
}
