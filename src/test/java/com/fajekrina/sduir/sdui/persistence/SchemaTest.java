package com.fajekrina.sduir.sdui.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaTest {
    @Test
    void theScriptsBuildTheSduiTablesAndTheirReadViews() {
        // one database, one schema per service (as template-service and value-service run); one
        // connection each: H2 rejected CHECK constraints when every new connection ran the INIT DDL again
        var tplDs = new SingleConnectionDataSource("jdbc:h2:mem:sduischema;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS TPL\\;SET SCHEMA TPL", "sa", "", true);
        var valDs = new SingleConnectionDataSource("jdbc:h2:mem:sduischema;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS VAL\\;SET SCHEMA VAL", "sa", "", true);
        Flyway.configure().dataSource(tplDs).schemas("TPL").defaultSchema("TPL").locations("classpath:sdui/db/template").load().migrate();
        Flyway.configure().dataSource(valDs).schemas("VAL").defaultSchema("VAL").locations("classpath:sdui/db/value").load().migrate();
        JdbcTemplate tpl = new JdbcTemplate(tplDs);
        JdbcTemplate val = new JdbcTemplate(valDs);
        JdbcTemplate jdbc = tpl;
        List<String> tables = jdbc.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA IN ('TPL', 'VAL') AND TABLE_NAME NOT LIKE 'flyway%' ORDER BY TABLE_NAME", String.class);
        assertThat(tables).allMatch((t) -> t.startsWith("SDUI_"));
        assertThat(tables).contains("SDUI_TEMPLATE", "SDUI_TEMPLATE_SUPPORT", "SDUI_TEMPLATE_RELEASE_READ", "SDUI_SCREEN", "SDUI_VALUE_RELEASE_READ", "SDUI_ASSET_READ", "SDUI_MANIFEST_READ");

        // the support table names a release and a native platform only
        tpl.update("INSERT INTO sdui_template (owner, template_key) VALUES ('sdui', 'banner')");
        tpl.update("INSERT INTO sdui_template_revision (owner, template_key, revision_no, payload, content_hash, created_by) VALUES ('sdui', 'banner', 1, '{}', 'h', 'ana')");
        long rev = tpl.queryForObject("SELECT revision_id FROM sdui_template_revision", Long.class);
        tpl.update("INSERT INTO sdui_template_release (owner, template_key, version, revision_id, payload, envelope, content_hash, key_id, published_by) VALUES ('sdui', 'banner', 1, ?, '{}', 'env', 'h', 'k', 'ana')", rev);
        tpl.update("INSERT INTO sdui_template_support (owner, template_key, version, platform, minimum_client_version, recorded_by) VALUES ('sdui', 'banner', 1, 'android', '5.4.0', 'ci')");
        assertThatThrownBy(() -> tpl.update("INSERT INTO sdui_template_support (owner, template_key, version, platform, minimum_client_version, recorded_by) VALUES ('sdui', 'banner', 1, 'web', '1', 'ci')")).isNotNull();
        assertThatThrownBy(() -> tpl.update("INSERT INTO sdui_template_support (owner, template_key, version, platform, minimum_client_version, recorded_by) VALUES ('sdui', 'banner', 2, 'ios', '1', 'ci')")).isNotNull();
        // the checks still hold after the rename
        assertThatThrownBy(() -> tpl.update("UPDATE sdui_template SET replaced_by = template_key")).isNotNull();
        Map<String, Object> view = jdbc.queryForMap("SELECT * FROM TPL.sdui_template_support_read");
        assertThat(view).containsEntry("MINIMUM_CLIENT_VERSION", "5.4.0");

        // delivery sees published pictures only
        val.update("INSERT INTO sdui_asset (asset_id, merchant_id, content_type, byte_size, content, content_hash, source, created_by) VALUES ('img_1', 'merchant_demo', 'image/png', 1, X'00', 'h', 'UPLOAD', 'ana')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM VAL.sdui_asset_read", Integer.class)).isZero();
        val.update("UPDATE sdui_asset SET published_at = CURRENT_TIMESTAMP");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM VAL.sdui_asset_read", Integer.class)).isEqualTo(1);
    }
}
