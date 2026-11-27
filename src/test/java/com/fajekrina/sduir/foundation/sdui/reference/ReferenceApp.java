package com.fajekrina.sduir.foundation.sdui.reference;

import com.fajekrina.sduir.foundation.sdui.annotation.SduiApi;
import com.fajekrina.sduir.foundation.sdui.model.dto.SduiContext;
import com.fajekrina.sduir.foundation.sdui.service.action.SduiHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.util.Map;

/// foundation-sdui as a channel would run it with every part on — templates, pages, delivery
/// and actions in one service, on H2 — for the server conformance kit (the `*ConformanceTest`s
/// beside this). Its routes are {@link ReferenceRoutes}, deliberately unlike the demo's.
@SpringBootApplication
public class ReferenceApp {
    static final String SPACE = "sdui";

    /// one H2 database, the template tables in TPL and the page tables in VAL (as the services
    /// keep them), both found unqualified; one connection: H2 rejected CHECK constraints when
    /// every new connection ran the INIT DDL again
    @Bean
    DataSource dataSource() {
        var ds = new SingleConnectionDataSource("jdbc:h2:mem:reference;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS TPL\\;CREATE SCHEMA IF NOT EXISTS VAL", "sa", "", true);
        Flyway.configure().dataSource(ds).schemas("TPL").defaultSchema("TPL").locations("classpath:sdui/db/template").load().migrate();
        Flyway.configure().dataSource(ds).schemas("VAL").defaultSchema("VAL").locations("classpath:sdui/db/value").load().migrate();
        new org.springframework.jdbc.core.JdbcTemplate(ds).execute("SET SCHEMA_SEARCH_PATH TPL, VAL");
        return ds;
    }

    /// the one request of this service
    @SduiApi(name = "ping", method = "GET", customer = false, title = "Ping")
    static class Ping implements SduiHandler<JsonNode, Map<String, Object>> {
        @Override
        public Map<String, Object> handle(JsonNode request, SduiContext context) { return Map.of("pong", true, "page", context.page()); }
    }
}
