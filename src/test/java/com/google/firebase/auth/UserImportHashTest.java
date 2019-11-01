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

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.hash.Bcrypt;
import com.google.firebase.auth.hash.HmacMd5;
import com.google.firebase.auth.hash.HmacSha1;
import com.google.firebase.auth.hash.HmacSha256;
import com.google.firebase.auth.hash.HmacSha512;
import com.google.firebase.auth.hash.Md5;
import com.google.firebase.auth.hash.Pbkdf2Sha256;
import com.google.firebase.auth.hash.PbkdfSha1;
import com.google.firebase.auth.hash.Scrypt;
import com.google.firebase.auth.hash.Sha1;
import com.google.firebase.auth.hash.Sha256;
import com.google.firebase.auth.hash.Sha512;
import com.google.firebase.auth.hash.StandardScrypt;
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
  public void testScryptHash() {
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
  public void testStandardScryptHash() {
    UserImportHash scrypt = StandardScrypt.builder()
        .setBlockSize(1)
        .setParallelization(2)
        .setDerivedKeyLength(3)
        .setMemoryCost(4)
        .build();
    Map<String, Object> properties = ImmutableMap.<String, Object>of(
        "hashAlgorithm", "STANDARD_SCRYPT",
        "blockSize", 1,
        "parallelization", 2,
        "dkLen", 3,
        "memoryCost", 4
    );
    assertEquals(properties, scrypt.getProperties());
  }

  @Test
  public void testHmacHash() {
    Map<String, UserImportHash> hashes = ImmutableMap.<String, UserImportHash>of(
        "HMAC_SHA512", HmacSha512.builder().setKey(SIGNER_KEY).build(),
        "HMAC_SHA256", HmacSha256.builder().setKey(SIGNER_KEY).build(),
        "HMAC_SHA1", HmacSha1.builder().setKey(SIGNER_KEY).build(),
        "HMAC_MD5", HmacMd5.builder().setKey(SIGNER_KEY).build()
    );
    for (Map.Entry<String, UserImportHash> entry : hashes.entrySet()) {
      Map<String, Object> properties = ImmutableMap.<String, Object>of(
          "hashAlgorithm", entry.getKey(),
          "signerKey", BaseEncoding.base64Url().encode(SIGNER_KEY)
      );
      assertEquals(properties, entry.getValue().getProperties());
    }
  }

  @Test
  public void testBasicHash() {
    Map<String, UserImportHash> hashes = ImmutableMap.<String, UserImportHash>builder()
        .put("SHA512", Sha512.builder().setRounds(42).build())
        .put("SHA256", Sha256.builder().setRounds(42).build())
        .put("SHA1", Sha1.builder().setRounds(42).build())
        .put("MD5", Md5.builder().setRounds(42).build())
        .put("PBKDF2_SHA256", Pbkdf2Sha256.builder().setRounds(42).build())
        .put("PBKDF_SHA1", PbkdfSha1.builder().setRounds(42).build())
        .build();
    for (Map.Entry<String, UserImportHash> entry : hashes.entrySet()) {
      Map<String, Object> properties = ImmutableMap.<String, Object>of(
          "hashAlgorithm", entry.getKey(),
          "rounds", 42
      );
      assertEquals(properties, entry.getValue().getProperties());
    }
  }

  @Test
  public void testBcryptHash() {
    UserImportHash bcrypt = Bcrypt.getInstance();
    Map<String, Object> properties = ImmutableMap.<String, Object>of("hashAlgorithm", "BCRYPT");
    assertEquals(properties, bcrypt.getProperties());
  }
}
