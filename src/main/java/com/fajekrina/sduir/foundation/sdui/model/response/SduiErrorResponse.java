package com.fajekrina.sduir.foundation.sdui.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/// The contract's error body, which Studio and the renderer read: the stable code
/// (`SduiFault.code()`), a message, and the trace id when the service knows one.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SduiErrorResponse(String errorCode, String message, String traceId) {}
