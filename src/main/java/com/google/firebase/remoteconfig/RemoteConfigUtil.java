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

  static boolean isValidVersionNumber(String versionNumber) {
    return !Strings.isNullOrEmpty(versionNumber) && versionNumber.matches("^\\d+$");
  }

  static long convertToMilliseconds(String dateString) throws ParseException {
    if (isUTCDateString(dateString)) {
      return convertFromUtcDateFormat(dateString);
    }
    return convertFromUtcZuluFormat(dateString);
  }

  static String convertToUtcZuluFormat(long millis) {
    // sample output date string: 2020-12-08T15:49:51.887878Z
    checkArgument(millis >= 0, "Milliseconds duration must not be negative");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS000000'Z'");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.format(new Date(millis));
  }

  static String convertToUtcDateFormat(long millis) {
    // sample output date string: Tue, 08 Dec 2020 15:49:51 GMT
    checkArgument(millis >= 0, "Milliseconds duration must not be negative");
    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.format(new Date(millis));
  }

  static long convertFromUtcZuluFormat(String dateString) throws ParseException {
    // sample input date string: 2020-12-08T15:49:51.887878Z
    checkArgument(!Strings.isNullOrEmpty(dateString), "Date string must not be null or empty");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.parse(dateString).getTime();
  }

  static long convertFromUtcDateFormat(String dateString) throws ParseException {
    // sample input date string: Tue, 08 Dec 2020 15:49:51 GMT
    checkArgument(!Strings.isNullOrEmpty(dateString), "Date string must not be null or empty");
    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return dateFormat.parse(dateString).getTime();
  }

  static boolean isUTCDateString(String dateString) {
    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    try {
      dateFormat.parse(dateString);
      return true;
    } catch (ParseException e) {
      return false;
    }
  }
}
