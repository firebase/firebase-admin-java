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

package com.google.firebase.auth.hash;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class InvalidHashTest {

  private static final byte[] SIGNER_KEY = "key".getBytes();
  private static final byte[] SALT_SEPARATOR = "separator".getBytes();

  @Test
  public void testInvalidHmac() {
    List<Hmac.Builder> builders = ImmutableList.<Hmac.Builder>of(
        HmacSha512.builder(),
        HmacSha256.builder(),
        HmacSha1.builder(),
        HmacMd5.builder()
    );
    for (Hmac.Builder builder : builders) {
      try {
        builder.build();
        fail("No error thrown for missing key");
      } catch (Exception expected) {
        assertTrue(expected instanceof IllegalArgumentException);
      }
    }
  }

  @Test
  public void testInvalidRepeatableHash() {
    List<RepeatableHash.Builder> builders = ImmutableList.<RepeatableHash.Builder>builder()
        .add(Sha512.builder().setRounds(-1))
        .add(Sha256.builder().setRounds(-1))
        .add(Sha1.builder().setRounds(-1))
        .add(Md5.builder().setRounds(-1))
        .add(Pbkdf2Sha256.builder().setRounds(-1))
        .add(PbkdfSha1.builder().setRounds(-1))
        .add(Sha512.builder().setRounds(120001))
        .add(Sha256.builder().setRounds(120001))
        .add(Sha1.builder().setRounds(120001))
        .add(Md5.builder().setRounds(120001))
        .add(Pbkdf2Sha256.builder().setRounds(120001))
        .add(PbkdfSha1.builder().setRounds(120001))
        .build();
    for (RepeatableHash.Builder builder : builders) {
      try {
        builder.build();
        fail("No error thrown for invalid rounds");
      } catch (Exception expected) {
        assertTrue(expected instanceof IllegalArgumentException);
      }
    }
  }

  @Test
  public void testInvalidScrypt() {
    List<Scrypt.Builder> builders = ImmutableList.of(
        Scrypt.builder() // missing signer key
            .setSaltSeparator(SALT_SEPARATOR)
            .setRounds(5)
            .setMemoryCost(12),
        Scrypt.builder() // invalid rounds (> 8)
            .setKey(SIGNER_KEY)
            .setSaltSeparator(SALT_SEPARATOR)
            .setRounds(9)
            .setMemoryCost(14),
        Scrypt.builder() // invalid rounds (< 0)
            .setKey(SIGNER_KEY)
            .setSaltSeparator(SALT_SEPARATOR)
            .setRounds(-1)
            .setMemoryCost(14),
        Scrypt.builder() // invalid memory cost (> 15)
            .setKey(SIGNER_KEY)
            .setSaltSeparator(SALT_SEPARATOR)
            .setRounds(8)
            .setMemoryCost(15)
    );
    for (Scrypt.Builder builder : builders) {
      try {
        builder.build();
        fail("No error thrown for invalid configuration");
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }
}
