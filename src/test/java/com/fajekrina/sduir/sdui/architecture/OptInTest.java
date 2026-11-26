package com.fajekrina.sduir.sdui.architecture;

import com.fajekrina.sduir.sdui.config.SduiActionConfig;
import com.fajekrina.sduir.sdui.action.SduiActionDispatcher;
import com.fajekrina.sduir.sdui.config.SduiAuthoringConfig;
import com.fajekrina.sduir.sdui.authoring.page.SduiPageAuthoring;
import com.fajekrina.sduir.sdui.authoring.template.SduiTemplateAuthoring;
import com.fajekrina.sduir.sdui.core.Signer;
import com.fajekrina.sduir.sdui.delivery.SduiDelivery;
import com.fajekrina.sduir.sdui.config.SduiDeliveryConfig;
import com.fajekrina.sduir.sdui.delivery.SduiDeliveryImpl;
import com.fajekrina.sduir.sdui.web.SduiResponse;
import com.fajekrina.sduir.sdui.config.SduiPersistenceConfig;
import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.config.SduiWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/// One jar in every service: a part the service does not turn on makes no bean — a delivery
/// service holds no authoring code paths and no signer, a domain service no delivery.
class OptInTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SduiWebConfig.class, SduiPersistenceConfig.class,
            SduiAuthoringConfig.class, SduiDeliveryConfig.class, SduiActionConfig.class))
        .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:optin")));

    @Test
    void nothingIsOnUntilTheServiceTurnsItOn() {
        runner.run((ctx) -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SduiErrors.class).hasSingleBean(SduiActionDispatcher.class);
            assertThat(ctx).doesNotHaveBean(SduiTemplateAuthoring.class).doesNotHaveBean(SduiPageAuthoring.class)
                .doesNotHaveBean(SduiDelivery.class).doesNotHaveBean(Signer.class);
        });
    }

    @Test
    void deliveryTurnedOnBringsDeliveryOnly() {
        runner.withPropertyValues("sdui.delivery.enabled=true").run((ctx) -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SduiDelivery.class);
            assertThat(ctx).doesNotHaveBean(SduiTemplateAuthoring.class).doesNotHaveBean(SduiPageAuthoring.class).doesNotHaveBean(Signer.class);
        });
    }

    @Test
    void aServiceBeanReplacesTheBuiltInHandler() {
        SduiDelivery own = new SduiDelivery() {
            public SduiResponse template(String space, String key, int version, jakarta.servlet.http.HttpServletRequest r) { return SduiResponse.empty(204); }
            public SduiResponse spaces(jakarta.servlet.http.HttpServletRequest r) { return SduiResponse.empty(204); }
            public SduiResponse screen(String space, String key, jakarta.servlet.http.HttpServletRequest r) { return SduiResponse.empty(204); }
            public SduiResponse screenRelease(String space, String key, int sequence, jakarta.servlet.http.HttpServletRequest r) { return SduiResponse.empty(204); }
            public SduiResponse manifest(String space, jakarta.servlet.http.HttpServletRequest r) { return SduiResponse.empty(204); }
            public SduiResponse asset(String id, jakarta.servlet.http.HttpServletRequest r) { return SduiResponse.empty(204); }
            public SduiResponse signingKeys(jakarta.servlet.http.HttpServletRequest r) { return SduiResponse.empty(204); }
        };
        runner.withPropertyValues("sdui.delivery.enabled=true").withBean(SduiDelivery.class, () -> own).run((ctx) -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(SduiDelivery.class).doesNotHaveBean(SduiDeliveryImpl.class);
            assertThat(ctx.getBean(SduiDelivery.class)).isSameAs(own);
        });
        runner.withPropertyValues("sdui.delivery.enabled=true").run((ctx) -> assertThat(ctx.getBean(SduiDelivery.class)).isInstanceOf(SduiDeliveryImpl.class));
    }
}
