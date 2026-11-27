package com.fajekrina.sduir.foundation.sdui.model.response;

import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import com.fajekrina.sduir.foundation.sdui.repository.IdempotencyStore;
import com.fajekrina.sduir.foundation.sdui.service.idempotency.SduiIdempotency;
import com.fajekrina.sduir.foundation.sdui.utils.Json;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.utils.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTest {
    @Test
    void storedBytesLeaveAsTheyAreAndARevalidationIsA304() {
        byte[] envelope = "{ \"payload\" :\"x\" }".getBytes(StandardCharsets.UTF_8);   // spacing a serializer would change
        String etag = Json.sha256(envelope);
        SduiResponse first = SduiResponse.stored(envelope, etag, null, CacheControl.noCache());
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.toEntity().getBody()).isEqualTo(envelope);
        assertThat(first.headers().getETag()).isEqualTo("\"" + etag + "\"");
        SduiResponse again = SduiResponse.stored(envelope, etag, "\"" + etag + "\"", CacheControl.noCache());
        assertThat(again.status()).isEqualTo(304);
        assertThat(again.body()).isNull();
    }

    @Test
    void errorsLeaveInTheContractFormatAndInternalOnesHideTheirMessage() {
        SduiErrorGuard errors = new SduiErrorGuard(() -> "t-1");
        SduiResponse missing = errors.guard(() -> { throw SduiException.notFound("screen x not found"); });
        assertThat(missing.status()).isEqualTo(404);
        assertThat(Json.parse(missing.text()).path("errorCode").asText()).isEqualTo("SDUI-404");
        assertThat(Json.parse(missing.text()).path("traceId").asText()).isEqualTo("t-1");
        SduiResponse broken = new SduiErrorGuard(TraceIds.NONE).guard(() -> { throw new IllegalStateException("db password wrong"); });
        assertThat(broken.status()).isEqualTo(500);
        assertThat(broken.text()).doesNotContain("password").doesNotContain("traceId");
    }

    @Test
    void anIdempotencyKeyReplaysTheFirstAnswerAndSkipsReads() {
        var idem = new SduiIdempotency(new IdempotencyStore.Memory(), Duration.ofMinutes(5), Clock.systemUTC());
        AtomicInteger runs = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            var put = new MockHttpServletRequest("PUT", "/merchant/templates/x/draft");
            put.addHeader(SduiIdempotency.HEADER, "k");
            put.setContent("{\"a\":1}".getBytes(StandardCharsets.UTF_8));
            SduiResponse r = idem.run(put, () -> SduiResponse.ok("{\"n\":" + runs.incrementAndGet() + "}"));
            assertThat(Json.parse(r.text()).path("n").asInt()).isEqualTo(1);
        }
        var get = new MockHttpServletRequest("GET", "/merchant/templates/x");
        get.addHeader(SduiIdempotency.HEADER, "k");
        idem.run(get, () -> SduiResponse.ok("{\"n\":" + runs.incrementAndGet() + "}"));
        assertThat(runs.get()).isEqualTo(2);
    }
}
