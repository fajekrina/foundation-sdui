package com.fajekrina.sduir.foundation.sdui.model.dto;

import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;

import java.util.Set;

/// Who is calling: a subject, a display name, roles, and the page spaces (formerly merchants) it
/// reaches (`*` = every one). The service decides how it is known (foundation-sdui web
/// `ActorResolver`); the SDUI code only checks it.
///
/// One Studio role: the **template admin**; services read on their callers' behalf.
public record SduiActor(String sub, String name, Set<String> roles, Set<String> merchants) {
    /// the request attribute an actor is kept under (set by the service's authentication)
    public static final String ATTRIBUTE = SduiActor.class.getName();

    public static final String TEMPLATE_ADMIN = "SDUI_TEMPLATE_ADMIN";
    public static final String SERVICE = "SDUI_SERVICE";

    /// Studio reads and writes: the admin; services read on their callers' behalf
    public static final String[] ADMIN_OR_SERVICE = {TEMPLATE_ADMIN, SERVICE};

    public boolean hasRole(String role) { return roles.contains(role); }

    public SduiActor require(String... anyOf) {
        for (String role : anyOf) if (roles.contains(role)) return this;
        throw SduiException.forbidden("requires one of " + String.join(", ", anyOf));
    }

    public SduiActor requireMerchant(String merchantId) {
        if (merchants.contains("*") || merchants.contains(merchantId)) return this;
        throw SduiException.forbidden("not allowed for merchant " + merchantId);
    }

    /// The one space an actor works for. A service, scoped to every space, has none.
    public String merchant() {
        if (merchants.size() == 1 && !merchants.contains("*")) return merchants.iterator().next();
        throw SduiException.forbidden("this account is not bound to one merchant");
    }
}
