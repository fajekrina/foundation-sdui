package com.fajekrina.sduir.foundation.sdui.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a controller of the service that serves SDUI routes (authoring, delivery, action).
/// Errors raised in such a controller before its handler answers (a path variable that does
/// not convert, a missing header) leave in the contract's error format through
/// {@link SduiErrorHandler}; the service's own error handling keeps every other controller.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SduiEndpoint {}
