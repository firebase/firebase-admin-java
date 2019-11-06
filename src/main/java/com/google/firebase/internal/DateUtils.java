/*
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

import static com.google.common.base.Preconditions.checkNotNull;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A utility class for parsing and formatting HTTP dates as used in cookies and
 * other headers.  This class handles dates as defined by RFC 2616 section
 * 3.3.1 as well as some other common non-standard formats.
 *
 * <p>Most of this class was borrowed from the
 * <a href="http://svn.apache.org/repos/asf/httpcomponents/httpclient/tags/4.3/httpclient/src/main/java/org/apache/http/client/utils/DateUtils.java">
 * Apache HTTP client</a> in order to avoid a direct dependency on it. We currently
 * have a transitive dependency on this library (via Google API client), but the API
 * client team is working towards removing it, so we won't have it in the classpath for long.
 *
 * <p>The original implementation of this class uses
 * thread locals to cache the {@code SimpleDateFormat} instances. Instead, this implementation
 * uses static constants and explicit locking to ensure thread safety. This is probably slower,
 * but also simpler and avoids memory leaks that may result from unreleased thread locals.
 */
final class DateUtils {

  /**
   * Date format pattern used to parse HTTP date headers in RFC 1123 format.
   */
  static final String PATTERN_RFC1123 = "EEE, dd MMM yyyy HH:mm:ss zzz";

  /**
   * Date format pattern used to parse HTTP date headers in RFC 1036 format.
   */
  static final String PATTERN_RFC1036 = "EEE, dd-MMM-yy HH:mm:ss zzz";

  /**
   * Date format pattern used to parse HTTP date headers in ANSI C
   * {@code asctime()} format.
   */
  static final String PATTERN_ASCTIME = "EEE MMM d HH:mm:ss yyyy";

  private static final SimpleDateFormat[] DEFAULT_PATTERNS = new SimpleDateFormat[] {
      new SimpleDateFormat(PATTERN_RFC1123, Locale.US),
      new SimpleDateFormat(PATTERN_RFC1036, Locale.US),
      new SimpleDateFormat(PATTERN_ASCTIME, Locale.US)
  };

  static final TimeZone GMT = TimeZone.getTimeZone("GMT");

  static {
    final Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(GMT);
    calendar.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    final Date defaultTwoDigitYearStart = calendar.getTime();

    for (final SimpleDateFormat datePattern : DEFAULT_PATTERNS) {
      datePattern.set2DigitYearStart(defaultTwoDigitYearStart);
    }
  }

  /**
   * Parses the date value using the given date formats.
   *
   * @param dateValue the date value to parse
   * @return the parsed date or null if input could not be parsed
   */
  public static Date parseDate(final String dateValue) {
    String v = checkNotNull(dateValue);
    // trim single quotes around date if present
    // see issue #5279
    if (v.length() > 1 && v.startsWith("'") && v.endsWith("'")) {
      v = v.substring(1, v.length() - 1);
    }

    for (final SimpleDateFormat datePattern : DEFAULT_PATTERNS) {
      final ParsePosition pos = new ParsePosition(0);
      synchronized (datePattern) {
        final Date result = datePattern.parse(v, pos);
        if (pos.getIndex() != 0) {
          return result;
        }
      }
    }
    return null;
  }

  /** This class should not be instantiated. */
  private DateUtils() {
  }
}
