package com.fajekrina.sduir.foundation.sdui.config;

import com.fajekrina.sduir.foundation.sdui.repository.DeliveryRepository;
import com.fajekrina.sduir.foundation.sdui.service.delivery.SduiDelivery;
import com.fajekrina.sduir.foundation.sdui.service.delivery.impl.SduiDeliveryImpl;
import com.fajekrina.sduir.foundation.sdui.security.SigningKeys;

import com.fajekrina.sduir.foundation.sdui.contract.security.Signer;
import org.springframework.beans.factory.ObjectProvider;
import com.fajekrina.sduir.foundation.sdui.properties.SduiProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/// The handlers of the read-only client API (`sdui.delivery.enabled`, default false); no routes:
/// the service maps them ({@link SduiDelivery}). Properties:
/// - `sdui.delivery.template-schema`, `sdui.delivery.page-schema`: where the `*_read` views are
///   (default: the connection's schema)
/// - `sdui.delivery.fallback`: `no-content` (default, 204) | `previous-release` — for a native
///   app whose version does not carry the active page's template release
/// - `sdui.delivery.public-key-file`: the published key, when the service does not sign
///
/// It reads through the service's JdbcTemplate: give delivery a read-only database user.
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@AutoConfiguration(after = SduiWebConfig.class)
@ConditionalOnProperty(name = "sdui.delivery.enabled", havingValue = "true")
public class SduiDeliveryConfig {

    @Bean
    @ConditionalOnMissingBean
    public SigningKeys sduiSigningKeys(ObjectProvider<Signer> signer, SduiProperties properties) {
        String file = properties.getDelivery().getPublicKeyFile();
        if (!file.isBlank()) return SigningKeys.fromFile(Path.of(file));
        Signer s = signer.getIfAvailable();
        if (s == null) return List::of;
        return () -> List.of(new SigningKeys.Key(s.keyId(), Base64.getEncoder().encodeToString(s.publicKeyRaw()), "ACTIVE"));
    }

    @Bean
    @ConditionalOnMissingBean
    public DeliveryRepository sduiDeliveryRepository(JdbcTemplate jdbc, SduiProperties properties) {
        SduiProperties.Delivery d = properties.getDelivery();
        return new DeliveryRepository(jdbc, d.getTemplateSchema(), d.getPageSchema());
    }

    @Bean
    @ConditionalOnMissingBean(SduiDelivery.class)
    public SduiDelivery sduiDelivery(DeliveryRepository releases, SigningKeys keys, SduiErrorGuard errors, SduiProperties properties) {
        return new SduiDeliveryImpl(releases, keys, errors, properties.getDelivery().getFallback(), properties.getLanguage().getDefault());
    }
}
