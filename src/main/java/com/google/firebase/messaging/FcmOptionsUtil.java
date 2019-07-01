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

package com.google.firebase.messaging;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Pattern;

final class FcmOptionsUtil {

  /**
   * Pattern matching a valid analytics labels.
   */
  private static final Pattern ANALYTICS_LABEL_REGEX = Pattern.compile("^[a-zA-Z0-9-_.~%]{0,50}$");

  /**
   * Returns false if the supplied {@code analyticsLabel} has a disallowed format.
   */
  private static boolean isValid(String analyticsLabel) {
    return ANALYTICS_LABEL_REGEX.matcher(analyticsLabel).matches();
  }

  /**
   * Validates the format of the supplied label.
   *
   * @throws IllegalArgumentException If the label is non-null and has a disallowed format.
   */
  static void checkAnalyticsLabel(String analyticsLabel) {
    checkArgument(
        analyticsLabel == null || isValid(analyticsLabel),
        "Analytics label must have format matching'^[a-zA-Z0-9-_.~%]{1,50}$");
  }
}
