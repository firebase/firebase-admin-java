package com.google.firebase.database;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Properties that don't map to class fields when serializing to a class annotated with this
 * annotation cause an exception to be thrown.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ThrowOnExtraProperties {}
