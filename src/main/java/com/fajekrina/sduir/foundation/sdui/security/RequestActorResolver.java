package com.fajekrina.sduir.foundation.sdui.security;

import com.fajekrina.sduir.foundation.sdui.contract.exception.SduiException;
import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/// The default {@link ActorResolver}: the request attribute `SduiActor.ATTRIBUTE`, else (when
/// trusted) `X-Sdui-Actor: <subject>` — a Studio user (template admin) of every space.
public class RequestActorResolver implements ActorResolver {
    public static final String HEADER = com.fajekrina.sduir.foundation.sdui.contract.constant.SduiHeaders.ACTOR;
    private final boolean trustHeader;

    public RequestActorResolver(boolean trustHeader) { this.trustHeader = trustHeader; }

    @Override
    public SduiActor current() {
        HttpServletRequest request = request();
        if (request.getAttribute(SduiActor.ATTRIBUTE) instanceof SduiActor a) return a;
        String sub = trustHeader ? request.getHeader(HEADER) : null;
        if (sub != null && !sub.isBlank()) return new SduiActor(sub.trim(), sub.trim(), Set.of(SduiActor.TEMPLATE_ADMIN), Set.of("*"));
        throw SduiException.unauthorized("authentication required");
    }

    /// The actor the service's authentication put on the current request, or 401 (for code that
    /// has no resolver at hand).
    public static SduiActor fromRequest() { return fromRequest(request()); }

    public static SduiActor fromRequest(HttpServletRequest request) {
        if (request.getAttribute(SduiActor.ATTRIBUTE) instanceof SduiActor a) return a;
        throw SduiException.unauthorized("authentication required");
    }

    private static HttpServletRequest request() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) return sra.getRequest();
        throw SduiException.unauthorized("no request");
    }
}
