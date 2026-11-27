package com.fajekrina.sduir.foundation.sdui.exception;

import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiFault;
import com.fajekrina.sduir.foundation.sdui.annotation.SduiEndpoint;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.utils.TraceIds;
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
/// handler answers (handlers answer their own errors through {@link SduiErrorGuard#guard}) — in the
/// contract's format, ahead of the service's own error handling. Other controllers are not
/// touched.
@RestControllerAdvice(annotations = SduiEndpoint.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SduiErrorHandler {
    private static final Log LOG = LogFactory.getLog(SduiErrorHandler.class);
    private final TraceIds traces;

    public SduiErrorHandler(TraceIds traces) { this.traces = traces; }

    @ExceptionHandler(SduiException.class)
    public ResponseEntity<byte[]> sduiException(SduiException e) {
        return SduiErrorGuard.body(e.fault(), e.getMessage(), traces.current()).toEntity();
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<byte[]> badRequest(Exception e) {
        return SduiErrorGuard.body(SduiFault.BAD_REQUEST, e.getMessage(), traces.current()).toEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> internal(Exception e) {
        LOG.error("unhandled error", e);
        return SduiErrorGuard.body(SduiFault.INTERNAL, "internal error", traces.current()).toEntity();
    }
}
