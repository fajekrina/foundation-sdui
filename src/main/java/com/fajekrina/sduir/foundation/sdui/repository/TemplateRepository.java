package com.fajekrina.sduir.foundation.sdui.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The template tables (`sdui_template`, `sdui_template_revision`, `sdui_template_release`,
/// `sdui_template_release_locale`, `sdui_template_support`): reads and writes only, no decision —
/// what may be written is `TemplateService`'s.
@Repository
public class TemplateRepository {
    private static final String DEPRECATION_COLUMNS = """
        CASE WHEN t.deprecated_at IS NULL THEN FALSE ELSE TRUE END AS "deprecated", t.deprecation_reason AS "deprecationReason",
        t.replaced_by AS "replacedBy", t.deprecated_by AS "deprecatedBy", t.deprecated_at AS "deprecatedAt\"""";

    private final JdbcTemplate jdbc;

    public TemplateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /// `owner`'s templates with their head state, by key
    public List<Map<String, Object>> list(String owner) {
        return jdbc.queryForList("""
            SELECT t.template_key AS "key", t.title AS "title", t.status AS "status", t.active_version AS "activeVersion",
                   t.lock_version AS "lockVersion", t.updated_at AS "updatedAt",
                   CASE WHEN t.head_revision_id IS NULL OR r.version IS NOT NULL THEN FALSE ELSE TRUE END AS "unpublished",
                   """ + DEPRECATION_COLUMNS + """
             FROM sdui_template t
              LEFT JOIN sdui_template_revision v ON v.revision_id = t.head_revision_id
              LEFT JOIN sdui_template_release r ON r.revision_id = v.revision_id
            WHERE t.owner = ? ORDER BY t.template_key""", owner);
    }

    /// one template row (`key`, `title`, `status`, `headRevisionId`, `activeVersion`, `lockVersion`,
    /// `updatedAt`, deprecation), or null
    public Map<String, Object> find(String owner, String key) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT t.template_key AS "key", t.title AS "title", t.status AS "status", t.head_revision_id AS "headRevisionId",
                   t.active_version AS "activeVersion", t.lock_version AS "lockVersion", t.updated_at AS "updatedAt",
                   """ + DEPRECATION_COLUMNS + """
             FROM sdui_template t WHERE t.owner = ? AND t.template_key = ?""", owner, key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean exists(String owner, String key) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template WHERE owner = ? AND template_key = ?", Integer.class, owner, key);
        return n != null && n > 0;
    }

    /// `{ LOCK_VERSION, ACTIVE_VERSION }` of a template, or null
    public Map<String, Object> lockAndActive(String owner, String key) {
        return jdbc.queryForList("SELECT lock_version, active_version FROM sdui_template WHERE owner = ? AND template_key = ?", owner, key)
            .stream().findFirst().orElse(null);
    }

    /// `{ ACTIVE_VERSION, DEPRECATED_AT }` of a template, or null
    public Map<String, Object> activeAndDeprecation(String owner, String key) {
        return jdbc.queryForList("SELECT active_version, deprecated_at FROM sdui_template WHERE owner = ? AND template_key = ?", owner, key)
            .stream().findFirst().orElse(null);
    }

    /// keys equal to `key` but for letter case, of any owner
    public List<String> keysIgnoringCase(String key) {
        return jdbc.queryForList("SELECT template_key FROM sdui_template WHERE LOWER(template_key) = LOWER(?)", String.class, key);
    }

    /// keys equal to `key` but for letter case, of the two owners
    public List<String> keysIgnoringCase(String owner, String other, String key) {
        return jdbc.queryForList("SELECT template_key FROM sdui_template WHERE owner IN (?, ?) AND LOWER(template_key) = LOWER(?)", String.class, owner, other, key);
    }

    public void insert(String owner, String key, String title) {
        jdbc.update("INSERT INTO sdui_template (owner, template_key, title) VALUES (?, ?, ?)", owner, key, title);
    }

    /// Append revision `revisionNo` and answer its id.
    public long appendRevision(String owner, String key, int revisionNo, String payload, String contentHash, String createdBy) {
        jdbc.update("INSERT INTO sdui_template_revision (owner, template_key, revision_no, payload, content_hash, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            owner, key, revisionNo, payload, contentHash, createdBy);
        return jdbc.queryForObject("SELECT revision_id FROM sdui_template_revision WHERE owner = ? AND template_key = ? AND revision_no = ?", Long.class, owner, key, revisionNo);
    }

    public int nextRevisionNo(String owner, String key) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(revision_no), 0) + 1 FROM sdui_template_revision WHERE owner = ? AND template_key = ?", Integer.class, owner, key);
    }

    public String revisionPayload(Object revisionId) {
        return jdbc.queryForObject("SELECT payload FROM sdui_template_revision WHERE revision_id = ?", String.class, revisionId);
    }

    /// point the template at its new head revision (and title)
    public void setHead(String owner, String key, long revisionId, String title) {
        jdbc.update("UPDATE sdui_template SET head_revision_id = ?, title = ?, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?",
            revisionId, title, owner, key);
    }

    public boolean releaseExists(String owner, String key, int version) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template_release WHERE owner = ? AND template_key = ? AND version = ?", Integer.class, owner, key, version);
        return n != null && n > 0;
    }

    public void insertRelease(String owner, String key, int version, long revisionId, String title, String payload, String envelope,
                              String contentHash, String keyId, String publishedBy, String language) {
        jdbc.update("""
            INSERT INTO sdui_template_release (owner, template_key, version, revision_id, title, payload, envelope, content_hash, key_id, published_by, language)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            owner, key, version, revisionId, title, payload, envelope, contentHash, keyId, publishedBy, language);
    }

    public void insertReleaseLocale(String owner, String key, int version, String language, String envelope, String contentHash, String keyId) {
        jdbc.update("INSERT INTO sdui_template_release_locale (owner, template_key, version, language, envelope, content_hash, key_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
            owner, key, version, language, envelope, contentHash, keyId);
    }

    /// make release `version` the active one
    public void activate(String owner, String key, int version) {
        jdbc.update("UPDATE sdui_template SET active_version = ?, status = 'PUBLISHED', lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?",
            version, owner, key);
    }

    /// one release with its payload as stored text, or null
    public Map<String, Object> release(String owner, String key, int version) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT template_key AS "key", version AS "version", title AS "title", content_hash AS "contentHash", key_id AS "keyId",
                   published_by AS "publishedBy", released_at AS "releasedAt", payload AS "payload"
            FROM sdui_template_release WHERE owner = ? AND template_key = ? AND version = ?""", owner, key, version);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    /// the releases of a template, newest first (no payload)
    public List<Map<String, Object>> releases(String owner, String key) {
        return jdbc.queryForList("""
            SELECT version AS "version", title AS "title", published_by AS "publishedBy", released_at AS "releasedAt"
            FROM sdui_template_release WHERE owner = ? AND template_key = ? ORDER BY version DESC""", owner, key);
    }

    /// a release's document as stored, or null
    public String releasedPayload(String owner, String key, int version) {
        try {
            return jdbc.queryForObject("SELECT payload FROM sdui_template_release WHERE owner = ? AND template_key = ? AND version = ?", String.class, owner, key, version);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /// `owner`'s released templates: the active release of each key, or every release
    public List<Map<String, Object>> catalog(String owner, boolean all) {
        String dep = "CASE WHEN t.deprecated_at IS NULL THEN FALSE ELSE TRUE END AS \"deprecated\", "
            + "t.deprecation_reason AS \"deprecationReason\", t.replaced_by AS \"replacedBy\"";
        return new ArrayList<>(jdbc.queryForList(all
            ? "SELECT r.template_key AS \"key\", r.version AS \"version\", r.title AS \"title\", " + dep
              + " FROM sdui_template_release r JOIN sdui_template t ON t.owner = r.owner AND t.template_key = r.template_key"
              + " WHERE r.owner = ? ORDER BY r.template_key, r.version DESC"
            : "SELECT r.template_key AS \"key\", r.version AS \"version\", r.title AS \"title\", " + dep
              + " FROM sdui_template_release r JOIN sdui_template t ON t.owner = r.owner AND t.template_key = r.template_key"
              + " AND t.active_version = r.version AND t.status = 'PUBLISHED' WHERE r.owner = ? ORDER BY r.template_key", owner)
            .stream().map((m) -> (Map<String, Object>) new LinkedHashMap<>(m)).toList());
    }

    public void deprecate(String owner, String key, String by, String reason, String replacedBy) {
        jdbc.update("""
            UPDATE sdui_template SET deprecated_at = CURRENT_TIMESTAMP, deprecated_by = ?, deprecation_reason = ?, replaced_by = ?,
                   lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?""",
            by, reason, replacedBy, owner, key);
    }

    public void restore(String owner, String key) {
        jdbc.update("""
            UPDATE sdui_template SET deprecated_at = NULL, deprecated_by = NULL, deprecation_reason = NULL, replaced_by = NULL,
                   lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?""", owner, key);
    }

    /// owners other than `except` with a released, not deprecated template `key`
    public List<String> releasedOwnersExcept(String key, String except) {
        return jdbc.queryForList("SELECT owner FROM sdui_template WHERE template_key = ? AND owner <> ? AND deprecated_at IS NULL AND active_version IS NOT NULL",
            String.class, key, except);
    }

    public List<Map<String, Object>> support(String owner, String key, int version) {
        return jdbc.queryForList("""
            SELECT platform AS "platform", minimum_client_version AS "minimumClientVersion", recorded_by AS "recordedBy", recorded_at AS "recordedAt"
            FROM sdui_template_support WHERE owner = ? AND template_key = ? AND version = ? ORDER BY platform""", owner, key, version);
    }

    /// replace the platforms that carry a release (`platform -> minimum version`)
    public void replaceSupport(String owner, String key, int version, Map<String, String> minimumByPlatform, String recordedBy) {
        jdbc.update("DELETE FROM sdui_template_support WHERE owner = ? AND template_key = ? AND version = ?", owner, key, version);
        minimumByPlatform.forEach((platform, minimum) -> jdbc.update("""
            INSERT INTO sdui_template_support (owner, template_key, version, platform, minimum_client_version, recorded_by) VALUES (?, ?, ?, ?, ?, ?)""",
            owner, key, version, platform, minimum, recordedBy));
    }

    public int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template", Integer.class); }

    public int countOwnedByOthersThan(String owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template WHERE owner <> ?", Integer.class, owner);
    }
}
