package com.fajekrina.sduir.sdui.action;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// An {@link SduiHandler} bean that answers a page's `api` action `name`.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface SduiApi {
    /// the name pages use (an identifier, unique in the service)
    String name();

    /// `POST` (default) or `GET`
    String method() default "POST";

    /// a POST must carry `Idempotency-Key` (a retry then replays the first answer)
    boolean idempotent() default false;

    /// the caller must be a customer (`CustomerResolver`), else 401
    boolean customer() default true;

    /// what it does, for the Studio's pick list
    String title() default "";
}
