package com.fajekrina.sduir.foundation.sdui.config;

import com.fajekrina.sduir.foundation.sdui.service.action.ActionRegistry;
import com.fajekrina.sduir.foundation.sdui.service.action.SduiActionDispatcher;
import com.fajekrina.sduir.foundation.sdui.service.action.impl.SduiActionDispatcherImpl;
import com.fajekrina.sduir.foundation.sdui.service.action.SduiHandler;

import com.fajekrina.sduir.foundation.sdui.exception.SduiErrorGuard;
import com.fajekrina.sduir.foundation.sdui.service.idempotency.SduiIdempotency;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/// The service's {@link SduiHandler} beans by name ({@link ActionRegistry}) and the
/// {@link SduiActionDispatcher} that runs them. No route: the service maps operations `actionNames` and
/// `action` (at paths of its own) to the dispatcher and hands over the customer of its own login.
@AutoConfiguration(after = SduiWebConfig.class)
public class SduiActionConfig {

    @Bean
    @ConditionalOnMissingBean
    public ActionRegistry sduiActionRegistry(ApplicationContext context) { return new ActionRegistry(context); }

    @Bean
    @ConditionalOnMissingBean(SduiActionDispatcher.class)
    public SduiActionDispatcher sduiActionDispatcher(ActionRegistry registry, SduiErrorGuard errors, SduiIdempotency idempotency) {
        return new SduiActionDispatcherImpl(registry, errors, idempotency);
    }
}
