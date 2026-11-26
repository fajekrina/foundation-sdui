package com.fajekrina.sduir.sdui.web;

import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.core.TraceIds;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/// Errors of the service's {@link SduiEndpoint} controllers only — what is raised before a
/// handler answers (handlers answer their own errors through {@link SduiErrors#guard}) — in the
/// contract's format, ahead of the service's own error handling. Other controllers are not
/// touched.
@RestControllerAdvice(annotations = SduiEndpoint.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SduiEndpointAdvice {
    private static final Log LOG = LogFactory.getLog(SduiEndpointAdvice.class);
    private final TraceIds traces;

    public SduiEndpointAdvice(TraceIds traces) { this.traces = traces; }

    @ExceptionHandler(SduiProblem.class)
    public ResponseEntity<byte[]> problem(SduiProblem e) {
        return SduiErrors.body(e.status(), e.code(), e.getMessage(), traces.current()).toEntity();
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<byte[]> badRequest(Exception e) {
        return SduiErrors.body(400, "SDUI-400", e.getMessage(), traces.current()).toEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> internal(Exception e) {
        LOG.error("unhandled error", e);
        return SduiErrors.body(500, "SDUI-500", "internal error", traces.current()).toEntity();
    }
}
