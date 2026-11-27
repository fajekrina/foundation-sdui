package com.fajekrina.sduir.foundation.sdui.service.delivery.impl;

import com.fajekrina.sduir.foundation.sdui.contract.constant.SduiHeaders;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.model.ClientInfo;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SduiLocales;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SduiReleases;
import com.fajekrina.sduir.foundation.sdui.contract.rule.SpecContract;
import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import com.fajekrina.sduir.foundation.sdui.repository.DeliveryRepository;
import com.fajekrina.sduir.foundation.sdui.security.SigningKeys;
import com.fajekrina.sduir.foundation.sdui.service.delivery.SduiDelivery;
import com.fajekrina.sduir.foundation.sdui.utils.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/// The built-in {@link SduiDelivery} (see there for what each handler answers). It reads through
/// {@link DeliveryRepository}; which release an app gets is the contract's ({@link SduiReleases}),
/// and so is its language ({@link SduiLocales#pick}).
public class SduiDeliveryImpl implements SduiDelivery {
    /// the owner of the shared templates
    private static final String SHARED = "sdui";
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
    private static final CacheControl REVALIDATE = CacheControl.noCache().cachePublic();
    private static final String IF_NONE_MATCH = "If-None-Match";
    private final DeliveryRepository releases;
    private final SigningKeys keys;
    private final SduiErrorGuard errors;
    private final SduiReleases.Fallback fallback;
    private final String defaultLanguage;

    /// `fallback`: `no-content` | `previous-release` ({@link SduiReleases.Fallback});
    /// `defaultLanguage`: the language of a release that names none (spec 2.8, `sdui.language.default`)
    public SduiDeliveryImpl(DeliveryRepository releases, SigningKeys keys, SduiErrorGuard errors, String fallback, String defaultLanguage) {
        this.releases = releases;
        this.keys = keys;
        this.errors = errors;
        this.fallback = SduiReleases.Fallback.parse(fallback);
        this.defaultLanguage = defaultLanguage;
    }

    @Override
    public SduiResponse template(String space, String key, int version, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space, key);
            Map<String, Object> row = releases.templateRelease(space, SHARED, key, version);
            if (row == null) throw SduiException.notFound("template " + space + "/" + key + "@" + version + " not found");
            Localized in = localized((String) row.get("ENVELOPE"), row.get("LANGUAGE"), request, () -> releases.templateLocales(row.get("OWNER"), key, version));
            return perLanguage(serve(in.envelope(), request, IMMUTABLE), in);
        });
    }

    @Override
    public SduiResponse spaces(HttpServletRequest request) {
        return errors.guard(() -> SduiResponse.ok(releases.liveSpaces()).cache(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic()));
    }

    @Override
    public SduiResponse screen(String space, String key, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space, key);
            ClientInfo client = ClientInfo.parse(request.getHeader(SduiHeaders.CLIENT));
            Map<String, Object> live = releases.liveRelease(space, key);
            if (live == null) throw SduiException.notFound("screen " + space + "/" + key + " not found");
            Optional<Map<String, Object>> chosen = SduiReleases.choose(client, live,
                () -> releases.releasesBefore(space, key, live.get("SEQUENCE")),
                (r) -> releases.minimumClientVersion(space, SHARED, r.get("TEMPLATE_KEY"), r.get("TEMPLATE_VERSION"), client.platform()),
                fallback);
            return perClient(chosen.map((r) -> page(space, key, r, request, REVALIDATE)).orElseGet(() -> SduiResponse.empty(204).cache(REVALIDATE)));
        });
    }

    @Override
    public SduiResponse screenRelease(String space, String key, int sequence, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space, key);
            Map<String, Object> row = releases.release(space, key, sequence);
            if (row == null) throw SduiException.notFound("screen " + space + "/" + key + "/v" + sequence + " not found");
            return page(space, key, row, request, IMMUTABLE);
        });
    }

    @Override
    public SduiResponse manifest(String space, HttpServletRequest request) {
        return errors.guard(() -> {
            ids(space);
            String envelope = releases.latestManifest(space);
            if (envelope == null) throw SduiException.notFound("manifest " + space + " not found");
            return serve(envelope, request, REVALIDATE);
        });
    }

    @Override
    public SduiResponse asset(String id, HttpServletRequest request) {
        return errors.guard(() -> {
            if (!id.matches("^img_[0-9a-f]{32}$")) throw SduiException.badRequest("not an asset id: " + id);
            Map<String, Object> row = releases.asset(id);
            if (row == null) throw SduiException.notFound("asset " + id + " not found");
            String etag = "\"" + row.get("CONTENT_HASH") + "\"";
            SduiResponse out = SduiResponse.matches(request.getHeader(IF_NONE_MATCH), etag) ? SduiResponse.empty(304)
                : SduiResponse.bytes(200, MediaType.parseMediaType((String) row.get("CONTENT_TYPE")), (byte[]) row.get("CONTENT"));
            return out.header("ETag", etag).cache(IMMUTABLE)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox");
        });
    }

    @Override
    public SduiResponse signingKeys(HttpServletRequest request) {
        return errors.guard(() -> {
            List<SigningKeys.Key> list = keys.keys();
            if (list.isEmpty()) throw SduiException.notFound("no signing key yet");
            return SduiResponse.ok(Map.of("keys", list.stream().map((k) -> Map.of("keyId", k.keyId(), "alg", "ed25519", "publicKey", k.publicKey(), "status", k.status())).toList()));
        });
    }

    private static SduiResponse serve(String envelope, HttpServletRequest request, CacheControl cache) {
        byte[] bytes = envelope.getBytes(StandardCharsets.UTF_8);
        return SduiResponse.stored(bytes, Json.sha256(bytes), request.getHeader(IF_NONE_MATCH), cache);
    }

    /// a stored envelope in one language (spec 2.8)
    private record Localized(String envelope, String language) {}

    /// A release in the language the app asks for (`Accept-Language`): one of its further
    /// languages (`further` → language, envelope), else its own (`ownLanguage`, or the default).
    private Localized localized(String envelope, Object ownLanguage, HttpServletRequest request, Supplier<List<Map<String, Object>>> further) {
        String own = ownLanguage == null ? defaultLanguage : (String) ownLanguage;
        String wanted = request.getHeader(SduiHeaders.ACCEPT_LANGUAGE);
        if (wanted == null || wanted.isBlank()) return new Localized(envelope, own);
        List<Map<String, Object>> rows = further.get();
        if (rows.isEmpty()) return new Localized(envelope, own);
        List<String> available = new ArrayList<>(List.of(own));
        rows.forEach((r) -> available.add((String) r.get("LANGUAGE")));
        String chosen = SduiLocales.pick(wanted, available, own);
        for (Map<String, Object> r : rows) if (r.get("LANGUAGE").equals(chosen)) return new Localized((String) r.get("ENVELOPE"), chosen);
        return new Localized(envelope, own);
    }

    /// one page release in the asking app's language
    private SduiResponse page(String space, String key, Map<String, Object> release, HttpServletRequest request, CacheControl cache) {
        Localized in = localized((String) release.get("ENVELOPE"), release.get("LANGUAGE"), request, () -> releases.pageLocales(space, key, release.get("SEQUENCE")));
        return perLanguage(serve(in.envelope(), request, cache), in);
    }

    /// the answer depends on the asked language: caches keep one per `Accept-Language`
    private static SduiResponse perLanguage(SduiResponse out, Localized in) {
        return out.header(SduiHeaders.CONTENT_LANGUAGE, in.language()).header("Vary", SduiHeaders.ACCEPT_LANGUAGE);
    }

    /// the answer depends on the asking app: caches keep one per `X-Sdui-Client`
    private static SduiResponse perClient(SduiResponse out) { return out.header("Vary", SduiHeaders.CLIENT); }

    private static void ids(String... values) {
        for (String v : values) if (!SpecContract.isIdentifier(v)) throw SduiException.badRequest("not an identifier: " + v);
    }
}
