package com.google.firebase.database.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An element annotated with this class indicates that it can be null. This is used by lint tools to
 * inform callers that they may send in null values as parameters. It can also be used on return
 * values to indicate to the caller that he or she must check for null.
 */
@Retention(RetentionPolicy.CLASS)
@Target( {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE})
public @interface Nullable {

  String value() default "";
}
