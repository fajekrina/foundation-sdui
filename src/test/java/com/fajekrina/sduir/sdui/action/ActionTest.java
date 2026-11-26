package com.fajekrina.sduir.sdui.action;

import com.fajekrina.sduir.sdui.core.TraceIds;
import com.fajekrina.sduir.sdui.web.IdempotencyStore;
import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.web.SduiIdempotency;
import com.fajekrina.sduir.sdui.web.SduiResponse;
import com.fajekrina.sduir.sdui.web.Json;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionTest {
    record Vote(String vote) {}

    @SduiApi(name = "sendFeedback", idempotent = true)
    static class SendFeedback implements SduiHandler<Vote, Map<String, String>> {
        @Override
        public Map<String, String> handle(Vote request, SduiContext context) {
            return Map.of("vote", request.vote(), "page", context.requirePage(), "customer", context.requireCustomer());
        }
    }

    @SduiSource(name = "coffeeSpending")
    static class Coffee implements SduiHandler<JsonNode, Map<String, Object>> {
        @Override
        public Map<String, Object> handle(JsonNode request, SduiContext context) {
            return Map.of("title", "Kopi", "period", request.path("period").asText(""), "guest", context.customerId() == null);
        }
    }

    static SduiActionDispatcher dispatcher(Class<?>... handlers) {
        var context = new AnnotationConfigApplicationContext(handlers);
        var idempotency = new SduiIdempotency(new IdempotencyStore.Memory(), Duration.ofHours(1), Clock.systemUTC());
        return new SduiActionDispatcherImpl(new ActionRegistry(context), new SduiErrors(TraceIds.NONE), idempotency);
    }

    static MockHttpServletRequest request(String method, String body) {
        var r = new MockHttpServletRequest(method, "/sdui/api/x");
        r.addHeader("X-Sdui-Page", "insightKopi");
        if (body != null) r.setContent(body.getBytes(StandardCharsets.UTF_8));
        return r;
    }

    static int status(SduiResponse answer) {
        if (answer.status() >= 400) assertThat(Json.parse(answer.text()).path("errorCode").asText()).startsWith("SDUI-");
        return answer.status();
    }

    @Test
    void aHandlerAnswersItsNameWithTheRequestReadAsItsType() {
        SduiActionDispatcher actions = dispatcher(SendFeedback.class, Coffee.class);
        var post = request("POST", "{\"vote\":\"like\"}");
        post.addHeader("Idempotency-Key", "k1");
        JsonNode answer = Json.parse(actions.dispatch("sendFeedback", post, "andi").text());
        assertThat(answer.path("vote").asText()).isEqualTo("like");
        assertThat(answer.path("page").asText()).isEqualTo("insightKopi");
        assertThat(answer.path("customer").asText()).isEqualTo("andi");

        var get = request("GET", null);
        get.setParameter("period", "2025-10");
        JsonNode chart = Json.parse(actions.dispatch("coffeeSpending", get, null).text());
        assertThat(chart.path("period").asText()).isEqualTo("2025-10");
        assertThat(chart.path("guest").asBoolean()).isTrue();

        assertThat(Json.parse(actions.names().text())).hasSize(2);
    }

    @Test
    void aRetryWithTheSameKeyIsReplayedAndAnotherBodyIsRefused() {
        SduiActionDispatcher actions = dispatcher(SendFeedback.class, Coffee.class);
        var first = request("POST", "{\"vote\":\"like\"}");
        first.addHeader("Idempotency-Key", "same");
        assertThat(actions.dispatch("sendFeedback", first, "andi").status()).isEqualTo(200);
        var retry = request("POST", "{\"vote\":\"like\"}");
        retry.addHeader("Idempotency-Key", "same");
        SduiResponse replay = actions.dispatch("sendFeedback", retry, "andi");
        assertThat(replay.headers().getFirst("Idempotency-Replayed")).isEqualTo("true");
        var other = request("POST", "{\"vote\":\"dislike\"}");
        other.addHeader("Idempotency-Key", "same");
        assertThat(status(actions.dispatch("sendFeedback", other, "andi"))).isEqualTo(422);
    }

    @Test
    void wrongNamesMethodsKeysAndGuestsAreRefusedInTheContractFormat() {
        SduiActionDispatcher actions = dispatcher(SendFeedback.class, Coffee.class);
        assertThat(status(actions.dispatch("nope", request("POST", "{}"), "andi"))).isEqualTo(404);
        assertThat(status(actions.dispatch("sendFeedback", request("GET", null), "andi"))).isEqualTo(405);
        assertThat(status(actions.dispatch("sendFeedback", request("POST", "{\"vote\":\"like\"}"), "andi"))).isEqualTo(400);
        var guest = request("POST", "{\"vote\":\"like\"}");
        guest.addHeader("Idempotency-Key", "k2");
        assertThat(status(actions.dispatch("sendFeedback", guest, null))).isEqualTo(401);
        var bad = request("POST", "{\"vote\":{\"x\":1}}");
        bad.addHeader("Idempotency-Key", "k3");
        assertThat(status(actions.dispatch("sendFeedback", bad, "andi"))).isEqualTo(422);
    }

    @SduiApi(name = "sendFeedback")
    static class Twice implements SduiHandler<JsonNode, String> {
        @Override public String handle(JsonNode request, SduiContext context) { return "x"; }
    }

    @Test
    void aNameAnsweredTwiceStopsTheService() {
        assertThatThrownBy(() -> dispatcher(SendFeedback.class, Twice.class)).hasMessageContaining("two handlers named sendFeedback");
    }
}
