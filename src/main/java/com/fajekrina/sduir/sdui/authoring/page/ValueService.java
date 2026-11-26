package com.fajekrina.sduir.sdui.authoring.page;

import com.fajekrina.sduir.sdui.core.SduiActor;
import com.fajekrina.sduir.sdui.core.SduiAudit;
import com.fajekrina.sduir.sdui.core.Signer;
import com.fajekrina.sduir.sdui.web.Json;
import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.contract.SpecContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/// Value sets: one per page of a kanal (formerly a merchant screen), pinned to a release of the
/// template it is made from (a shared template, or one the kanal owns from before). From spec
/// 2.6 a page may set any prop of its template's components and their actions (it stores only
/// what differs). Drafts are validated against that release; publishing (direct, no review
/// step) writes a signed immutable release with the next per-page sequence and a new signed
/// manifest for the kanal. Several pages may use one template.
@Service
public class ValueService {
    private final JdbcTemplate jdbc;
    private final SpecContract contract;
    private final Signer signer;
    private final SduiAudit audit;
    private final TemplateSource templates;
    private final Merchants merchants;
    private final AssetService assets;

    public ValueService(JdbcTemplate jdbc, SpecContract contract, Signer signer, SduiAudit audit, TemplateSource templates,
                        Merchants merchants, AssetService assets) {
        this.jdbc = jdbc;
        this.contract = contract;
        this.signer = signer;
        this.audit = audit;
        this.templates = templates;
        this.merchants = merchants;
        this.assets = assets;
    }

    /// The merchants the caller may work on: an admin its own, a service every one.
    public List<Map<String, Object>> merchants(SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        return jdbc.queryForList("SELECT merchant_id AS \"merchantId\", name AS \"name\", manifest_sequence AS \"manifestSequence\" FROM sdui_merchant WHERE merchant_id <> ? ORDER BY merchant_id", Merchants.SHARED)
            .stream().filter((m) -> actor.merchants().contains("*") || actor.merchants().contains((String) m.get("merchantId"))).toList();
    }

    public List<Map<String, Object>> list(String mid, SduiActor actor) {
        merchant(mid, actor);
        return jdbc.queryForList("""
            SELECT s.screen_key AS "key", s.template_key AS "templateKey", s.template_version AS "templateVersion", s.status AS "status",
                   s.active_sequence AS "activeSequence", s.lock_version AS "lockVersion", s.updated_at AS "updatedAt",
                   CASE WHEN s.head_revision_id IS NULL OR r.sequence IS NOT NULL THEN FALSE ELSE TRUE END AS "unpublished"
            FROM sdui_screen s LEFT JOIN sdui_value_release r ON r.revision_id = s.head_revision_id
            WHERE s.merchant_id = ? ORDER BY s.screen_key""", mid);
    }

    public Map<String, Object> get(String mid, String key, SduiActor actor) {
        merchant(mid, actor);
        Map<String, Object> s = row(mid, key);
        Map<String, Object> out = new LinkedHashMap<>(s);
        Object head = s.get("headRevisionId");
        out.put("draft", head == null ? null : Json.parse(jdbc.queryForObject("SELECT payload FROM sdui_value_revision WHERE revision_id = ?", String.class, head)));
        Integer active = (Integer) s.get("activeSequence");
        out.put("active", active == null ? null : release(mid, key, active));
        out.put("releases", jdbc.queryForList("""
            SELECT sequence AS "sequence", template_key AS "templateKey", template_version AS "templateVersion", published_by AS "publishedBy", released_at AS "releasedAt"
            FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ? ORDER BY sequence DESC""", mid, key));
        return out;
    }

    /// Save a draft value set. The screen key in the path wins; the template release it
    /// names must be one of this merchant's and the values must fit it.
    @Transactional
    public Map<String, Object> saveDraft(String mid, String key, JsonNode payload, Integer lockVersion, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        merchant(mid, actor);
        if (!SpecContract.isIdentifier(key)) throw SduiProblem.badRequest("screen key must be an identifier");
        if (!(payload instanceof ObjectNode doc) || !(doc.get("values") instanceof ObjectNode v)) throw SduiProblem.invalid("expected a value set document { schemaVersion, values }");
        v.put("screen", key);
        String templateKey = v.path("template").path("key").asText();
        int templateVersion = v.path("template").path("version").asInt();
        if (!SpecContract.isIdentifier(templateKey) || templateVersion < 1) throw SduiProblem.invalid("values.template must name a template release { key, version }");
        // the template's release — or its draft of that version: a page's content is drafted
        // with the template's design, before (or between) template releases
        JsonNode release = templates.releaseOrDraft(mid, templateKey, templateVersion);
        contract.validateValues(doc, release);
        // uploaded pictures (`image.name` = img_…) must be this merchant's
        assets.requireKnown(mid, AssetService.referenced(release, doc));
        byte[] bytes = Json.canonical(doc);

        List<Map<String, Object>> existing = jdbc.queryForList("SELECT lock_version, template_key FROM sdui_screen WHERE merchant_id = ? AND screen_key = ?", mid, key);
        if (existing.isEmpty()) {
            // a page has its own key; several pages may use one template (spec 2.6)
            // keys that differ only by letter case (landingPage / landingpage) are two pages to the
            // system but one to people, links and case-insensitive caches: refuse a new one
            List<String> clash = jdbc.queryForList("SELECT screen_key FROM sdui_screen WHERE merchant_id = ? AND LOWER(screen_key) = LOWER(?)", String.class, mid, key);
            if (!clash.isEmpty()) throw SduiProblem.conflict("page " + clash.get(0) + " already exists for " + mid + "; a key may not differ from it only by letter case");
            // a deprecated template starts no new page (pages that use it keep working)
            JsonNode status = templates.status(mid, templateKey);
            if (status.path("deprecated").asBoolean(false)) {
                String reason = status.path("deprecationReason").asText("");
                String replacement = status.path("replacedBy").asText("");
                throw SduiProblem.invalid("template " + templateKey + " is deprecated" + (reason.isEmpty() ? "" : " (" + reason + ")")
                    + "; no new page can use it" + (replacement.isEmpty() ? "" : " — use " + replacement));
            }
            jdbc.update("INSERT INTO sdui_screen (merchant_id, screen_key, template_key, template_version) VALUES (?, ?, ?, ?)", mid, key, templateKey, templateVersion);
        } else {
            // a page keeps its template (a newer release of it is fine)
            String currentTemplate = (String) existing.get(0).get("TEMPLATE_KEY");
            if (!templateKey.equals(currentTemplate)) throw SduiProblem.invalid("page " + key + " uses template " + currentTemplate + "; it cannot switch to " + templateKey);
            int current = (Integer) existing.get(0).get("LOCK_VERSION");
            if (lockVersion == null || lockVersion != current) throw SduiProblem.conflict("screen " + mid + "/" + key + " changed (lockVersion " + current + ")");
        }
        int revisionNo = jdbc.queryForObject("SELECT COALESCE(MAX(revision_no), 0) + 1 FROM sdui_value_revision WHERE merchant_id = ? AND screen_key = ?", Integer.class, mid, key);
        jdbc.update("INSERT INTO sdui_value_revision (merchant_id, screen_key, revision_no, payload, content_hash, created_by) VALUES (?, ?, ?, ?, ?, ?)",
            mid, key, revisionNo, new String(bytes, StandardCharsets.UTF_8), Json.sha256(bytes), actor.sub());
        long revisionId = jdbc.queryForObject("SELECT revision_id FROM sdui_value_revision WHERE merchant_id = ? AND screen_key = ? AND revision_no = ?", Long.class, mid, key, revisionNo);
        jdbc.update("UPDATE sdui_screen SET head_revision_id = ?, template_key = ?, template_version = ?, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE merchant_id = ? AND screen_key = ?",
            revisionId, templateKey, templateVersion, mid, key);
        audit.record(actor, mid, "VALUES_DRAFT_SAVED", "screen", key, Map.of("revision", revisionNo, "template", templateKey + "@" + templateVersion));
        return get(mid, key, actor);
    }

    /// Publish the draft now (no review step): the next release of the page, live at once.
    @Transactional
    public Map<String, Object> publish(String mid, String key, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        merchant(mid, actor);
        Map<String, Object> s = row(mid, key);
        Object head = s.get("headRevisionId");
        if (head == null) throw SduiProblem.conflict("screen " + key + " has no draft");
        Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_value_release WHERE revision_id = ?", Integer.class, head);
        if (done != null && done > 0) throw SduiProblem.conflict("screen " + key + " has no unpublished change");
        JsonNode payload = Json.parse(jdbc.queryForObject("SELECT payload FROM sdui_value_revision WHERE revision_id = ?", String.class, head));
        Map<String, Object> release = publish(mid, key, payload, ((Number) head).longValue(), actor.sub());
        audit.record(actor, mid, "VALUES_PUBLISHED", "screen", key, Map.of("sequence", release.get("sequence")));
        return get(mid, key, actor);
    }

    /// Publish the page at template release `templateVersion` (one "Terbitkan" for design and
    /// content, studio-bff): the content draft — or an empty one for a page never filled — is
    /// carried over to that release first. Content of components the release no longer has, or
    /// props that are no longer content, is dropped and listed in `dropped`.
    @Transactional
    public Map<String, Object> publishAt(String mid, String key, int templateVersion, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        merchant(mid, actor);
        if (!SpecContract.isIdentifier(key)) throw SduiProblem.badRequest("screen key must be an identifier");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT template_key, head_revision_id, lock_version FROM sdui_screen WHERE merchant_id = ? AND screen_key = ?", mid, key);
        String templateKey = rows.isEmpty() ? key : (String) rows.get(0).get("TEMPLATE_KEY");
        Object head = rows.isEmpty() ? null : rows.get(0).get("HEAD_REVISION_ID");
        JsonNode current = head == null ? null : Json.parse(jdbc.queryForObject("SELECT payload FROM sdui_value_revision WHERE revision_id = ?", String.class, head));
        JsonNode release = templates.release(mid, templateKey, templateVersion);
        ObjectNode doc;
        if (current instanceof ObjectNode c && c.get("values") instanceof ObjectNode) {
            doc = c.deepCopy();
        } else {
            doc = Json.object();
            doc.put("schemaVersion", SpecContract.SCHEMA_VERSION);
            ObjectNode v = doc.putObject("values");
            v.put("screen", key);
            v.putObject("components");
        }
        ObjectNode values = (ObjectNode) doc.get("values");
        ObjectNode ref = values.putObject("template");
        ref.put("key", templateKey);
        ref.put("version", templateVersion);
        List<String> dropped = rebase(values, release, doc.path("schemaVersion").asText(SpecContract.SCHEMA_VERSION));
        if (current == null || !Arrays.equals(Json.canonical(doc), Json.canonical(current))) {
            Integer lock = rows.isEmpty() ? null : (Integer) rows.get(0).get("LOCK_VERSION");
            saveDraft(mid, key, doc, lock, actor);
        }
        Map<String, Object> out = new LinkedHashMap<>(publish(mid, key, actor));
        out.put("dropped", dropped);
        return out;
    }

    /// Keep only what `release` takes from a value set: its component ids, and of each the props
    /// the document's version may set (2.6: any but the builder note, and the action; below: the
    /// content props). Answers what was dropped (`id` or `id.prop`).
    private List<String> rebase(ObjectNode values, JsonNode release, String schemaVersion) {
        Map<String, String> types = SpecContract.templateTypes(release);
        List<String> dropped = new ArrayList<>();
        if (!(values.get("components") instanceof ObjectNode components)) {
            values.putObject("components");
            return dropped;
        }
        List<String> ids = new ArrayList<>();
        components.fieldNames().forEachRemaining(ids::add);
        for (String id : ids) {
            String type = types.get(id);
            if (type == null) {
                components.remove(id);
                dropped.add(id);
                continue;
            }
            if (components.get(id).get("props") instanceof ObjectNode props) {
                List<String> keys = new ArrayList<>();
                props.fieldNames().forEachRemaining(keys::add);
                for (String prop : keys) {
                    if (!contract.isOverridableProp(type, prop, schemaVersion)) {
                        props.remove(prop);
                        dropped.add(id + "." + prop);
                    }
                }
            }
        }
        return dropped;
    }

    /// Re-validate, sign, write the release, then — under the merchant row lock — the next
    /// manifest: `sequence` is monotonic per merchant (HLD Target AD-7).
    public Map<String, Object> publish(String mid, String key, JsonNode payload, long revisionId, String publishedBy) {
        String templateKey = payload.path("values").path("template").path("key").asText();
        int templateVersion = payload.path("values").path("template").path("version").asInt();
        JsonNode release = templates.release(mid, templateKey, templateVersion);
        contract.validateValues(payload, release);
        Set<String> pictures = AssetService.referenced(release, payload);
        assets.requireKnown(mid, pictures);
        int sequence = jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) + 1 FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ?", Integer.class, mid, key);
        byte[] bytes = Json.canonical(payload);
        // spec 2.8: the release row signs the default language, each further language its own row (no `locales` in either)
        String language = payload.path("values").path("language").asText("");
        byte[] envelope = signer.envelope(Json.canonical(com.fajekrina.sduir.sdui.contract.SduiLocales.resolveValues(payload, language)), SpecContract.SCHEMA_VERSION);
        jdbc.update("""
            INSERT INTO sdui_value_release (merchant_id, screen_key, sequence, revision_id, template_key, template_version, payload, envelope, content_hash, key_id, published_by, language)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            mid, key, sequence, revisionId, templateKey, templateVersion, new String(bytes, StandardCharsets.UTF_8),
            new String(envelope, StandardCharsets.UTF_8), Json.sha256(bytes), signer.keyId(), publishedBy, language.isEmpty() ? null : language);
        for (String lang : com.fajekrina.sduir.sdui.contract.SduiLocales.languages(payload)) {
            byte[] inLanguage = Json.canonical(com.fajekrina.sduir.sdui.contract.SduiLocales.resolveValues(payload, lang));
            jdbc.update("INSERT INTO sdui_value_release_locale (merchant_id, screen_key, sequence, language, envelope, content_hash, key_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                mid, key, sequence, lang, new String(signer.envelope(inLanguage, SpecContract.SCHEMA_VERSION), StandardCharsets.UTF_8), Json.sha256(inLanguage), signer.keyId());
        }
        jdbc.update("UPDATE sdui_screen SET active_sequence = ?, status = 'PUBLISHED', lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP WHERE merchant_id = ? AND screen_key = ?",
            sequence, mid, key);
        // its pictures go public with it (delivery serves published assets only)
        assets.publish(mid, pictures);
        writeManifest(mid);
        return release(mid, key, sequence);
    }

    private void writeManifest(String mid) {
        long next = jdbc.queryForObject("SELECT manifest_sequence FROM sdui_merchant WHERE merchant_id = ? FOR UPDATE", Long.class, mid) + 1;
        jdbc.update("UPDATE sdui_merchant SET manifest_sequence = ? WHERE merchant_id = ?", next, mid);
        Map<String, String> screens = new TreeMap<>();
        jdbc.queryForList("SELECT screen_key, active_sequence FROM sdui_screen WHERE merchant_id = ? AND active_sequence IS NOT NULL", mid)
            .forEach((r) -> screens.put((String) r.get("SCREEN_KEY"), "v" + r.get("ACTIVE_SEQUENCE")));
        ObjectNode body = Json.object();
        body.put("sequence", next);
        body.set("screens", Json.MAPPER.valueToTree(screens));
        byte[] bytes = Json.canonical(body);
        byte[] envelope = signer.envelope(bytes, SpecContract.SCHEMA_VERSION);
        jdbc.update("INSERT INTO sdui_manifest (merchant_id, sequence, body, envelope, etag, key_id) VALUES (?, ?, ?, ?, ?, ?)",
            mid, next, new String(bytes, StandardCharsets.UTF_8), new String(envelope, StandardCharsets.UTF_8), Json.sha256(envelope), signer.keyId());
    }

    public Map<String, Object> manifest(String mid, SduiActor actor) {
        merchant(mid, actor);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT sequence AS \"sequence\", body AS \"body\" FROM sdui_manifest WHERE merchant_id = ? ORDER BY sequence DESC FETCH FIRST 1 ROWS ONLY", mid);
        if (rows.isEmpty()) throw SduiProblem.notFound("no manifest for " + mid);
        return Map.of("sequence", rows.get(0).get("sequence"), "body", Json.parse((String) rows.get(0).get("body")));
    }

    public int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM sdui_screen", Integer.class); }

    /// Every page (of every kanal the caller reaches) made from template `templateKey`: the
    /// Template tab's "used by N pages" and its offer to update them after a release.
    public List<Map<String, Object>> pagesOf(String templateKey, SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        if (!SpecContract.isIdentifier(templateKey)) throw SduiProblem.badRequest("template must be an identifier");
        return jdbc.queryForList("""
            SELECT s.merchant_id AS "kanal", s.screen_key AS "key", s.template_key AS "templateKey", s.template_version AS "templateVersion",
                   s.active_sequence AS "activeSequence", s.status AS "status",
                   CASE WHEN s.head_revision_id IS NULL OR r.sequence IS NOT NULL THEN FALSE ELSE TRUE END AS "unpublished"
            FROM sdui_screen s LEFT JOIN sdui_value_release r ON r.revision_id = s.head_revision_id
            WHERE s.template_key = ? ORDER BY s.merchant_id, s.screen_key""", templateKey)
            .stream().filter((p) -> actor.merchants().contains("*") || actor.merchants().contains((String) p.get("kanal"))).toList();
    }

    /// Every page of every kanal the caller reaches, with its template — the Konten tab's list.
    public List<Map<String, Object>> allPages(SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        return jdbc.queryForList("""
            SELECT s.merchant_id AS "kanal", s.screen_key AS "key", s.template_key AS "templateKey", s.template_version AS "templateVersion",
                   s.active_sequence AS "activeSequence", s.status AS "status", s.updated_at AS "updatedAt",
                   CASE WHEN s.head_revision_id IS NULL OR r.sequence IS NOT NULL THEN FALSE ELSE TRUE END AS "unpublished"
            FROM sdui_screen s LEFT JOIN sdui_value_release r ON r.revision_id = s.head_revision_id
            ORDER BY s.merchant_id, s.screen_key""")
            .stream().filter((p) -> actor.merchants().contains("*") || actor.merchants().contains((String) p.get("kanal"))).toList();
    }

    private Map<String, Object> release(String mid, String key, int sequence) {
        // the screen row names a release that is not there: say so (never a bare 500, and
        // never something a client could mistake for "no such page")
        Integer present = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ? AND sequence = ?", Integer.class, mid, key, sequence);
        if (present == null || present == 0) throw SduiProblem.conflict("screen " + mid + "/" + key + ": its active release v" + sequence + " is missing (inconsistent data)");
        Map<String, Object> out = new LinkedHashMap<>(jdbc.queryForMap("""
            SELECT screen_key AS "key", sequence AS "sequence", template_key AS "templateKey", template_version AS "templateVersion",
                   content_hash AS "contentHash", key_id AS "keyId", published_by AS "publishedBy", released_at AS "releasedAt", payload AS "payload"
            FROM sdui_value_release WHERE merchant_id = ? AND screen_key = ? AND sequence = ?""", mid, key, sequence));
        out.put("payload", Json.parse((String) out.get("payload")));
        return out;
    }

    private void merchant(String mid, SduiActor actor) { merchants.require(mid, actor); }

    private Map<String, Object> row(String mid, String key) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT screen_key AS "key", template_key AS "templateKey", template_version AS "templateVersion", status AS "status",
                   head_revision_id AS "headRevisionId", active_sequence AS "activeSequence", lock_version AS "lockVersion", updated_at AS "updatedAt"
            FROM sdui_screen WHERE merchant_id = ? AND screen_key = ?""", mid, key);
        if (rows.isEmpty()) throw SduiProblem.notFound("screen " + mid + "/" + key + " not found");
        return rows.get(0);
    }
}
