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

import java.util.Calendar;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for the {@link DateUtils}. Adapted from the tests available in the
 * <a href="http://svn.apache.org/repos/asf/httpcomponents/httpclient/tags/4.3/httpclient/src/test/java/org/apache/http/client/utils/TestDateUtils.java">
 * Apache HTTP client</a> library.
 */
public class DateUtilsTest {

  @Test
  public void testBasicDateParse() {
    final Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(DateUtils.GMT);
    calendar.set(2005, Calendar.OCTOBER, 14, 0, 0, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    final Date date1 = calendar.getTime();

    Date date2 = DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT");
    Assert.assertEquals(date1, date2);
    date2 = DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT");
    Assert.assertEquals(date1, date2);
    date2 = DateUtils.parseDate("Fri, 14 Oct 2005 00:00:00 GMT");
    Assert.assertEquals(date1, date2);
  }

  @Test
  public void testInvalidInput() {
    try {
      DateUtils.parseDate(null);
      Assert.fail("NullPointerException should have been thrown");
    } catch (NullPointerException ex) {
      // expected
    }
  }

  @Test
  public void testTwoDigitYearDateParse() {
    final Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(DateUtils.GMT);
    calendar.set(2005, Calendar.OCTOBER, 14, 0, 0, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    Date date1 = calendar.getTime();

    Date date2 = DateUtils.parseDate("Friday, 14-Oct-05 00:00:00 GMT");
    Assert.assertEquals(date1, date2);
  }

  @Test
  public void testParseQuotedDate() {
    final Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(DateUtils.GMT);
    calendar.set(2005, Calendar.OCTOBER, 14, 0, 0, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    final Date date1 = calendar.getTime();

    final Date date2 = DateUtils.parseDate("'Fri, 14 Oct 2005 00:00:00 GMT'");
    Assert.assertEquals(date1, date2);
  }
}
