package com.fajekrina.sduir.sdui.config;

import com.fajekrina.sduir.sdui.authoring.page.AssetService;
import com.fajekrina.sduir.sdui.authoring.page.LocalTemplateSource;
import com.fajekrina.sduir.sdui.authoring.page.Merchants;
import com.fajekrina.sduir.sdui.authoring.page.TemplateSource;
import com.fajekrina.sduir.sdui.authoring.page.UrlFetcher;
import com.fajekrina.sduir.sdui.authoring.page.SduiPageAuthoring;
import com.fajekrina.sduir.sdui.authoring.page.SduiPageAuthoringImpl;
import com.fajekrina.sduir.sdui.authoring.page.SduiAssetAuthoring;
import com.fajekrina.sduir.sdui.authoring.page.SduiAssetAuthoringImpl;
import com.fajekrina.sduir.sdui.authoring.page.ValueService;
import com.fajekrina.sduir.sdui.authoring.template.SduiTemplateAuthoring;
import com.fajekrina.sduir.sdui.authoring.template.SduiTemplateAuthoringImpl;
import com.fajekrina.sduir.sdui.web.ActorResolver;
import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.web.SduiIdempotency;
import com.fajekrina.sduir.sdui.authoring.template.TemplateService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/// The handlers of the Studio API (no routes: the service maps them), in two halves a service turns on separately:
/// - `sdui.authoring.templates.enabled` (default false): templates — draft, publish, deprecate,
///   which app versions carry a release; tables `classpath:sdui/db/template`
/// - `sdui.authoring.pages.enabled` (default false): pages and their pictures; tables
///   `classpath:sdui/db/value`. Needs a {@link TemplateSource}: the templates half of the same
///   service ({@link LocalTemplateSource}), or the service's own bean.
///
/// Each handler ({@link SduiTemplateAuthoring}, {@link SduiPageAuthoring}, {@link SduiAssetAuthoring}) is
/// the built-in `…Impl` unless the service has its own bean of that type.
///
/// Both need a {@link com.fajekrina.sduir.sdui.core.Signer} and a
/// {@link com.fajekrina.sduir.sdui.core.SduiAudit} (`sdui.audit.table`).
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@AutoConfiguration(after = SduiWebConfig.class)
public class SduiAuthoringConfig {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "sdui.authoring.templates.enabled", havingValue = "true")
    @Import(TemplateService.class)
    static class Templates {
        @Bean
        @ConditionalOnMissingBean(SduiTemplateAuthoring.class)
        SduiTemplateAuthoring sduiTemplateAuthoring(TemplateService templates, ActorResolver actors, SduiErrors errors, SduiIdempotency idempotency) {
            return new SduiTemplateAuthoringImpl(templates, actors, errors, idempotency);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "sdui.authoring.pages.enabled", havingValue = "true")
    @Import({ValueService.class, AssetService.class, UrlFetcher.class, Merchants.class})
    static class Pages {
        @Bean
        @ConditionalOnMissingBean(SduiPageAuthoring.class)
        SduiPageAuthoring sduiPageAuthoring(ValueService values, Merchants merchants, ActorResolver actors, SduiErrors errors, SduiIdempotency idempotency) {
            return new SduiPageAuthoringImpl(values, merchants, actors, errors, idempotency);
        }

        @Bean
        @ConditionalOnMissingBean(SduiAssetAuthoring.class)
        SduiAssetAuthoring sduiAssetAuthoring(AssetService assets, ActorResolver actors, SduiErrors errors, SduiIdempotency idempotency) {
            return new SduiAssetAuthoringImpl(assets, actors, errors, idempotency);
        }

        @Bean
        @ConditionalOnMissingBean(TemplateSource.class)
        TemplateSource sduiLocalTemplateSource(ObjectProvider<TemplateService> templates) {
            TemplateService local = templates.getIfAvailable();
            if (local == null) throw new IllegalStateException(
                "sdui pages need a TemplateSource: turn on sdui.authoring.templates.enabled in this service, or register a TemplateSource bean");
            return new LocalTemplateSource(local);
        }
    }
}
