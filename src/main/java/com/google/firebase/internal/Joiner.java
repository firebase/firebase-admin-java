// Copyright 2014 Google Inc. All Rights Reserved.

package com.google.firebase.internal;

import java.util.Iterator;

/**
 * Joins pieces of text with a separator.
 */
public class Joiner {

  private final String separator;

  private Joiner(String separator) {
    this.separator = separator;
  }

  public static Joiner on(String separator) {
    return new Joiner(separator);
  }

  /**
   * Appends each of part, using the configured separator between each.
   */
  public final StringBuilder appendTo(StringBuilder builder, Iterable<?> parts) {
    Iterator<?> iterator = parts.iterator();
    if (iterator.hasNext()) {
      builder.append(toString(iterator.next()));
      while (iterator.hasNext()) {
        builder.append(separator);
        builder.append(toString(iterator.next()));
      }
    }
    return builder;
  }

  /**
   * Returns a string containing the string representation of each of {@code parts}, using the
   * previously configured separator between each.
   */
  public final String join(Iterable<?> parts) {
    return appendTo(new StringBuilder(), parts).toString();
  }

  CharSequence toString(Object part) {
    return (part instanceof CharSequence) ? (CharSequence) part : part.toString();
  }
}
