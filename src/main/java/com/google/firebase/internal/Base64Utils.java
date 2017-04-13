// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.firebase.internal;

/**
 * Base64 conversion utility helpers.
 *
 * @hide
 */
public final class Base64Utils {

  /**
   * @param encodedData String to decode into byte array. If input is null, output data will be null
   *     as well.
   * @return byte array corresponding to encoded string.
   */
  public static byte[] decode(String encodedData) {
    // Base64 decode explodes on null input.
    if (encodedData == null) {
      return null;
    }
    return Base64.decode(encodedData, Base64.DEFAULT);
  }

  /**
   * @param encodedData String to decode into byte array using the URL_SAFE option. If input is
   *     null, output data will be null as well.
   * @return byte array corresponding to encoded string.
   */
  public static byte[] decodeUrlSafe(String encodedData) {
    // Base64 decode explodes on null input.
    if (encodedData == null) {
      return null;
    }
    return Base64.decode(encodedData, Base64.URL_SAFE | Base64.NO_WRAP);
  }

  /**
   * @param encodedData String to decode into byte array using the URL_SAFE, NO_WRAP, and NO_PADDING
   *     options. If input is null, output data will be null as well.
   * @return byte array corresponding to encoded string.
   */
  public static byte[] decodeUrlSafeNoPadding(String encodedData) {
    // Base64 decode explodes on null input.
    if (encodedData == null) {
      return null;
    }
    return Base64.decode(encodedData, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
  }

  /**
   * This is the equivalent of {@link #decodeUrlSafeNoPadding(String)} except it accepts a byte[].
   *
   * @param encodedData byte[] to decode into byte array using the URL_SAFE, NO_WRAP, and NO_PADDING
   *     options. If input is null, output data will be null as well.
   * @return byte array corresponding to encoded string.
   */
  public static byte[] decodeUrlSafeNoPadding(byte[] encodedData) {
    // Base64 decode explodes on null input.
    if (encodedData == null) {
      return null;
    }
    return Base64.decode(encodedData, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
  }

  /**
   * @param data Byte array to encode with Base64. If input data is null, output will be null as
   *     well.
   * @return String representing encoded data.
   */
  public static String encode(byte[] data) {
    // Base64 encoder will explode if you give it null.
    if (data == null) {
      return null;
    }
    return Base64.encodeToString(data, Base64.DEFAULT);
  }

  /**
   * @param data Byte array to encode with Base64 using the URL_SAFE option. If input data is null,
   *     output will be null as well.
   * @return String representing encoded data.
   */
  public static String encodeUrlSafe(byte[] data) {
    // Base64 encoder will explode if you give it null.
    if (data == null) {
      return null;
    }
    return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP);
  }

  /**
   * @param data Byte array to encode with Base64 using the URL_SAFE, NO_WRAP, and NO_PADDING
   *     options. If input data is null, output will be null as well.
   * @return String representing encoded data.
   */
  public static String encodeUrlSafeNoPadding(byte[] data) {
    // Base64 encoder will explode if you give it null.
    if (data == null) {
      return null;
    }
    return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
  }
}
