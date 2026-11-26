package com.fajekrina.sduir.sdui.authoring.template;

import com.fajekrina.sduir.sdui.core.SduiActor;
import com.fajekrina.sduir.sdui.core.SduiAudit;
import com.fajekrina.sduir.sdui.core.Signer;
import com.fajekrina.sduir.sdui.web.Json;
import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.contract.SpecContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Templates: drafts (immutable revisions, optimistic lock) and direct publish to a signed,
/// immutable release `owner/key@version`. A template key can be DEPRECATED (catalog state, not
/// part of the signed document): no new page may use it; existing pages keep working.
///
/// Templates are SHARED by every Studio user (owner {@link #SHARED}); who made and published a
/// version is in its revisions and releases. Older templates were owned by one kanal (formerly a
/// merchant) and still are: a kanal's catalog reads its own key first, then the shared one. A new
/// key may not clash with any existing key (of any owner), so that lookup stays unambiguous.
@Service
public class TemplateService {
    /// the owner of every template made in the Studio (shared by all users)
    public static final String SHARED = "sdui";

    private final JdbcTemplate jdbc;
    private final SpecContract contract;
    private final Signer signer;
    private final SduiAudit audit;

    public TemplateService(JdbcTemplate jdbc, SpecContract contract, Signer signer, SduiAudit audit) {
        this.jdbc = jdbc;
        this.contract = contract;
        this.signer = signer;
        this.audit = audit;
    }

    private static final String DEPRECATION_COLUMNS = """
        CASE WHEN t.deprecated_at IS NULL THEN FALSE ELSE TRUE END AS "deprecated", t.deprecation_reason AS "deprecationReason",
        t.replaced_by AS "replacedBy", t.deprecated_by AS "deprecatedBy", t.deprecated_at AS "deprecatedAt\"""";

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

    /// The owner whose `key` a kanal reads: its own (an older, kanal-owned template), else the shared one.
    public String ownerFor(String kanal, String key) {
        if (kanal != null && !kanal.equals(SHARED)) {
            Integer own = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template WHERE owner = ? AND template_key = ?", Integer.class, kanal, key);
            if (own != null && own > 0) return kanal;
        }
        return SHARED;
    }

    public Map<String, Object> get(String owner, String key) {
        Map<String, Object> t = row(owner, key);
        Map<String, Object> out = new LinkedHashMap<>(t);
        Object head = t.get("headRevisionId");
        out.put("draft", head == null ? null : Json.parse(jdbc.queryForObject("SELECT payload FROM sdui_template_revision WHERE revision_id = ?", String.class, head)));
        Integer active = (Integer) t.get("activeVersion");
        out.put("active", active == null ? null : release(owner, key, active));
        out.put("releases", jdbc.queryForList("""
            SELECT version AS "version", title AS "title", published_by AS "publishedBy", released_at AS "releasedAt"
            FROM sdui_template_release WHERE owner = ? AND template_key = ? ORDER BY version DESC""", owner, key));
        return out;
    }

    /// Save a draft of one of the caller's templates. The key in the path wins; the version
    /// is set to the next release.
    @Transactional
    public Map<String, Object> saveDraft(String key, JsonNode payload, Integer lockVersion, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        return saveDraft(SHARED, key, payload, lockVersion, actor);
    }

    /// Save a draft of `owner`'s template (seeders; the Studio saves shared ones through the method above).
    public Map<String, Object> saveDraft(String owner, String key, JsonNode payload, Integer lockVersion, SduiActor actor) {
        if (!SpecContract.isIdentifier(key)) throw SduiProblem.badRequest("template key must be an identifier");
        Map<String, Object> existing = jdbc.queryForList("SELECT lock_version, active_version FROM sdui_template WHERE owner = ? AND template_key = ?", owner, key)
            .stream().findFirst().orElse(null);
        int next = existing == null || existing.get("ACTIVE_VERSION") == null ? 1 : (Integer) existing.get("ACTIVE_VERSION") + 1;
        if (!(payload instanceof ObjectNode doc) || !(doc.get("template") instanceof ObjectNode t)) throw SduiProblem.invalid("expected a template document { schemaVersion, template }");
        t.put("key", key);
        t.put("version", next);
        contract.validateTemplate(doc);
        byte[] bytes = Json.canonical(doc);
        String title = t.path("title").isTextual() ? t.path("title").asText() : null;
        if (existing == null) {
            // keys that differ only by letter case are one template to people (and to the pages named after it)
            // …and a new shared key is unique over every owner (a kanal reads its own key, then the shared one)
            List<String> clash = owner.equals(SHARED)
                ? jdbc.queryForList("SELECT template_key FROM sdui_template WHERE LOWER(template_key) = LOWER(?)", String.class, key)
                : jdbc.queryForList("SELECT template_key FROM sdui_template WHERE owner IN (?, ?) AND LOWER(template_key) = LOWER(?)", String.class, owner, SHARED, key);
            if (!clash.isEmpty()) throw SduiProblem.conflict("template " + clash.get(0) + " already exists; a key may not differ from it only by letter case");
            jdbc.update("INSERT INTO sdui_template (owner, template_key, title) VALUES (?, ?, ?)", owner, key, title);
        } else {
            int current = (Integer) existing.get("LOCK_VERSION");
            if (lockVersion == null || lockVersion != current) throw SduiProblem.conflict("template " + key + " changed (lockVersion " + current + ")");
        }
        int revisionNo = jdbc.queryForObject("SELECT COALESCE(MAX(revision_no), 0) + 1 FROM sdui_template_revision WHERE owner = ? AND template_key = ?", Integer.class, owner, key);
        jdbc.update("INSERT INTO sdui_template_revision (owner, template_key, revision_no, payload, content_hash, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            owner, key, revisionNo, new String(bytes, StandardCharsets.UTF_8), Json.sha256(bytes), actor.sub());
        long revisionId = jdbc.queryForObject("SELECT revision_id FROM sdui_template_revision WHERE owner = ? AND template_key = ? AND revision_no = ?", Long.class, owner, key, revisionNo);
        jdbc.update("UPDATE sdui_template SET head_revision_id = ?, title = ?, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?",
            revisionId, title, owner, key);
        audit.record(actor, owner, "TEMPLATE_DRAFT_SAVED", "template", key, Map.of("revision", revisionNo, "version", next));
        return get(owner, key);
    }

    /// Publish the draft (no review step): it becomes release `version` at once.
    @Transactional
    public Map<String, Object> publish(String key, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        String owner = SHARED;
        Map<String, Object> t = row(owner, key);
        Object head = t.get("headRevisionId");
        if (head == null) throw SduiProblem.conflict("template " + key + " has no draft");
        JsonNode payload = Json.parse(jdbc.queryForObject("SELECT payload FROM sdui_template_revision WHERE revision_id = ?", String.class, head));
        Map<String, Object> release = publish(owner, key, payload, ((Number) head).longValue(), actor.sub());
        audit.record(actor, owner, "TEMPLATE_PUBLISHED", "template", key, Map.of("version", release.get("version")));
        return get(owner, key);
    }

    /// Freeze the bytes, sign them, write the immutable release, point the template at it.
    public Map<String, Object> publish(String owner, String key, JsonNode payload, long revisionId, String publishedBy) {
        contract.validateTemplate(payload);
        int version = payload.path("template").path("version").asInt();
        Integer taken = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template_release WHERE owner = ? AND template_key = ? AND version = ?", Integer.class, owner, key, version);
        if (taken != null && taken > 0) throw SduiProblem.conflict("template " + key + "@" + version + " is already published; save a change first");
        byte[] bytes = Json.canonical(payload);
        // spec 2.8: the release row signs the default language, each further language its own row (no `locales` in either)
        String language = payload.path("template").path("language").asText("");
        byte[] envelope = signer.envelope(Json.canonical(com.fajekrina.sduir.sdui.contract.SduiLocales.resolveTemplate(payload, language)), SpecContract.SCHEMA_VERSION);
        String title = payload.path("template").path("title").isTextual() ? payload.path("template").path("title").asText() : null;
        jdbc.update("""
            INSERT INTO sdui_template_release (owner, template_key, version, revision_id, title, payload, envelope, content_hash, key_id, published_by, language)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            owner, key, version, revisionId, title, new String(bytes, StandardCharsets.UTF_8), new String(envelope, StandardCharsets.UTF_8),
            Json.sha256(bytes), signer.keyId(), publishedBy, language.isEmpty() ? null : language);
        for (String lang : com.fajekrina.sduir.sdui.contract.SduiLocales.languages(payload)) {
            byte[] inLanguage = Json.canonical(com.fajekrina.sduir.sdui.contract.SduiLocales.resolveTemplate(payload, lang));
            jdbc.update("INSERT INTO sdui_template_release_locale (owner, template_key, version, language, envelope, content_hash, key_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                owner, key, version, lang, new String(signer.envelope(inLanguage, SpecContract.SCHEMA_VERSION), StandardCharsets.UTF_8), Json.sha256(inLanguage), signer.keyId());
        }
        jdbc.update("UPDATE sdui_template SET active_version = ?, status = 'PUBLISHED', lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?",
            version, owner, key);
        return release(owner, key, version);
    }

    /// Released templates a kanal may use (the Konten tab's template picker): the shared ones and
    /// the kanal's own older ones; the latest of each key, or every version — deprecated ones
    /// included and marked (`deprecated`, `deprecationReason`, `replacedBy`): a page that uses one
    /// still needs it; a new page may not pick it. `shared: true` marks a shared template.
    public List<Map<String, Object>> catalog(String owner, boolean all) {
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        Map<String, Boolean> own = new java.util.HashMap<>();
        if (!owner.equals(SHARED)) for (Map<String, Object> r : catalogOf(owner, all)) { own.put((String) r.get("key"), true); r.put("shared", false); out.add(r); }
        for (Map<String, Object> r : catalogOf(SHARED, all)) if (!own.containsKey((String) r.get("key"))) { r.put("shared", true); out.add(r); }
        out.sort(java.util.Comparator.comparing((Map<String, Object> r) -> (String) r.get("key")));
        return out;
    }

    private List<Map<String, Object>> catalogOf(String owner, boolean all) {
        String dep = "CASE WHEN t.deprecated_at IS NULL THEN FALSE ELSE TRUE END AS \"deprecated\", "
            + "t.deprecation_reason AS \"deprecationReason\", t.replaced_by AS \"replacedBy\"";
        return new java.util.ArrayList<>(jdbc.queryForList(all
            ? "SELECT r.template_key AS \"key\", r.version AS \"version\", r.title AS \"title\", " + dep
              + " FROM sdui_template_release r JOIN sdui_template t ON t.owner = r.owner AND t.template_key = r.template_key"
              + " WHERE r.owner = ? ORDER BY r.template_key, r.version DESC"
            : "SELECT r.template_key AS \"key\", r.version AS \"version\", r.title AS \"title\", " + dep
              + " FROM sdui_template_release r JOIN sdui_template t ON t.owner = r.owner AND t.template_key = r.template_key"
              + " AND t.active_version = r.version AND t.status = 'PUBLISHED' WHERE r.owner = ? ORDER BY r.template_key", owner)
            .stream().map((m) -> (Map<String, Object>) new LinkedHashMap<>(m)).toList());
    }

    /// Whether a template key may start a new page: its active release and deprecation.
    public Map<String, Object> status(String kanal, String key) {
        Map<String, Object> t = row(ownerFor(kanal, key), key);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : new String[]{"key", "activeVersion", "deprecated", "deprecationReason", "replacedBy"}) out.put(k, t.get(k));
        return out;
    }

    /// Deprecate one of the caller's templates: no new page can start from it (value-service
    /// refuses), the Studio marks it. Live pages are untouched. `replacedBy`, when given, is
    /// another released template of the same owner that is not itself deprecated.
    @Transactional
    public Map<String, Object> deprecate(String key, String reason, String replacedBy, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        String owner = SHARED;
        Map<String, Object> t = row(owner, key);
        if (reason == null || reason.isBlank()) throw SduiProblem.invalid("a deprecation needs a reason");
        if (reason.length() > 4096) throw SduiProblem.invalid("reason: at most 4096 characters");
        if (t.get("activeVersion") == null) throw SduiProblem.conflict("template " + key + " has no release; delete the draft instead of deprecating it");
        String replacement = replacedBy == null || replacedBy.isBlank() ? null : replacedBy.trim();
        if (replacement != null) {
            if (replacement.equals(key)) throw SduiProblem.invalid("a template cannot replace itself");
            List<Map<String, Object>> r = jdbc.queryForList("SELECT active_version, deprecated_at FROM sdui_template WHERE owner = ? AND template_key = ?", owner, replacement);
            if (r.isEmpty() || r.get(0).get("ACTIVE_VERSION") == null) throw SduiProblem.invalid("replacement " + replacement + " is not a released template");
            if (r.get(0).get("DEPRECATED_AT") != null) throw SduiProblem.invalid("replacement " + replacement + " is deprecated itself");
        }
        jdbc.update("""
            UPDATE sdui_template SET deprecated_at = CURRENT_TIMESTAMP, deprecated_by = ?, deprecation_reason = ?, replaced_by = ?,
                   lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?""",
            actor.sub(), reason.trim(), replacement, owner, key);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", reason.trim());
        detail.put("replacedBy", replacement == null ? "" : replacement);
        audit.record(actor, owner, Boolean.TRUE.equals(t.get("deprecated")) ? "TEMPLATE_DEPRECATION_CHANGED" : "TEMPLATE_DEPRECATED", "template", key, detail);
        return get(owner, key);
    }

    /// Take a deprecation back: the template is offered to new pages again.
    @Transactional
    public Map<String, Object> restore(String key, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        String owner = SHARED;
        Map<String, Object> t = row(owner, key);
        if (!Boolean.TRUE.equals(t.get("deprecated"))) throw SduiProblem.conflict("template " + key + " is not deprecated");
        jdbc.update("""
            UPDATE sdui_template SET deprecated_at = NULL, deprecated_by = NULL, deprecation_reason = NULL, replaced_by = NULL,
                   lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?""", owner, key);
        audit.record(actor, owner, "TEMPLATE_RESTORED", "template", key, Map.of("reason", String.valueOf(t.get("deprecationReason"))));
        return get(owner, key);
    }

    /// A released template document (plain; delivery serves the envelope).
    public String releasedPayload(String kanal, String key, int version) {
        String owner = ownerFor(kanal, key);
        try {
            return jdbc.queryForObject("SELECT payload FROM sdui_template_release WHERE owner = ? AND template_key = ? AND version = ?", String.class, owner, key, version);
        } catch (EmptyResultDataAccessException e) {
            throw SduiProblem.notFound("template " + key + "@" + version + " not released");
        }
    }

    /// The head revision's document (the draft; after a publish, the released one).
    public String draftPayload(String kanal, String key) {
        String owner = ownerFor(kanal, key);
        Object head = row(owner, key).get("headRevisionId");
        if (head == null) throw SduiProblem.notFound("template " + key + " has no draft");
        return jdbc.queryForObject("SELECT payload FROM sdui_template_revision WHERE revision_id = ?", String.class, head);
    }

    /// Which app versions carry shared template release `key@version` (native apps build every
    /// template themselves): `[{ platform, minimumClientVersion, recordedBy, recordedAt }]`.
    public List<Map<String, Object>> support(String key, int version) {
        releaseExists(key, version);
        return jdbc.queryForList("""
            SELECT platform AS "platform", minimum_client_version AS "minimumClientVersion", recorded_by AS "recordedBy", recorded_at AS "recordedAt"
            FROM sdui_template_support WHERE owner = ? AND template_key = ? AND version = ? ORDER BY platform""", SHARED, key, version);
    }

    /// Replace the platforms that carry release `key@version`: `[{ platform, minimumClientVersion }]`
    /// (`android`, `ios`, `mpaas`; numeric dotted versions). A platform left out carries it in no
    /// version: delivery gives its apps the page's fallback instead. The Studio admin or a service
    /// (an app's release pipeline) records it.
    @Transactional
    public List<Map<String, Object>> setSupport(String key, int version, JsonNode rows, SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        releaseExists(key, version);
        if (rows == null || !rows.isArray()) throw SduiProblem.invalid("expected [{ platform, minimumClientVersion }]");
        Map<String, String> wanted = new LinkedHashMap<>();
        for (JsonNode r : rows) {
            String platform = r.path("platform").asText("");
            String minimum = r.path("minimumClientVersion").asText("");
            if (!com.fajekrina.sduir.sdui.core.ClientInfo.NATIVE.contains(platform)) throw SduiProblem.invalid("platform must be one of android, ios, mpaas: " + platform);
            if (!com.fajekrina.sduir.sdui.core.ClientInfo.isVersion(minimum)) throw SduiProblem.invalid("minimumClientVersion must be a version like 5.4.0: " + minimum);
            if (wanted.put(platform, minimum) != null) throw SduiProblem.invalid("platform " + platform + " given twice");
        }
        jdbc.update("DELETE FROM sdui_template_support WHERE owner = ? AND template_key = ? AND version = ?", SHARED, key, version);
        wanted.forEach((platform, minimum) -> jdbc.update("""
            INSERT INTO sdui_template_support (owner, template_key, version, platform, minimum_client_version, recorded_by) VALUES (?, ?, ?, ?, ?, ?)""",
            SHARED, key, version, platform, minimum, actor.sub()));
        audit.record(actor, SHARED, "TEMPLATE_SUPPORT_SET", "template", key + "@" + version, wanted);
        return support(key, version);
    }

    private void releaseExists(String key, int version) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template_release WHERE owner = ? AND template_key = ? AND version = ?", Integer.class, SHARED, key, version);
        if (n == null || n == 0) throw SduiProblem.notFound("template " + key + "@" + version + " not released");
    }

    public int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template", Integer.class); }

    /// How many templates are owned by a kanal (not shared): the demo seed runs when there are none.
    public int countOwned() { return jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template WHERE owner <> ?", Integer.class, SHARED); }

    /// Whether a shared template `key` exists (the starter seeder).
    public boolean hasShared(String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sdui_template WHERE LOWER(template_key) = LOWER(?)", Integer.class, key) > 0;
    }

    /// Mark older kanal-owned templates `keys` deprecated in favour of a shared one (the starter
    /// seeder; once — a template already deprecated is left as it is). Answers how many changed.
    public int deprecateLegacy(List<String> keys, String replacedBy, String reason, SduiActor actor) {
        int changed = 0;
        for (String key : keys) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT owner FROM sdui_template WHERE template_key = ? AND owner <> ? AND deprecated_at IS NULL AND active_version IS NOT NULL", key, SHARED);
            for (Map<String, Object> r : rows) {
                String owner = (String) r.get("OWNER");
                // replaced_by names a template of the same owner in Studio deprecations; a shared one is fine here
                jdbc.update("""
                    UPDATE sdui_template SET deprecated_at = CURRENT_TIMESTAMP, deprecated_by = ?, deprecation_reason = ?, replaced_by = ?,
                           lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE owner = ? AND template_key = ?""",
                    actor.sub(), reason, replacedBy, owner, key);
                audit.record(actor, owner, "TEMPLATE_DEPRECATED", "template", key, Map.of("reason", reason, "replacedBy", replacedBy));
                changed++;
            }
        }
        return changed;
    }

    private Map<String, Object> release(String owner, String key, int version) {
        Map<String, Object> out = new LinkedHashMap<>(jdbc.queryForMap("""
            SELECT template_key AS "key", version AS "version", title AS "title", content_hash AS "contentHash", key_id AS "keyId",
                   published_by AS "publishedBy", released_at AS "releasedAt", payload AS "payload"
            FROM sdui_template_release WHERE owner = ? AND template_key = ? AND version = ?""", owner, key, version));
        out.put("payload", Json.parse((String) out.get("payload")));
        return out;
    }

    private Map<String, Object> row(String owner, String key) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT t.template_key AS "key", t.title AS "title", t.status AS "status", t.head_revision_id AS "headRevisionId",
                   t.active_version AS "activeVersion", t.lock_version AS "lockVersion", t.updated_at AS "updatedAt",
                   """ + DEPRECATION_COLUMNS + """
             FROM sdui_template t WHERE t.owner = ? AND t.template_key = ?""", owner, key);
        if (rows.isEmpty()) throw SduiProblem.notFound("template " + key + " not found");
        return rows.get(0);
    }
}
