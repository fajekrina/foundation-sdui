package com.fajekrina.sduir.foundation.sdui.service.action;

import com.fajekrina.sduir.foundation.sdui.model.response.SduiResponse;
import jakarta.servlet.http.HttpServletRequest;

/// Runs the {@link SduiHandler} a page names. The service writes the routes (operation `actionNames` →
/// {@link #names()}, `action` → {@link #dispatch}, at paths of its own) and hands over the customer
/// from its own authentication; everything else is checked here: unknown name 404, wrong method
/// 405, a body that does not read as the handler's request type 422, a POST of an idempotent
/// handler without `Idempotency-Key` 400, a guest on a customer action 401. Errors leave in the
/// contract's format.
///
/// Built in: {@link SduiActionDispatcherImpl}. A service with its own bean of this type replaces it.
public interface SduiActionDispatcher {
    String PAGE_HEADER = com.fajekrina.sduir.foundation.sdui.contract.constant.SduiHeaders.PAGE;

    /// `[{ name, type, method, title }]`: what the Studio may offer
    SduiResponse names();

    /// Runs the handler `name` for `request` (GET: its query; POST: its body). `customerId`: the
    /// customer the service's authentication found, or null for a guest.
    SduiResponse dispatch(String name, HttpServletRequest request, String customerId);
}
