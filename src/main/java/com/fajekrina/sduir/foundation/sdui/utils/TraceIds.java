package com.fajekrina.sduir.foundation.sdui.utils;

/// The trace id of the current request, for error bodies and audit rows. Port: the default
/// knows none (`null`); a service with tracing registers its own.
@FunctionalInterface
public interface TraceIds {
    TraceIds NONE = () -> null;

    String current();
}
