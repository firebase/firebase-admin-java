/*
 * Copyright 2018 Google Inc.
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

package com.google.firebase.projectmanagement;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShaCertificateTest {

  @Test
  public void getTypeFromHashSha1() {
    assertEquals(
        ShaCertificate.getTypeFromHash("1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA"),
        ShaCertificateType.SHA_1);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTypeFromHashSha1WithSpecialCharacter() {
    ShaCertificate.getTypeFromHash("&111AAAA1111AAAA1111AAAA1111AAAA1111AAA$");
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTypeFromHashSha1WithIncorrectSize() {
    ShaCertificate.getTypeFromHash("1111AAAA1111AAAA1111AAAA1111AAAA1111");
  }

  @Test
  public void getTypeFromHashSha256() {
    assertEquals(
        ShaCertificate.getTypeFromHash(
            "1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA"),
        ShaCertificateType.SHA_256);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTypeFromHashSha256WithSpecialCharacter() {
    ShaCertificate.getTypeFromHash(
        "&111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAA&");
  }

  @Test(expected = IllegalArgumentException.class)
  public void getTypeFromHashSha256WithIncorrectSize() {
    ShaCertificate.getTypeFromHash(
        "1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA1111AAAA");
  }
}
