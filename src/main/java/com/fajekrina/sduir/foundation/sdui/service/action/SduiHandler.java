package com.fajekrina.sduir.foundation.sdui.service.action;

import com.fajekrina.sduir.foundation.sdui.model.dto.SduiContext;

/// What answers one name a page calls (`api` action or chart `source`). A bean of the service,
/// named by {@link SduiApi} or {@link SduiSource}; `Q` is read from the JSON body (POST) or the
/// query parameters (GET) — use {@link com.fasterxml.jackson.databind.JsonNode} to take it as is.
/// The answer is written as JSON. Throw `SduiException` for a 4xx.
@FunctionalInterface
public interface SduiHandler<Q, R> {
    R handle(Q request, SduiContext context);
}
