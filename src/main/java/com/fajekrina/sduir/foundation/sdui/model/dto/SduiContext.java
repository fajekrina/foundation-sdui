package com.fajekrina.sduir.foundation.sdui.model.dto;

import com.fajekrina.sduir.foundation.sdui.contract.constant.SduiHeaders;
import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.contract.model.ClientInfo;

/// What a handler knows about the call besides its body: the page that asks (`X-Sdui-Page`),
/// the app (`X-Sdui-Client`), the customer (handed over by the service's route; null for
/// a guest) and the `Idempotency-Key`.
public record SduiContext(String name, String page, ClientInfo client, String customerId, String idempotencyKey) {
    /// the customer, or 401 — for handlers that act for one
    public String requireCustomer() {
        if (customerId == null) throw SduiException.unauthorized("log in as a customer first");
        return customerId;
    }

    /// the page that asks, or 400 — for handlers that keep something per page
    public String requirePage() {
        if (page == null) throw SduiException.badRequest(SduiHeaders.PAGE + " must name the page that asks");
        return page;
    }
}
