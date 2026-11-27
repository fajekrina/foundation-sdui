package com.fajekrina.sduir.foundation.sdui.config;

import com.fajekrina.sduir.foundation.sdui.security.ActorResolver;
import com.fajekrina.sduir.foundation.sdui.repository.IdempotencyStore;
import com.fajekrina.sduir.foundation.sdui.security.RequestActorResolver;
import com.fajekrina.sduir.foundation.sdui.annotation.SduiEndpoint;
import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorHandler;
import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import com.fajekrina.sduir.foundation.sdui.service.idempotency.SduiIdempotency;

import com.fajekrina.sduir.foundation.sdui.contract.security.Ed25519Signer;
import com.fajekrina.sduir.foundation.sdui.contract.security.Signer;
import com.fajekrina.sduir.foundation.sdui.utils.TraceIds;
import com.fajekrina.sduir.foundation.sdui.properties.SduiProperties;
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
/// - {@link Signer}: `sdui.signing.private-key` (PKCS#8 base64) + `sdui.signing.key-id`, or
///   `sdui.signing.key-file` (dev; `sdui.signing.generate-if-missing`, default true)
/// - {@link SduiErrorGuard} (the contract's error format), {@link TraceIds} (none)
/// - {@link ActorResolver} (request attribute; `sdui.actor.trust-header` for `X-Sdui-Actor`)
/// - {@link SduiIdempotency} with an {@link IdempotencyStore} (memory; `sdui.idempotency.ttl`, default 24h)
///
/// Turns on {@link SduiProperties} (`sdui.*`) for every part.
///
/// No route, no filter: the only web hook is {@link SduiErrorHandler}, which applies to the
/// service's {@link SduiEndpoint} controllers alone.
@AutoConfiguration(after = com.fajekrina.sduir.foundation.sdui.contract.config.SduiContractConfig.class)
@EnableConfigurationProperties(SduiProperties.class)
@Import(SduiErrorHandler.class)
public class SduiWebConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock sduiClock() { return Clock.systemUTC(); }

    @Bean
    @ConditionalOnMissingBean
    public TraceIds sduiTraceIds() { return TraceIds.NONE; }

    @Bean
    @ConditionalOnMissingBean
    public SduiErrorGuard sduiErrors(TraceIds traces) { return new SduiErrorGuard(traces); }

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
