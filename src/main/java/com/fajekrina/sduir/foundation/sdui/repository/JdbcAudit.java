package com.fajekrina.sduir.foundation.sdui.repository;

import com.fajekrina.sduir.foundation.sdui.contract.utils.SduiJson;
import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;
import com.fajekrina.sduir.foundation.sdui.service.audit.SduiAudit;
import com.fajekrina.sduir.foundation.sdui.utils.TraceIds;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.regex.Pattern;

/// The default {@link SduiAudit}: one append-only row per event in `sdui.audit.table`
/// (e.g. `TPL.sdui_audit_log`) — who did what to which subject, with the trace id.
public class JdbcAudit implements SduiAudit {
    private static final Pattern TABLE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)?$");
    private final JdbcTemplate jdbc;
    private final TraceIds traces;
    private final String insert;

    public JdbcAudit(JdbcTemplate jdbc, String table, TraceIds traces) {
        if (!TABLE.matcher(table).matches()) throw new IllegalArgumentException("bad audit table " + table);
        this.jdbc = jdbc;
        this.traces = traces;
        this.insert = "INSERT INTO " + table + " (merchant_id, actor_sub, event, subject_type, subject_id, trace_id, detail) VALUES (?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    public void record(SduiActor actor, String space, String event, String subjectType, String subjectId, Map<String, ?> detail) {
        jdbc.update(insert, space, actor.sub(), event, subjectType, subjectId, traces.current(), SduiJson.write(detail));
    }
}
