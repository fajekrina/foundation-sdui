package com.fajekrina.sduir.sdui.action;

import com.fajekrina.sduir.sdui.contract.SduiProblem;
import com.fajekrina.sduir.sdui.core.ClientInfo;

/// What a handler knows about the call besides its body: the page that asks (`X-Sdui-Page`),
/// the app (`X-Sdui-Client`), the customer (handed over by the service's route; null for
/// a guest) and the `Idempotency-Key`.
public record SduiContext(String name, String page, ClientInfo client, String customerId, String idempotencyKey) {
    /// the customer, or 401 — for handlers that act for one
    public String requireCustomer() {
        if (customerId == null) throw SduiProblem.unauthorized("log in as a customer first");
        return customerId;
    }

    /// the page that asks, or 400 — for handlers that keep something per page
    public String requirePage() {
        if (page == null) throw SduiProblem.badRequest(SduiActionDispatcher.PAGE_HEADER + " must name the page that asks");
        return page;
    }
}
