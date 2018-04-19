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
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.hash.HmacSha256;
import com.google.firebase.auth.hash.HmacSha512;
import com.google.firebase.auth.hash.Scrypt;
import java.util.Map;
import org.junit.Test;

public class UserImportHashTest {

  private static final byte[] SIGNER_KEY = "key".getBytes();
  private static final byte[] SALT_SEPARATOR = "separator".getBytes();

  private static class MockHash extends UserImportHash {
    MockHash() {
      super("MOCK_HASH");
    }

    @Override
    protected Map<String, Object> getOptions() {
      return ImmutableMap.<String, Object>of("key", "value");
    }
  }

  @Test
  public void testBase() {
    UserImportHash hash = new MockHash();
    assertEquals(ImmutableMap.of("hashAlgorithm", "MOCK_HASH", "key", "value"),
        hash.getProperties());
  }

  @Test
  public void testValidScrypt() {
    UserImportHash scrypt = Scrypt.builder()
        .setKey(SIGNER_KEY)
        .setSaltSeparator(SALT_SEPARATOR)
        .setRounds(8)
        .setMemoryCost(14)
        .build();
    Map<String, Object> properties = ImmutableMap.<String, Object>of(
        "hashAlgorithm", "SCRYPT",
        "signerKey", BaseEncoding.base64Url().encode(SIGNER_KEY),
        "saltSeparator", BaseEncoding.base64Url().encode(SALT_SEPARATOR),
        "rounds", 8,
        "memoryCost", 14
    );
    assertEquals(properties, scrypt.getProperties());

    scrypt = Scrypt.builder()
        .setKey(SIGNER_KEY)
        .setRounds(8)
        .setMemoryCost(14)
        .build();
    properties = ImmutableMap.<String, Object>of(
        "hashAlgorithm", "SCRYPT",
        "signerKey", BaseEncoding.base64Url().encode(SIGNER_KEY),
        "saltSeparator", "",
        "rounds", 8,
        "memoryCost", 14
    );
    assertEquals(properties, scrypt.getProperties());
  }

  @Test
  public void testInvalidScrypt() {
    try {
      Scrypt.builder()
          .setSaltSeparator(SALT_SEPARATOR)
          .setRounds(5)
          .setMemoryCost(12)
          .build();
      fail("No error thrown for missing key");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      Scrypt.builder()
          .setKey(SIGNER_KEY)
          .setSaltSeparator(SALT_SEPARATOR)
          .setRounds(9)
          .setMemoryCost(14)
          .build();
      fail("No error thrown for invalid rounds");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      Scrypt.builder()
          .setKey(SIGNER_KEY)
          .setSaltSeparator(SALT_SEPARATOR)
          .setRounds(8)
          .setMemoryCost(15)
          .build();
      fail("No error thrown for invalid memory cost");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testValidHmac() {
    UserImportHash hmac = HmacSha512.builder()
        .setKey(SIGNER_KEY)
        .build();
    Map<String, Object> properties = ImmutableMap.<String, Object>of(
        "hashAlgorithm", "HMAC_SHA512",
        "signerKey", BaseEncoding.base64Url().encode(SIGNER_KEY)
    );
    assertEquals(properties, hmac.getProperties());

    hmac = HmacSha256.builder()
        .setKey(SIGNER_KEY)
        .build();
    properties = ImmutableMap.<String, Object>of(
        "hashAlgorithm", "HMAC_SHA256",
        "signerKey", BaseEncoding.base64Url().encode(SIGNER_KEY)
    );
    assertEquals(properties, hmac.getProperties());
  }

  @Test
  public void testInvalidHmac() {
    try {
      HmacSha512.builder().build();
      fail("No error thrown for missing key");
    } catch (IllegalArgumentException expected) {
      // expected
    }

    try {
      HmacSha256.builder().build();
      fail("No error thrown for missing key");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }
}
