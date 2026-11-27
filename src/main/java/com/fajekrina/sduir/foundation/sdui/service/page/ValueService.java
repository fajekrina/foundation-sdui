package com.fajekrina.sduir.foundation.sdui.service.page;

import com.fajekrina.sduir.foundation.sdui.contract.constant.SduiSpec;
import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;
import com.fajekrina.sduir.foundation.sdui.service.audit.SduiAudit;
import com.fajekrina.sduir.foundation.sdui.contract.security.Signer;
import com.fajekrina.sduir.foundation.sdui.utils.Json;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SpecContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SduiLocales;
import com.fajekrina.sduir.foundation.sdui.repository.MerchantRepository;
import com.fajekrina.sduir.foundation.sdui.repository.ValueRepository;
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
///
/// The workflow only: the tables are {@link ValueRepository}'s, the document rules (validation,
/// rebase, languages, the envelope) the contract's.
@Service
public class ValueService {
    private final ValueRepository pages;
    private final MerchantRepository spaces;
    private final SpecContract contract;
    private final Signer signer;
    private final SduiAudit audit;
    private final TemplateSource templates;
    private final Merchants merchants;
    private final AssetService assets;

    public ValueService(ValueRepository pages, MerchantRepository spaces, SpecContract contract, Signer signer, SduiAudit audit, TemplateSource templates,
                        Merchants merchants, AssetService assets) {
        this.pages = pages;
        this.spaces = spaces;
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
        return spaces.listExcept(Merchants.SHARED).stream().filter((m) -> actor.merchants().contains("*") || actor.merchants().contains((String) m.get("merchantId"))).toList();
    }

    public List<Map<String, Object>> list(String mid, SduiActor actor) {
        merchant(mid, actor);
        return pages.list(mid);
    }

    public Map<String, Object> get(String mid, String key, SduiActor actor) {
        merchant(mid, actor);
        Map<String, Object> s = row(mid, key);
        Map<String, Object> out = new LinkedHashMap<>(s);
        Object head = s.get("headRevisionId");
        out.put("draft", head == null ? null : Json.parse(pages.revisionPayload(head)));
        Integer active = (Integer) s.get("activeSequence");
        out.put("active", active == null ? null : release(mid, key, active));
        out.put("releases", pages.releases(mid, key));
        return out;
    }

    /// Save a draft value set. The screen key in the path wins; the template release it
    /// names must be one of this merchant's and the values must fit it.
    @Transactional
    public Map<String, Object> saveDraft(String mid, String key, JsonNode payload, Integer lockVersion, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        merchant(mid, actor);
        if (!SpecContract.isIdentifier(key)) throw SduiException.badRequest("screen key must be an identifier");
        if (!(payload instanceof ObjectNode doc) || !(doc.get("values") instanceof ObjectNode v)) throw SduiException.invalid("expected a value set document { schemaVersion, values }");
        v.put("screen", key);
        String templateKey = v.path("template").path("key").asText();
        int templateVersion = v.path("template").path("version").asInt();
        if (!SpecContract.isIdentifier(templateKey) || templateVersion < 1) throw SduiException.invalid("values.template must name a template release { key, version }");
        // the template's release — or its draft of that version: a page's content is drafted
        // with the template's design, before (or between) template releases
        JsonNode release = templates.releaseOrDraft(mid, templateKey, templateVersion);
        contract.validateValues(doc, release);
        // uploaded pictures (`image.name` = img_…) must be this merchant's
        assets.requireKnown(mid, AssetService.referenced(release, doc));
        byte[] bytes = Json.canonical(doc);

        Map<String, Object> existing = pages.lockAndTemplate(mid, key);
        if (existing == null) {
            // a page has its own key; several pages may use one template (spec 2.6)
            // keys that differ only by letter case (landingPage / landingpage) are two pages to the
            // system but one to people, links and case-insensitive caches: refuse a new one
            List<String> clash = pages.keysIgnoringCase(mid, key);
            if (!clash.isEmpty()) throw SduiException.conflict("page " + clash.get(0) + " already exists for " + mid + "; a key may not differ from it only by letter case");
            // a deprecated template starts no new page (pages that use it keep working)
            JsonNode status = templates.status(mid, templateKey);
            if (status.path("deprecated").asBoolean(false)) {
                String reason = status.path("deprecationReason").asText("");
                String replacement = status.path("replacedBy").asText("");
                throw SduiException.invalid("template " + templateKey + " is deprecated" + (reason.isEmpty() ? "" : " (" + reason + ")")
                    + "; no new page can use it" + (replacement.isEmpty() ? "" : " — use " + replacement));
            }
            pages.insert(mid, key, templateKey, templateVersion);
        } else {
            // a page keeps its template (a newer release of it is fine)
            String currentTemplate = (String) existing.get("TEMPLATE_KEY");
            if (!templateKey.equals(currentTemplate)) throw SduiException.invalid("page " + key + " uses template " + currentTemplate + "; it cannot switch to " + templateKey);
            int current = (Integer) existing.get("LOCK_VERSION");
            if (lockVersion == null || lockVersion != current) throw SduiException.conflict("screen " + mid + "/" + key + " changed (lockVersion " + current + ")");
        }
        int revisionNo = pages.nextRevisionNo(mid, key);
        long revisionId = pages.appendRevision(mid, key, revisionNo, new String(bytes, StandardCharsets.UTF_8), Json.sha256(bytes), actor.sub());
        pages.setHead(mid, key, revisionId, templateKey, templateVersion);
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
        if (head == null) throw SduiException.conflict("screen " + key + " has no draft");
        if (pages.isReleased(head)) throw SduiException.conflict("screen " + key + " has no unpublished change");
        JsonNode payload = Json.parse(pages.revisionPayload(head));
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
        if (!SpecContract.isIdentifier(key)) throw SduiException.badRequest("screen key must be an identifier");
        Map<String, Object> row = pages.head(mid, key);
        String templateKey = row == null ? key : (String) row.get("TEMPLATE_KEY");
        Object head = row == null ? null : row.get("HEAD_REVISION_ID");
        JsonNode current = head == null ? null : Json.parse(pages.revisionPayload(head));
        JsonNode release = templates.release(mid, templateKey, templateVersion);
        ObjectNode doc;
        if (current instanceof ObjectNode c && c.get("values") instanceof ObjectNode) {
            doc = c.deepCopy();
        } else {
            doc = Json.object();
            doc.put("schemaVersion", SduiSpec.SCHEMA_VERSION);
            ObjectNode v = doc.putObject("values");
            v.put("screen", key);
            v.putObject("components");
        }
        ObjectNode values = (ObjectNode) doc.get("values");
        ObjectNode ref = values.putObject("template");
        ref.put("key", templateKey);
        ref.put("version", templateVersion);
        List<String> dropped = contract.rebase(values, release, doc.path("schemaVersion").asText(SduiSpec.SCHEMA_VERSION));
        if (current == null || !Arrays.equals(Json.canonical(doc), Json.canonical(current))) {
            Integer lock = row == null ? null : (Integer) row.get("LOCK_VERSION");
            saveDraft(mid, key, doc, lock, actor);
        }
        Map<String, Object> out = new LinkedHashMap<>(publish(mid, key, actor));
        out.put("dropped", dropped);
        return out;
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
        int sequence = pages.nextSequence(mid, key);
        byte[] bytes = Json.canonical(payload);
        // spec 2.8: the release row signs the default language, each further language its own row (no `locales` in either)
        String language = payload.path("values").path("language").asText("");
        byte[] envelope = signer.envelope(Json.canonical(SduiLocales.resolveValues(payload, language)), SduiSpec.SCHEMA_VERSION);
        pages.insertRelease(mid, key, sequence, revisionId, templateKey, templateVersion, new String(bytes, StandardCharsets.UTF_8),
            new String(envelope, StandardCharsets.UTF_8), Json.sha256(bytes), signer.keyId(), publishedBy, language.isEmpty() ? null : language);
        for (String lang : SduiLocales.languages(payload)) {
            byte[] inLanguage = Json.canonical(SduiLocales.resolveValues(payload, lang));
            pages.insertReleaseLocale(mid, key, sequence, lang, new String(signer.envelope(inLanguage, SduiSpec.SCHEMA_VERSION), StandardCharsets.UTF_8), Json.sha256(inLanguage), signer.keyId());
        }
        pages.activate(mid, key, sequence);
        // its pictures go public with it (delivery serves published assets only)
        assets.publish(mid, pictures);
        writeManifest(mid);
        return release(mid, key, sequence);
    }

    private void writeManifest(String mid) {
        long next = spaces.nextManifestSequence(mid);
        Map<String, String> screens = new TreeMap<>();
        pages.activeSequences(mid).forEach((screen, sequence) -> screens.put(screen, "v" + sequence));
        ObjectNode body = Json.object();
        body.put("sequence", next);
        body.set("screens", Json.MAPPER.valueToTree(screens));
        byte[] bytes = Json.canonical(body);
        byte[] envelope = signer.envelope(bytes, SduiSpec.SCHEMA_VERSION);
        pages.insertManifest(mid, next, new String(bytes, StandardCharsets.UTF_8), new String(envelope, StandardCharsets.UTF_8), Json.sha256(envelope), signer.keyId());
    }

    public Map<String, Object> manifest(String mid, SduiActor actor) {
        merchant(mid, actor);
        Map<String, Object> latest = pages.latestManifest(mid);
        if (latest == null) throw SduiException.notFound("no manifest for " + mid);
        return Map.of("sequence", latest.get("sequence"), "body", Json.parse((String) latest.get("body")));
    }

    public int count() { return pages.count(); }

    /// Every page (of every kanal the caller reaches) made from template `templateKey`: the
    /// Template tab's "used by N pages" and its offer to update them after a release.
    public List<Map<String, Object>> pagesOf(String templateKey, SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        if (!SpecContract.isIdentifier(templateKey)) throw SduiException.badRequest("template must be an identifier");
        return pages.pagesOf(templateKey).stream().filter((p) -> actor.merchants().contains("*") || actor.merchants().contains((String) p.get("kanal"))).toList();
    }

    /// Every page of every kanal the caller reaches, with its template — the Konten tab's list.
    public List<Map<String, Object>> allPages(SduiActor actor) {
        actor.require(SduiActor.ADMIN_OR_SERVICE);
        return pages.allPages().stream().filter((p) -> actor.merchants().contains("*") || actor.merchants().contains((String) p.get("kanal"))).toList();
    }

    private Map<String, Object> release(String mid, String key, int sequence) {
        // the screen row names a release that is not there: say so (never a bare 500, and
        // never something a client could mistake for "no such page")
        Map<String, Object> out = pages.release(mid, key, sequence);
        if (out == null) throw SduiException.conflict("screen " + mid + "/" + key + ": its active release v" + sequence + " is missing (inconsistent data)");
        out.put("payload", Json.parse((String) out.get("payload")));
        return out;
    }

    private void merchant(String mid, SduiActor actor) { merchants.require(mid, actor); }

    private Map<String, Object> row(String mid, String key) {
        Map<String, Object> row = pages.find(mid, key);
        if (row == null) throw SduiException.notFound("screen " + mid + "/" + key + " not found");
        return row;
    }
}
