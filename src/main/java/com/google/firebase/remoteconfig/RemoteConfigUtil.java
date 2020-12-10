/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

final class RemoteConfigUtil {

  // SimpleDateFormat cannot handle fractional seconds in timestamps
  // (example: "2014-10-02T15:01:23.045123456Z"). Therefore, we strip fractional seconds
  // from the date string (example: "2014-10-02T15:01:23") when parsing Zulu timestamp strings.
  // The backend API expects timestamps in Zulu format with fractional seconds. To generate correct
  // timestamps in payloads we use ".SSS000000'Z'" suffix.
  // Hence, two Zulu date patterns are used below.
  private static final String ZULU_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS000000'Z'";
  private static final String ZULU_DATE_NO_FRAC_SECS_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
  private static final String UTC_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss z";
  private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

  static boolean isValidVersionNumber(String versionNumber) {
    return !Strings.isNullOrEmpty(versionNumber) && versionNumber.matches("^\\d+$");
  }

  static long convertToMilliseconds(String dateString) throws ParseException {
    try {
      return convertFromUtcZuluFormat(dateString);
    } catch (ParseException e) {
      return convertFromUtcDateFormat(dateString);
    }
  }

  static String convertToUtcZuluFormat(long millis) {
    // sample output date string: 2020-11-12T22:12:02.000000000Z
    checkArgument(millis >= 0, "Milliseconds duration must not be negative");
    SimpleDateFormat dateFormat = new SimpleDateFormat(ZULU_DATE_PATTERN);
    dateFormat.setTimeZone(UTC_TIME_ZONE);
    return dateFormat.format(new Date(millis));
  }

  static String convertToUtcDateFormat(long millis) {
    // sample output date string: Tue, 08 Dec 2020 15:49:51 GMT
    checkArgument(millis >= 0, "Milliseconds duration must not be negative");
    SimpleDateFormat dateFormat = new SimpleDateFormat(UTC_DATE_PATTERN);
    dateFormat.setTimeZone(UTC_TIME_ZONE);
    return dateFormat.format(new Date(millis));
  }

  static long convertFromUtcZuluFormat(String dateString) throws ParseException {
    checkArgument(!Strings.isNullOrEmpty(dateString), "Date string must not be null or empty");
    // Input timestamp is in RFC3339 UTC "Zulu" format, accurate to
    // nanoseconds (up to 9 fractional seconds digits).
    // SimpleDateFormat cannot handle fractional seconds, therefore we strip fractional seconds
    // from the input date string before parsing.
    // example: input -> "2014-10-02T15:01:23.045123456Z"
    // formatted -> "2014-10-02T15:01:23"
    int indexOfPeriod = dateString.indexOf(".");
    if (indexOfPeriod != -1) {
      dateString = dateString.substring(0, indexOfPeriod);
    }
    SimpleDateFormat dateFormat = new SimpleDateFormat(ZULU_DATE_NO_FRAC_SECS_PATTERN);
    dateFormat.setTimeZone(UTC_TIME_ZONE);
    return dateFormat.parse(dateString).getTime();
  }

  static long convertFromUtcDateFormat(String dateString) throws ParseException {
    // sample input date string: Tue, 08 Dec 2020 15:49:51 GMT
    checkArgument(!Strings.isNullOrEmpty(dateString), "Date string must not be null or empty");
    SimpleDateFormat dateFormat = new SimpleDateFormat(UTC_DATE_PATTERN);
    dateFormat.setTimeZone(UTC_TIME_ZONE);
    return dateFormat.parse(dateString).getTime();
  }
}
