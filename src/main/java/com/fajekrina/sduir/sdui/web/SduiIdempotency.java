package com.fajekrina.sduir.sdui.web;

import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.core.SduiActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/// `Idempotency-Key` on POST/PUT/PATCH of an SDUI handler: the first answer for (actor, method,
/// path, key) is kept for `ttl` and replayed for a retry with the same body
/// (`Idempotency-Replayed: true`); the same key with another body is a `422`. Only 2xx answers
/// are kept, so a failed attempt can be retried. Applied by the handlers themselves, never as a
/// filter, so the service's other endpoints are left alone.
public class SduiIdempotency {
    public static final String HEADER = com.fajekrina.sduir.sdui.constant.SduiHeaders.IDEMPOTENCY_KEY;

    private final IdempotencyStore store;
    private final Duration ttl;
    private final Clock clock;

    public SduiIdempotency(IdempotencyStore store, Duration ttl, Clock clock) {
        this.store = store;
        this.ttl = ttl;
        this.clock = clock;
    }

    /// `work`'s answer, kept under the request's key — or the kept answer of an earlier attempt
    public SduiResponse run(HttpServletRequest request, Supplier<SduiResponse> work) {
        String key = request.getHeader(HEADER);
        String m = request.getMethod();
        if (key == null || !(m.equals("POST") || m.equals("PUT") || m.equals("PATCH"))) return work.get();
        if (key.length() > 128) throw SduiProblem.badRequest("Idempotency-Key longer than 128");
        Object actor = request.getAttribute(SduiActor.ATTRIBUTE);
        String scope = (actor instanceof SduiActor a ? a.sub() : request.getRemoteAddr()) + " " + m + " " + request.getRequestURI() + " " + key;
        String hash = Json.sha256(SduiRequests.body(request));
        Instant now = clock.instant();
        IdempotencyStore.Stored hit = store.get(scope, now);
        if (hit != null) {
            if (!hit.bodyHash().equals(hash)) throw SduiProblem.invalid("Idempotency-Key reused with a different body");
            SduiResponse replay = hit.contentType() == null ? SduiResponse.empty(hit.status())
                : SduiResponse.bytes(hit.status(), org.springframework.http.MediaType.parseMediaType(hit.contentType()), hit.body());
            return replay.header(com.fajekrina.sduir.sdui.constant.SduiHeaders.IDEMPOTENCY_REPLAYED, "true");
        }
        SduiResponse answer = work.get();
        if (answer.status() >= 200 && answer.status() < 300) {
            String type = answer.headers().getFirst(HttpHeaders.CONTENT_TYPE);
            store.put(scope, new IdempotencyStore.Stored(hash, answer.status(), type, answer.body(), now.plus(ttl)));
        }
        return answer;
    }
}
