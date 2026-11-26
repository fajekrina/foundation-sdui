package com.fajekrina.sduir.sdui.config;

import com.fajekrina.sduir.sdui.action.ActionRegistry;
import com.fajekrina.sduir.sdui.action.SduiActionDispatcher;
import com.fajekrina.sduir.sdui.action.SduiActionDispatcherImpl;
import com.fajekrina.sduir.sdui.action.SduiHandler;

import com.fajekrina.sduir.sdui.web.SduiErrors;
import com.fajekrina.sduir.sdui.web.SduiIdempotency;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/// The service's {@link SduiHandler} beans by name ({@link ActionRegistry}) and the
/// {@link SduiActionDispatcher} that runs them. No route: the service maps `/sdui/api` and
/// `/sdui/api/{name}` to the dispatcher and hands over the customer of its own login.
@AutoConfiguration(after = SduiWebConfig.class)
public class SduiActionConfig {

    @Bean
    @ConditionalOnMissingBean
    public ActionRegistry sduiActionRegistry(ApplicationContext context) { return new ActionRegistry(context); }

    @Bean
    @ConditionalOnMissingBean(SduiActionDispatcher.class)
    public SduiActionDispatcher sduiActionDispatcher(ActionRegistry registry, SduiErrors errors, SduiIdempotency idempotency) {
        return new SduiActionDispatcherImpl(registry, errors, idempotency);
    }
}
