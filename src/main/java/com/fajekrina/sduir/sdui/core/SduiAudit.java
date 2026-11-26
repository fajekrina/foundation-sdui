package com.fajekrina.sduir.sdui.core;

import java.util.Map;

/// Append-only audit: who did what to which subject. Port: the default writes a table
/// (`persistence.JdbcAudit`); a service may send it elsewhere.
public interface SduiAudit {
    void record(SduiActor actor, String space, String event, String subjectType, String subjectId, Map<String, ?> detail);
}
