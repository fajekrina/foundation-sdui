package com.fajekrina.sduir.sdui.config;

import com.fajekrina.sduir.sdui.web.ActorResolver;
import com.fajekrina.sduir.sdui.web.IdempotencyStore;
import com.fajekrina.sduir.sdui.web.RequestActorResolver;
import com.fajekrina.sduir.sdui.web.SduiEndpoint;
import com.fajekrina.sduir.sdui.web.SduiEndpointAdvice;
import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.web.SduiIdempotency;

import com.fajekrina.sduir.sdui.contract.SpecContract;
import com.fajekrina.sduir.sdui.core.Ed25519Signer;
import com.fajekrina.sduir.sdui.core.Signer;
import com.fajekrina.sduir.sdui.core.TraceIds;
import com.fajekrina.sduir.sdui.properties.SduiProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;
import java.time.Clock;

/// The shared beans of the handler modules, each replaceable by a bean of the service:
/// - {@link SpecContract}
/// - {@link Signer}: `sdui.signing.private-key` (PKCS#8 base64) + `sdui.signing.key-id`, or
///   `sdui.signing.key-file` (dev; `sdui.signing.generate-if-missing`, default true)
/// - {@link SduiErrors} (the contract's error format), {@link TraceIds} (none)
/// - {@link ActorResolver} (request attribute; `sdui.actor.trust-header` for `X-Sdui-Actor`)
/// - {@link SduiIdempotency} with an {@link IdempotencyStore} (memory; `sdui.idempotency.ttl`, default 24h)
///
/// Turns on {@link SduiProperties} (`sdui.*`) for every part.
///
/// No route, no filter: the only web hook is {@link SduiEndpointAdvice}, which applies to the
/// service's {@link SduiEndpoint} controllers alone.
@AutoConfiguration
@EnableConfigurationProperties(SduiProperties.class)
@Import(SduiEndpointAdvice.class)
public class SduiWebConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock sduiClock() { return Clock.systemUTC(); }

    @Bean
    @ConditionalOnMissingBean
    public SpecContract specContract() { return new SpecContract(); }

    @Bean
    @ConditionalOnMissingBean
    public TraceIds sduiTraceIds() { return TraceIds.NONE; }

    @Bean
    @ConditionalOnMissingBean
    public SduiErrors sduiErrors(TraceIds traces) { return new SduiErrors(traces); }

    @Bean
    @ConditionalOnMissingBean
    public ActorResolver sduiActorResolver(SduiProperties properties) {
        return new RequestActorResolver(properties.getActor().isTrustHeader());
    }

    @Bean
    @ConditionalOnMissingBean(Signer.class)
    @ConditionalOnProperty("sdui.signing.private-key")
    public Signer sduiConfiguredSigner(SduiProperties properties) {
        return Ed25519Signer.fromPkcs8(properties.getSigning().getKeyId(), properties.getSigning().getPrivateKey());
    }

    @Bean
    @ConditionalOnMissingBean(Signer.class)
    @ConditionalOnExpression("'${sdui.signing.key-file:}' != '' && '${sdui.signing.private-key:}' == ''")
    public Signer sduiFileSigner(SduiProperties properties) {
        return Ed25519Signer.fromFile(Path.of(properties.getSigning().getKeyFile()), properties.getSigning().isGenerateIfMissing());
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyStore sduiIdempotencyStore() { return new IdempotencyStore.Memory(); }

    @Bean
    @ConditionalOnMissingBean
    public SduiIdempotency sduiIdempotency(IdempotencyStore store, Clock clock, SduiProperties properties) {
        return new SduiIdempotency(store, properties.getIdempotency().getTtl(), clock);
    }
}
