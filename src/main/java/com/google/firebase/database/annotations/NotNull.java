package com.google.firebase.database.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An element annotated with this class indicates that it cannot be null. This is used by lint tools
 * to ensure callers properly check for null values before sending values as parameters. It can also
 * be used on return values to indicate to the caller that the return cannot be null so therefore no
 * null checks need to be made.
 */
@Retention(RetentionPolicy.CLASS)
@Target( {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
public @interface NotNull {

  String value() default "";
}
