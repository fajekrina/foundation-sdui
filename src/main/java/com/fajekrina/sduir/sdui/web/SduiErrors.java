package com.fajekrina.sduir.sdui.web;

import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.core.TraceIds;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/// The contract's error format, `{ errorCode, message, traceId }`, which Studio and the
/// renderer read. Every handler answers through {@link #guard}, so an error leaves in this
/// format whatever error handling the service has; internal errors never leak their message.
public final class SduiErrors {
    private static final Log LOG = LogFactory.getLog(SduiErrors.class);
    private final TraceIds traces;

    public SduiErrors(TraceIds traces) { this.traces = traces; }

    /// `work`'s answer, or its error as an error answer
    public SduiResponse guard(Supplier<SduiResponse> work) {
        try {
            return work.get();
        } catch (SduiProblem e) {
            return of(e);
        } catch (RuntimeException e) {
            LOG.error("unhandled error", e);
            return body(500, "SDUI-500", "internal error", traces.current());
        }
    }

    public SduiResponse of(SduiProblem e) { return body(e.status(), e.code(), e.getMessage(), traces.current()); }

    /// The error body `{ errorCode, message, traceId }` (`traceId` left out when null).
    public static SduiResponse body(int status, String code, String message, String traceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("errorCode", code);
        out.put("message", message);
        if (traceId != null) out.put("traceId", traceId);
        return SduiResponse.json(status, out);
    }
}
