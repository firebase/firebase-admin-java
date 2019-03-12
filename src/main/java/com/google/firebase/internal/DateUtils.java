/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.internal;

import java.lang.ref.SoftReference;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.http.util.Args;

/**
 * A utility class for parsing and formatting HTTP dates as used in cookies and
 * other headers.  This class handles dates as defined by RFC 2616 section
 * 3.3.1 as well as some other common non-standard formats.
 *
 * <p>This class was copied from the
 * <a href="http://svn.apache.org/repos/asf/httpcomponents/httpclient/tags/4.3/httpclient/src/main/java/org/apache/http/client/utils/DateUtils.java">
 * Apache HTTP client</a> in order to avoid a direct dependency on it. We currently
 * have a transitive dependency on this library (via Google API client), but the API
 * client team is working towards removing it.
 */
final class DateUtils {

  /**
   * Date format pattern used to parse HTTP date headers in RFC 1123 format.
   */
  public static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

  /**
   * Date format pattern used to parse HTTP date headers in RFC 1036 format.
   */
  public static final String PATTERN_RFC1036 = "EEE, dd-MMM-yy HH:mm:ss zzz";

  /**
   * Date format pattern used to parse HTTP date headers in ANSI C
   * {@code asctime()} format.
   */
  public static final String PATTERN_ASCTIME = "EEE MMM d HH:mm:ss yyyy";

  private static final String[] DEFAULT_PATTERNS = new String[] {
      PATTERN_RFC1123,
      PATTERN_RFC1036,
      PATTERN_ASCTIME
  };

  private static final Date DEFAULT_TWO_DIGIT_YEAR_START;

  public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  static {
    final Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(GMT);
    calendar.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    DEFAULT_TWO_DIGIT_YEAR_START = calendar.getTime();
  }

  /**
   * Parses a date value.  The formats used for parsing the date value are retrieved from
   * the default http params.
   *
   * @param dateValue the date value to parse
   *
   * @return the parsed date or null if input could not be parsed
   */
  public static Date parseDate(final String dateValue) {
    return parseDate(dateValue, null, null);
  }

  /**
   * Parses the date value using the given date formats.
   *
   * @param dateValue the date value to parse
   * @param dateFormats the date formats to use
   * @param startDate During parsing, two digit years will be placed in the range
   *     {@code startDate} to {@code startDate + 100 years}. This value may
   *     be {@code null}. When {@code null} is given as a parameter, year
   * {@code 2000} will be used.
   *
   * @return the parsed date or null if input could not be parsed
   */
  public static Date parseDate(
      final String dateValue,
      final String[] dateFormats,
      final Date startDate) {
    Args.notNull(dateValue, "Date value");
    final String[] localDateFormats = dateFormats != null ? dateFormats : DEFAULT_PATTERNS;
    final Date localStartDate = startDate != null ? startDate : DEFAULT_TWO_DIGIT_YEAR_START;
    String v = dateValue;
    // trim single quotes around date if present
    // see issue #5279
    if (v.length() > 1 && v.startsWith("'") && v.endsWith("'")) {
      v = v.substring(1, v.length() - 1);
    }

    for (final String dateFormat : localDateFormats) {
      final SimpleDateFormat dateParser = DateFormatHolder.formatFor(dateFormat);
      dateParser.set2DigitYearStart(localStartDate);
      final ParsePosition pos = new ParsePosition(0);
      final Date result = dateParser.parse(v, pos);
      if (pos.getIndex() != 0) {
        return result;
      }
    }
    return null;
  }

  /**
   * Clears thread-local variable containing {@link java.text.DateFormat} cache.
   */
  public static void clearThreadLocal() {
    DateFormatHolder.clearThreadLocal();
  }

  /** This class should not be instantiated. */
  private DateUtils() {
  }

  /**
   * A factory for {@link SimpleDateFormat}s. The instances are stored in a
   * threadlocal way because SimpleDateFormat is not threadsafe as noted in
   * {@link SimpleDateFormat its javadoc}.
   *
   */
  static final class DateFormatHolder {

    private static final ThreadLocal<SoftReference<Map<String, SimpleDateFormat>>>
        THREADLOCAL_FORMATS = new ThreadLocal<>();

    /**
     * creates a {@link SimpleDateFormat} for the requested format string.
     *
     * @param pattern a non-{@code null} format String according to
     *     {@link SimpleDateFormat}. The format is not checked against
     *     {@code null} since all paths go through {@link DateUtils}.
     * @return the requested format. This simple dateformat should not be used
     *     to {@link SimpleDateFormat#applyPattern(String) apply} to a
     *     different pattern.
     */
    public static SimpleDateFormat formatFor(final String pattern) {
      final SoftReference<Map<String, SimpleDateFormat>> ref = THREADLOCAL_FORMATS.get();
      Map<String, SimpleDateFormat> formats = ref == null ? null : ref.get();
      if (formats == null) {
        formats = new HashMap<>();
        THREADLOCAL_FORMATS.set(new SoftReference<>(formats));
      }

      SimpleDateFormat format = formats.get(pattern);
      if (format == null) {
        format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        formats.put(pattern, format);
      }

      return format;
    }

    public static void clearThreadLocal() {
      THREADLOCAL_FORMATS.remove();
    }
  }
}
