package com.fajekrina.sduir.foundation.sdui.exception;

import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiFault;
import com.fajekrina.sduir.foundation.sdui.model.response.SduiErrorResponse;
import com.fajekrina.sduir.foundation.sdui.utils.TraceIds;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/// The contract's error format, `{ errorCode, message, traceId }`, which Studio and the
/// renderer read. Every handler answers through {@link #guard}, so an error leaves in this
/// format whatever error handling the service has; internal errors never leak their message.
public final class SduiErrorGuard {
    private static final Log LOG = LogFactory.getLog(SduiErrorGuard.class);
    private final TraceIds traces;

    public SduiErrorGuard(TraceIds traces) { this.traces = traces; }

    /// `work`'s answer, or its error as an error answer
    public SduiResponse guard(Supplier<SduiResponse> work) {
        try {
            return work.get();
        } catch (SduiException e) {
            return of(e);
        } catch (RuntimeException e) {
            LOG.error("unhandled error", e);
            return body(SduiFault.INTERNAL, "internal error", traces.current());
        }
    }

    public SduiResponse of(SduiException e) { return body(e.fault(), e.getMessage(), traces.current()); }

    /// The error body {@link SduiErrorResponse} `{ errorCode, message, traceId }` (`traceId` left out when null).
    public static SduiResponse body(SduiFault fault, String message, String traceId) {
        return SduiResponse.json(fault.status().value(), new SduiErrorResponse(fault.code(), message, traceId));
    }
}
