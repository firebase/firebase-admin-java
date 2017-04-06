package com.google.firebase.internal;

/**
 * Simple static methods to be called at the start of your own methods to verify correct arguments
 * and state.
 *
 * <p>Copied (and extended) from the version present in Ice Cream Sandwich+.
 *
 * @hide
 */
public final class Preconditions {

  private Preconditions() {
    throw new AssertionError("Uninstantiable");
  }

  // The following method is copied from Android's TextUtils class

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(T reference) {
    if (reference == null) {
      throw new NullPointerException("null reference");
    }
    return reference;
  }

  /**
   * Returns true if the string is null or 0-length.
   *
   * @param str the string to be examined
   * @return true if str is null or zero length
   */
  private static boolean isEmpty(CharSequence str) {
    if (str == null || str.length() == 0) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Ensures that the given String is not empty and not null.
   *
   * @param string the String to test
   * @return the non-null non-empty String that was validated
   * @throws IllegalArgumentException if {@code string} is null or empty
   */
  public static String checkNotEmpty(String string) {
    if (isEmpty(string)) {
      throw new IllegalArgumentException("Given String is empty or null");
    }
    return string;
  }

  /**
   * Ensures that the given String is not empty and not null.
   *
   * @param string the String to test
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using {@link String#valueOf(Object)}
   * @return the non-null non-empty String that was validated
   * @throws IllegalArgumentException if {@code string} is null or empty
   */
  public static String checkNotEmpty(String string, Object errorMessage) {
    if (isEmpty(string)) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
    return string;
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using {@link String#valueOf(Object)}
   * @return the non-null reference that was validated
   * @throws NullPointerException if {@code reference} is null
   */
  public static <T> T checkNotNull(T reference, Object errorMessage) {
    if (reference == null) {
      throw new NullPointerException(String.valueOf(errorMessage));
    }
    return reference;
  }

  /**
   * Ensures that an integer passed as a parameter to the calling method is not zero.
   *
   * @param value an integer
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using {@link String#valueOf(Object)}
   * @return the value that was validated
   * @throws IllegalArgumentException if {@code value} is zero
   */
  public static int checkNotZero(int value, Object errorMessage) {
    if (value == 0) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
    return value;
  }

  /**
   * Ensures that an integer passed as a parameter to the calling method is not zero.
   *
   * @param value an integer
   * @return the value that was validated
   * @throws IllegalArgumentException if {@code value} is zero
   */
  public static int checkNotZero(int value) {
    if (value == 0) {
      throw new IllegalArgumentException("Given Integer is zero");
    }
    return value;
  }

  /**
   * Ensures that a long passed as a parameter to the calling method is not zero.
   *
   * @param value a long
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using {@link String#valueOf(Object)}
   * @return the value that was validated
   * @throws IllegalArgumentException if {@code value} is zero
   */
  public static long checkNotZero(long value, Object errorMessage) {
    if (value == 0) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
    return value;
  }

  /**
   * Ensures that a long passed as a parameter to the calling method is not zero.
   *
   * @param value a long
   * @return the value that was validated
   * @throws IllegalArgumentException if {@code value} is zero
   */
  public static long checkNotZero(long value) {
    if (value == 0) {
      throw new IllegalArgumentException("Given Long is zero");
    }
    return value;
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression) {
    if (!expression) {
      throw new IllegalStateException();
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using {@link String#valueOf(Object)}
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalStateException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling instance, but not
   * involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be formatted using
   * {@link String#format(String, Object...)} with the errorMessageArgs
   * @param errorMessageArgs the arguments to use in the errorMessage
   * @throws IllegalStateException if {@code expression} is false
   */
  public static void checkState(
      boolean expression, String errorMessage, Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalStateException(String.format(errorMessage, errorMessageArgs));
    }
  }

  /**
   * Ensures the truth of an expression involving parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using {@link String#valueOf(Object)}
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression, Object errorMessage) {
    if (!expression) {
      throw new IllegalArgumentException(String.valueOf(errorMessage));
    }
  }

  /**
   * Ensures the truth of an expression involving parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be formatted using
   * {@link String#format(String, Object...)} with the errorMessageArgs
   * @param errorMessageArgs the arguments to use in the errorMessage
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(
      boolean expression, String errorMessage, Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessage, errorMessageArgs));
    }
  }

  /**
   * Ensures the truth of an expression involving parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalArgumentException if {@code expression} is false
   */
  public static void checkArgument(boolean expression) {
    if (!expression) {
      throw new IllegalArgumentException();
    }
  }

  // The following methods are copied from google-common library Preconditions class

  /**
   * Ensures that {@code index} specifies a valid <i>element</i> in an array, list or string of size
   * {@code size}. An element index may range from zero, inclusive, to {@code size}, exclusive.
   *
   * @param index a user-supplied index identifying an element of an array, list or string
   * @param size the size of that array, list or string
   * @return the value of {@code index}
   * @throws IndexOutOfBoundsException if {@code index} is negative or is not less than {@code
   * size}
   * @throws IllegalArgumentException if {@code size} is negative
   */
  public static int checkElementIndex(int index, int size) {
    return checkElementIndex(index, size, "index");
  }

  /**
   * Ensures that {@code index} specifies a valid <i>element</i> in an array, list or string of size
   * {@code size}. An element index may range from zero, inclusive, to {@code size}, exclusive.
   *
   * @param index a user-supplied index identifying an element of an array, list or string
   * @param size the size of that array, list or string
   * @param desc the text to use to describe this index in an error message
   * @return the value of {@code index}
   * @throws IndexOutOfBoundsException if {@code index} is negative or is not less than {@code
   * size}
   * @throws IllegalArgumentException if {@code size} is negative
   */
  public static int checkElementIndex(int index, int size, String desc) {
    // Carefully optimized for execution by hotspot (explanatory comment above)
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(badElementIndex(index, size, desc));
    }
    return index;
  }

  private static String badElementIndex(int index, int size, String desc) {
    if (index < 0) {
      return format("%s (%s) must not be negative", desc, index);
    } else if (size < 0) {
      throw new IllegalArgumentException("negative size: " + size);
    } else { // index >= size
      return format("%s (%s) must be less than size (%s)", desc, index, size);
    }
  }

  /**
   * Ensures that {@code index} specifies a valid <i>position</i> in an array, list or string of
   * size {@code size}. A position index may range from zero to {@code size}, inclusive.
   *
   * @param index a user-supplied index identifying a position in an array, list or string
   * @param size the size of that array, list or string
   * @return the value of {@code index}
   * @throws IndexOutOfBoundsException if {@code index} is negative or is greater than {@code size}
   * @throws IllegalArgumentException if {@code size} is negative
   */
  public static int checkPositionIndex(int index, int size) {
    return checkPositionIndex(index, size, "index");
  }

  /**
   * Ensures that {@code index} specifies a valid <i>position</i> in an array, list or string of
   * size {@code size}. A position index may range from zero to {@code size}, inclusive.
   *
   * @param index a user-supplied index identifying a position in an array, list or string
   * @param size the size of that array, list or string
   * @param desc the text to use to describe this index in an error message
   * @return the value of {@code index}
   * @throws IndexOutOfBoundsException if {@code index} is negative or is greater than {@code size}
   * @throws IllegalArgumentException if {@code size} is negative
   */
  public static int checkPositionIndex(int index, int size, String desc) {
    // Carefully optimized for execution by hotspot (explanatory comment above)
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException(badPositionIndex(index, size, desc));
    }
    return index;
  }

  private static String badPositionIndex(int index, int size, String desc) {
    if (index < 0) {
      return format("%s (%s) must not be negative", desc, index);
    } else if (size < 0) {
      throw new IllegalArgumentException("negative size: " + size);
    } else { // index > size
      return format("%s (%s) must not be greater than size (%s)", desc, index, size);
    }
  }

  /**
   * Ensures that {@code start} and {@code end} specify a valid <i>positions</i> in an array, list
   * or string of size {@code size}, and are in order. A position index may range from zero to
   * {@code size}, inclusive.
   *
   * @param start a user-supplied index identifying a starting position in an array, list or string
   * @param end a user-supplied index identifying a ending position in an array, list or string
   * @param size the size of that array, list or string
   * @throws IndexOutOfBoundsException if either index is negative or is greater than {@code size},
   * or if {@code end} is less than {@code start}
   * @throws IllegalArgumentException if {@code size} is negative
   */
  public static void checkPositionIndexes(int start, int end, int size) {
    // Carefully optimized for execution by hotspot (explanatory comment above)
    if (start < 0 || end < start || end > size) {
      throw new IndexOutOfBoundsException(badPositionIndexes(start, end, size));
    }
  }

  private static String badPositionIndexes(int start, int end, int size) {
    if (start < 0 || start > size) {
      return badPositionIndex(start, size, "start index");
    }
    if (end < 0 || end > size) {
      return badPositionIndex(end, size, "end index");
    }
    // end < start
    return format("end index (%s) must not be less than start index (%s)", end, start);
  }

  /**
   * Substitutes each {@code %s} in {@code template} with an argument. These are matched by position
   * - the first {@code %s} gets {@code args[0]}, etc. If there are more arguments than
   * placeholders, the unmatched arguments will be appended to the end of the formatted message in
   * square braces.
   *
   * @param template a non-null string containing 0 or more {@code %s} placeholders.
   * @param args the arguments to be substituted into the message template. Arguments are converted
   * to strings using {@link String#valueOf(Object)}. Arguments can be null.
   */
  static String format(String template, Object... args) {
    // start substituting the arguments into the '%s' placeholders
    StringBuilder builder = new StringBuilder(template.length() + 16 * args.length);
    int templateStart = 0;
    int i = 0;
    while (i < args.length) {
      int placeholderStart = template.indexOf("%s", templateStart);
      if (placeholderStart == -1) {
        break;
      }
      builder.append(template.substring(templateStart, placeholderStart));
      builder.append(args[i++]);
      templateStart = placeholderStart + 2;
    }
    builder.append(template.substring(templateStart));

    // if we run out of placeholders, append the extra args in square braces
    if (i < args.length) {
      builder.append(" [");
      builder.append(args[i++]);
      while (i < args.length) {
        builder.append(", ");
        builder.append(args[i++]);
      }
      builder.append("]");
    }

    return builder.toString();
  }
}
