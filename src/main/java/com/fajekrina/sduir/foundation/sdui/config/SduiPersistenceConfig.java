package com.fajekrina.sduir.foundation.sdui.config;

import com.fajekrina.sduir.foundation.sdui.repository.JdbcAudit;

import com.fajekrina.sduir.foundation.sdui.service.audit.SduiAudit;
import com.fajekrina.sduir.foundation.sdui.utils.TraceIds;
import org.springframework.beans.factory.ObjectProvider;
import com.fajekrina.sduir.foundation.sdui.properties.SduiProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/// The audit table (`sdui.audit.table`, e.g. `TPL.sdui_audit_log`). The tables themselves are
/// the service's to create: Flyway locations `classpath:sdui/db/template` (templates) and
/// `classpath:sdui/db/value` (pages), or the Oracle scripts in `sdui/sql/oracle/`.
@ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
@AutoConfiguration
public class SduiPersistenceConfig {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("sdui.audit.table")
    public SduiAudit sduiAudit(JdbcTemplate jdbc, SduiProperties properties, ObjectProvider<TraceIds> traces) {
        return new JdbcAudit(jdbc, properties.getAudit().getTable(), traces.getIfAvailable(() -> TraceIds.NONE));
    }
}
