package com.fajekrina.sduir.foundation.sdui.service.template;

import com.fajekrina.sduir.foundation.sdui.contract.constant.SduiSpec;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.model.ClientInfo;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SduiLocales;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SpecContract;
import com.fajekrina.sduir.foundation.sdui.contract.security.Signer;
import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;
import com.fajekrina.sduir.foundation.sdui.repository.TemplateRepository;
import com.fajekrina.sduir.foundation.sdui.service.audit.SduiAudit;
import com.fajekrina.sduir.foundation.sdui.utils.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
///
/// The workflow only: the tables are {@link TemplateRepository}'s, the document rules (validation,
/// languages, the envelope) the contract's.
@Service
public class TemplateService {
    /// the owner of every template made in the Studio (shared by all users)
    public static final String SHARED = "sdui";

    private final TemplateRepository templates;
    private final SpecContract contract;
    private final Signer signer;
    private final SduiAudit audit;

    public TemplateService(TemplateRepository templates, SpecContract contract, Signer signer, SduiAudit audit) {
        this.templates = templates;
        this.contract = contract;
        this.signer = signer;
        this.audit = audit;
    }

    public List<Map<String, Object>> list(String owner) { return templates.list(owner); }

    /// The owner whose `key` a kanal reads: its own (an older, kanal-owned template), else the shared one.
    public String ownerFor(String kanal, String key) {
        if (kanal != null && !kanal.equals(SHARED) && templates.exists(kanal, key)) return kanal;
        return SHARED;
    }

    public Map<String, Object> get(String owner, String key) {
        Map<String, Object> t = row(owner, key);
        Map<String, Object> out = new LinkedHashMap<>(t);
        Object head = t.get("headRevisionId");
        out.put("draft", head == null ? null : Json.parse(templates.revisionPayload(head)));
        Integer active = (Integer) t.get("activeVersion");
        out.put("active", active == null ? null : release(owner, key, active));
        out.put("releases", templates.releases(owner, key));
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
        if (!SpecContract.isIdentifier(key)) throw SduiException.badRequest("template key must be an identifier");
        Map<String, Object> existing = templates.lockAndActive(owner, key);
        int next = existing == null || existing.get("ACTIVE_VERSION") == null ? 1 : (Integer) existing.get("ACTIVE_VERSION") + 1;
        if (!(payload instanceof ObjectNode doc) || !(doc.get("template") instanceof ObjectNode t)) throw SduiException.invalid("expected a template document { schemaVersion, template }");
        t.put("key", key);
        t.put("version", next);
        contract.validateTemplate(doc);
        byte[] bytes = Json.canonical(doc);
        String title = t.path("title").isTextual() ? t.path("title").asText() : null;
        if (existing == null) {
            // keys that differ only by letter case are one template to people (and to the pages named after it)
            // …and a new shared key is unique over every owner (a kanal reads its own key, then the shared one)
            List<String> clash = owner.equals(SHARED) ? templates.keysIgnoringCase(key) : templates.keysIgnoringCase(owner, SHARED, key);
            if (!clash.isEmpty()) throw SduiException.conflict("template " + clash.get(0) + " already exists; a key may not differ from it only by letter case");
            templates.insert(owner, key, title);
        } else {
            int current = (Integer) existing.get("LOCK_VERSION");
            if (lockVersion == null || lockVersion != current) throw SduiException.conflict("template " + key + " changed (lockVersion " + current + ")");
        }
        int revisionNo = templates.nextRevisionNo(owner, key);
        long revisionId = templates.appendRevision(owner, key, revisionNo, new String(bytes, StandardCharsets.UTF_8), Json.sha256(bytes), actor.sub());
        templates.setHead(owner, key, revisionId, title);
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
        if (head == null) throw SduiException.conflict("template " + key + " has no draft");
        JsonNode payload = Json.parse(templates.revisionPayload(head));
        Map<String, Object> release = publish(owner, key, payload, ((Number) head).longValue(), actor.sub());
        audit.record(actor, owner, "TEMPLATE_PUBLISHED", "template", key, Map.of("version", release.get("version")));
        return get(owner, key);
    }

    /// Freeze the bytes, sign them, write the immutable release, point the template at it.
    public Map<String, Object> publish(String owner, String key, JsonNode payload, long revisionId, String publishedBy) {
        contract.validateTemplate(payload);
        int version = payload.path("template").path("version").asInt();
        if (templates.releaseExists(owner, key, version)) throw SduiException.conflict("template " + key + "@" + version + " is already published; save a change first");
        byte[] bytes = Json.canonical(payload);
        // spec 2.8: the release row signs the default language, each further language its own row (no `locales` in either)
        String language = payload.path("template").path("language").asText("");
        byte[] envelope = signer.envelope(Json.canonical(SduiLocales.resolveTemplate(payload, language)), SduiSpec.SCHEMA_VERSION);
        String title = payload.path("template").path("title").isTextual() ? payload.path("template").path("title").asText() : null;
        templates.insertRelease(owner, key, version, revisionId, title, new String(bytes, StandardCharsets.UTF_8), new String(envelope, StandardCharsets.UTF_8),
            Json.sha256(bytes), signer.keyId(), publishedBy, language.isEmpty() ? null : language);
        for (String lang : SduiLocales.languages(payload)) {
            byte[] inLanguage = Json.canonical(SduiLocales.resolveTemplate(payload, lang));
            templates.insertReleaseLocale(owner, key, version, lang, new String(signer.envelope(inLanguage, SduiSpec.SCHEMA_VERSION), StandardCharsets.UTF_8),
                Json.sha256(inLanguage), signer.keyId());
        }
        templates.activate(owner, key, version);
        return release(owner, key, version);
    }

    /// Released templates a kanal may use (the Konten tab's template picker): the shared ones and
    /// the kanal's own older ones; the latest of each key, or every version — deprecated ones
    /// included and marked (`deprecated`, `deprecationReason`, `replacedBy`): a page that uses one
    /// still needs it; a new page may not pick it. `shared: true` marks a shared template.
    public List<Map<String, Object>> catalog(String owner, boolean all) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Boolean> own = new HashMap<>();
        if (!owner.equals(SHARED)) for (Map<String, Object> r : templates.catalog(owner, all)) { own.put((String) r.get("key"), true); r.put("shared", false); out.add(r); }
        for (Map<String, Object> r : templates.catalog(SHARED, all)) if (!own.containsKey((String) r.get("key"))) { r.put("shared", true); out.add(r); }
        out.sort(Comparator.comparing((Map<String, Object> r) -> (String) r.get("key")));
        return out;
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
        if (reason == null || reason.isBlank()) throw SduiException.invalid("a deprecation needs a reason");
        if (reason.length() > 4096) throw SduiException.invalid("reason: at most 4096 characters");
        if (t.get("activeVersion") == null) throw SduiException.conflict("template " + key + " has no release; delete the draft instead of deprecating it");
        String replacement = replacedBy == null || replacedBy.isBlank() ? null : replacedBy.trim();
        if (replacement != null) {
            if (replacement.equals(key)) throw SduiException.invalid("a template cannot replace itself");
            Map<String, Object> r = templates.activeAndDeprecation(owner, replacement);
            if (r == null || r.get("ACTIVE_VERSION") == null) throw SduiException.invalid("replacement " + replacement + " is not a released template");
            if (r.get("DEPRECATED_AT") != null) throw SduiException.invalid("replacement " + replacement + " is deprecated itself");
        }
        templates.deprecate(owner, key, actor.sub(), reason.trim(), replacement);
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
        if (!Boolean.TRUE.equals(t.get("deprecated"))) throw SduiException.conflict("template " + key + " is not deprecated");
        templates.restore(owner, key);
        audit.record(actor, owner, "TEMPLATE_RESTORED", "template", key, Map.of("reason", String.valueOf(t.get("deprecationReason"))));
        return get(owner, key);
    }

    /// A released template document (plain; delivery serves the envelope).
    public String releasedPayload(String kanal, String key, int version) {
        String payload = templates.releasedPayload(ownerFor(kanal, key), key, version);
        if (payload == null) throw SduiException.notFound("template " + key + "@" + version + " not released");
        return payload;
    }

    /// The head revision's document (the draft; after a publish, the released one).
    public String draftPayload(String kanal, String key) {
        String owner = ownerFor(kanal, key);
        Object head = row(owner, key).get("headRevisionId");
        if (head == null) throw SduiException.notFound("template " + key + " has no draft");
        return templates.revisionPayload(head);
    }

    /// Which app versions carry shared template release `key@version` (native apps build every
    /// template themselves): `[{ platform, minimumClientVersion, recordedBy, recordedAt }]`.
    public List<Map<String, Object>> support(String key, int version) {
        releaseExists(key, version);
        return templates.support(SHARED, key, version);
    }

    /// Replace the platforms that carry release `key@version`: `[{ platform, minimumClientVersion }]`
    /// (`android`, `ios`, `mpaas`; numeric dotted versions). A platform left out carries it in no
    /// version: delivery gives its apps the page's fallback instead. The Studio admin or a service
    /// (an app's release pipeline) records it.
    @Transactional
    public List<Map<String, Object>> setSupport(String key, int version, JsonNode rows, SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        releaseExists(key, version);
        if (rows == null || !rows.isArray()) throw SduiException.invalid("expected [{ platform, minimumClientVersion }]");
        Map<String, String> wanted = new LinkedHashMap<>();
        for (JsonNode r : rows) {
            String platform = r.path("platform").asText("");
            String minimum = r.path("minimumClientVersion").asText("");
            if (!ClientInfo.NATIVE.contains(platform)) throw SduiException.invalid("platform must be one of android, ios, mpaas: " + platform);
            if (!ClientInfo.isVersion(minimum)) throw SduiException.invalid("minimumClientVersion must be a version like 5.4.0: " + minimum);
            if (wanted.put(platform, minimum) != null) throw SduiException.invalid("platform " + platform + " given twice");
        }
        templates.replaceSupport(SHARED, key, version, wanted, actor.sub());
        audit.record(actor, SHARED, "TEMPLATE_SUPPORT_SET", "template", key + "@" + version, wanted);
        return support(key, version);
    }

    private void releaseExists(String key, int version) {
        if (!templates.releaseExists(SHARED, key, version)) throw SduiException.notFound("template " + key + "@" + version + " not released");
    }

    public int count() { return templates.count(); }

    /// How many templates are owned by a kanal (not shared): the demo seed runs when there are none.
    public int countOwned() { return templates.countOwnedByOthersThan(SHARED); }

    /// Whether a shared template `key` exists (the starter seeder).
    public boolean hasShared(String key) { return !templates.keysIgnoringCase(key).isEmpty(); }

    /// Mark older kanal-owned templates `keys` deprecated in favour of a shared one (the starter
    /// seeder; once — a template already deprecated is left as it is). Answers how many changed.
    public int deprecateLegacy(List<String> keys, String replacedBy, String reason, SduiActor actor) {
        int changed = 0;
        for (String key : keys) {
            for (String owner : templates.releasedOwnersExcept(key, SHARED)) {
                // replaced_by names a template of the same owner in Studio deprecations; a shared one is fine here
                templates.deprecate(owner, key, actor.sub(), reason, replacedBy);
                audit.record(actor, owner, "TEMPLATE_DEPRECATED", "template", key, Map.of("reason", reason, "replacedBy", replacedBy));
                changed++;
            }
        }
        return changed;
    }

    private Map<String, Object> release(String owner, String key, int version) {
        Map<String, Object> out = templates.release(owner, key, version);
        if (out == null) throw SduiException.notFound("template " + key + "@" + version + " not released");
        out.put("payload", Json.parse((String) out.get("payload")));
        return out;
    }

    private Map<String, Object> row(String owner, String key) {
        Map<String, Object> row = templates.find(owner, key);
        if (row == null) throw SduiException.notFound("template " + key + " not found");
        return row;
    }
}
