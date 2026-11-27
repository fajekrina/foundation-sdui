package com.fajekrina.sduir.foundation.sdui.security;

import com.fajekrina.sduir.foundation.sdui.model.dto.SduiActor;

/// Who is calling the authoring endpoints. Port: the default ({@link RequestActorResolver})
/// takes the actor the service's authentication left on the request (`SduiActor.ATTRIBUTE`),
/// or — only when `sdui.actor.trust-header=true`, behind a gateway that sets it — the
/// `X-Sdui-Actor` header. A service registers its own bean to read its login.
@FunctionalInterface
public interface ActorResolver {
    /// the caller, or `SduiException.unauthorized`
    SduiActor current();
}
