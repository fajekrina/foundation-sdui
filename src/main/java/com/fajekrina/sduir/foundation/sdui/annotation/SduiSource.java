package com.fajekrina.sduir.foundation.sdui.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// An {@link SduiHandler} bean that answers a chart's data `source` `name` (always GET). The
/// answer is the summary the spec reads: `{ title, unit, items: [{ key, label, value }] }`
/// (other fields may be read by pages as tokens).
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface SduiSource {
    String name();

    /// the caller must be a customer, else 401 (default false: a guest sees example data)
    boolean customer() default false;

    String title() default "";
}
