package com.fajekrina.sduir.sdui.authoring.page;

import com.fajekrina.sduir.sdui.core.SduiActor;
import com.fajekrina.sduir.sdui.core.SduiAudit;
import com.fajekrina.sduir.sdui.web.Json;
import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fajekrina.sduir.sdui.properties.SduiProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// Pictures for image components. An admin uploads a file (JSON `{ filename, data: <base64> }`)
/// or names an https link the service copies ONCE ({@link UrlFetcher}); either way the value
/// set only holds the asset id (`image.name` = `img_<32 hex>`), so a page never loads from a
/// payload URL. Only PNG, JPEG, WebP and GIF (checked by their bytes, not the declared type;
/// no SVG). Assets are immutable and per merchant; the first published value release that uses
/// one publishes it, and delivery serves published assets only.
@Service
public class AssetService {
    /// the name a value set uses for an uploaded picture
    public static final Pattern ID = Pattern.compile("^img_[0-9a-f]{32}$");
    private final JdbcTemplate jdbc;
    private final Merchants merchants;
    private final SduiAudit audit;
    private final UrlFetcher fetcher;
    private final int maxBytes;

    public AssetService(JdbcTemplate jdbc, Merchants merchants, SduiAudit audit, UrlFetcher fetcher,
                        SduiProperties properties) {
        this.jdbc = jdbc;
        this.merchants = merchants;
        this.audit = audit;
        this.fetcher = fetcher;
        this.maxBytes = properties.getAssets().getMaxBytes();
    }

    /// Store a picture: body `{ "filename": "…", "data": "<base64>" }` or `{ "url": "https://…" }`.
    /// The same bytes again answer the same asset (no duplicate).
    @Transactional
    public Map<String, Object> create(String mid, JsonNode body, SduiActor actor) {
        actor.require(SduiActor.TEMPLATE_ADMIN);
        merchants.require(mid, actor);
        String url = text(body, "url");
        byte[] bytes;
        String source;
        String name;
        if (url != null) {
            bytes = fetcher.fetch(url, maxBytes);
            source = "URL";
            name = url.replaceAll("[?#].*$", "").replaceAll("^.*/", "");
        } else {
            String data = text(body, "data");
            if (data == null) throw SduiProblem.invalid("send { filename, data: <base64> } or { url }");
            try {
                bytes = Base64.getDecoder().decode(data.replaceFirst("^data:[^,]*,", ""));
            } catch (IllegalArgumentException e) {
                throw SduiProblem.invalid("data is not base64");
            }
            source = "UPLOAD";
            name = text(body, "filename");
            url = null;
        }
        if (bytes.length == 0) throw SduiProblem.invalid("the picture is empty");
        if (bytes.length > maxBytes) throw SduiProblem.invalid("the picture is " + bytes.length / 1024 + " KB; the limit is " + maxBytes / 1024 + " KB");
        String type = sniff(bytes);
        if (type == null) throw SduiProblem.invalid("not a PNG, JPEG, WebP or GIF picture");
        String hash = Json.sha256(bytes);
        String id = "img_" + Json.sha256((mid + "\n" + hash).getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_asset WHERE asset_id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            String original = name == null ? null : name.replaceAll("[^A-Za-z0-9._ -]", "_");
            if (original != null && original.length() > 256) original = original.substring(0, 256);
            jdbc.update("""
                INSERT INTO sdui_asset (asset_id, merchant_id, content_type, byte_size, content, content_hash, source, source_url, original_name, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, mid, type, bytes.length, bytes, hash, source, url, original == null || original.isBlank() ? null : original, actor.sub());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("source", source);
            detail.put("bytes", bytes.length);
            detail.put("type", type);
            if (url != null) detail.put("url", url);
            audit.record(actor, mid, "ASSET_CREATED", "asset", id, detail);
        }
        return meta(mid, id);
    }

    public List<Map<String, Object>> list(String mid, SduiActor actor) {
        merchants.require(mid, actor);
        return jdbc.queryForList(SELECT_META + " WHERE merchant_id = ? ORDER BY created_at DESC, asset_id", mid);
    }

    /// The bytes of one asset of this merchant (the Studio preview; drafts included).
    public Content content(String mid, String id, SduiActor actor) {
        merchants.require(mid, actor);
        if (!ID.matcher(id).matches()) throw SduiProblem.badRequest("not an asset id: " + id);
        // a kanal's preview also shows the shared templates' pictures
        var rows = jdbc.queryForList("SELECT content_type, content FROM sdui_asset WHERE merchant_id IN (?, ?) AND asset_id = ?", mid, Merchants.SHARED, id);
        if (rows.isEmpty()) throw SduiProblem.notFound("asset " + id + " not found");
        return new Content((String) rows.get(0).get("CONTENT_TYPE"), (byte[]) rows.get(0).get("CONTENT"));
    }

    public record Content(String type, byte[] bytes) {}

    /// The asset ids the page shows in the template's image components (`image.name`): the value
    /// set's picture, else the template's default picture (spec 2.2: a template may carry one).
    public static Set<String> referenced(JsonNode template, JsonNode values) {
        Map<String, String> images = new LinkedHashMap<>();   // image id → the template's default name
        JsonNode t = template.path("template");
        collectImages(t.path("root"), images);
        t.path("variants").forEach((v) -> collectImages(v, images));
        JsonNode components = values.path("values").path("components");
        Set<String> out = new LinkedHashSet<>();
        images.forEach((id, fallback) -> {
            JsonNode own = components.path(id).path("props").path("name");
            String name = own.isTextual() ? own.asText() : fallback;
            if (ID.matcher(name).matches()) out.add(name);
        });
        return out;
    }

    private static void collectImages(JsonNode node, Map<String, String> out) {
        if ("image".equals(node.path("type").asText())) out.put(node.path("id").asText(), node.path("props").path("name").asText(""));
        node.path("children").forEach((c) -> collectImages(c, out));
    }

    /// Every uploaded picture a value set names belongs to this merchant (else 422).
    public void requireKnown(String mid, Set<String> ids) {
        List<String> missing = new ArrayList<>();
        for (String id : ids) {
            // the kanal's own pictures, or the shared templates' (kanal `sdui`)
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sdui_asset WHERE merchant_id IN (?, ?) AND asset_id = ?", Integer.class, mid, Merchants.SHARED, id);
            if (n == null || n == 0) missing.add(id);
        }
        if (!missing.isEmpty()) throw SduiProblem.invalid("unknown picture(s) for " + mid + ": " + String.join(", ", missing) + " — upload them first");
    }

    /// The pictures of a published release become public (delivery serves them).
    public void publish(String mid, Set<String> ids) {
        for (String id : ids) jdbc.update("UPDATE sdui_asset SET published_at = CURRENT_TIMESTAMP WHERE merchant_id IN (?, ?) AND asset_id = ? AND published_at IS NULL", mid, Merchants.SHARED, id);
    }

    private Map<String, Object> meta(String mid, String id) {
        return jdbc.queryForMap(SELECT_META + " WHERE merchant_id = ? AND asset_id = ?", mid, id);
    }

    private static final String SELECT_META = """
        SELECT asset_id AS "name", content_type AS "contentType", byte_size AS "size", source AS "source", source_url AS "sourceUrl",
               original_name AS "originalName", created_by AS "createdBy", created_at AS "createdAt", published_at AS "publishedAt"
        FROM sdui_asset""";

    /// The picture type from its first bytes, or null.
    static String sniff(byte[] b) {
        if (starts(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (starts(b, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (starts(b, 'G', 'I', 'F', '8') && b.length > 5 && (b[4] == '7' || b[4] == '9') && b[5] == 'a') return "image/gif";
        if (starts(b, 'R', 'I', 'F', 'F') && b.length >= 12 && Arrays.equals(Arrays.copyOfRange(b, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII))) return "image/webp";
        return null;
    }

    private static boolean starts(byte[] b, int... prefix) {
        if (b.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if ((b[i] & 0xFF) != prefix[i]) return false;
        return true;
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body == null ? null : body.path(field);
        return n != null && n.isTextual() && !n.asText().isBlank() ? n.asText().trim() : null;
    }
}
