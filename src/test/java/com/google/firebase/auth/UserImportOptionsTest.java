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

package com.google.firebase.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.hash.HmacSha512;
import java.util.Map;
import org.junit.Test;

public class UserImportOptionsTest {

  @Test(expected = NullPointerException.class)
  public void testEmptyOptions() {
    UserImportOptions.builder().build();
  }

  @Test
  public void testHash() {
    HmacSha512 hash = HmacSha512.builder()
        .setKey("key".getBytes())
        .build();
    UserImportOptions options = UserImportOptions.builder()
        .setHash(hash)
        .build();
    Map<String, Object> expected = ImmutableMap.<String, Object>of(
        "hashAlgorithm", "HMAC_SHA512",
        "signerKey", BaseEncoding.base64Url().encode("key".getBytes())
    );
    assertEquals(expected, options.getProperties());
    assertSame(hash, options.getHash());
  }
}
