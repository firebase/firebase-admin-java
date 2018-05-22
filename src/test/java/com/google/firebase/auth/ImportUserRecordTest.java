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

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ImportUserRecordTest {

  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  @Test
  public void testUidOnlyRecord() {
    ImportUserRecord record = ImportUserRecord.builder()
        .setUid("testuid")
        .build();
    assertEquals(ImmutableMap.of("localId", "testuid"), record.getProperties(JSON_FACTORY));
  }

  @Test
  public void testAllProperties() throws IOException {
    Date date = new Date();
    UserProvider provider1 = UserProvider.builder()
        .setUid("testuid")
        .setProviderId("google.com")
        .build();
    UserProvider provider2 = UserProvider.builder()
        .setUid("testuid")
        .setProviderId("test.com")
        .build();
    ImportUserRecord record = ImportUserRecord.builder()
        .setUid("testuid")
        .setEmail("test@example.com")
        .setDisplayName("Test User")
        .setPhotoUrl("https://test.com/user.png")
        .setPhoneNumber("+1234567890")
        .setUserMetadata(new UserMetadata(date.getTime(), date.getTime()))
        .setDisabled(false)
        .setEmailVerified(true)
        .setPasswordHash("password".getBytes())
        .setPasswordSalt("salt".getBytes())
        .addUserProvider(provider1)
        .addAllUserProviders(ImmutableList.of(provider2))
        .putCustomClaim("admin", true)
        .putAllCustomClaims(ImmutableMap.<String, Object>of("package", "gold"))
        .build();

    Map<String, Object> properties = record.getProperties(JSON_FACTORY);

    Map<String, Object> customClaims = new HashMap<>();
    String customAttributes = (String) properties.get("customAttributes");
    JSON_FACTORY.createJsonParser(customAttributes).parse(customClaims);
    assertEquals(ImmutableMap.of("admin", true, "package", "gold"), customClaims);

    Map<String, Object> expected = ImmutableMap.<String, Object>builder()
        .put("localId", "testuid")
        .put("email", "test@example.com")
        .put("displayName", "Test User")
        .put("photoUrl", "https://test.com/user.png")
        .put("phoneNumber", "+1234567890")
        .put("createdAt", date.getTime())
        .put("lastLoginAt", date.getTime())
        .put("disabled", false)
        .put("emailVerified", true)
        .put("passwordHash", BaseEncoding.base64Url().encode("password".getBytes()))
        .put("salt", BaseEncoding.base64Url().encode("salt".getBytes()))
        .put("providerUserInfo", ImmutableList.of(provider1, provider2))
        .put("customAttributes", customAttributes)
        .build();
    assertEquals(expected, properties);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidUid() {
    ImportUserRecord.builder()
        .setUid(Strings.repeat("a", 129))
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidEmail() {
    ImportUserRecord.builder()
        .setUid("test")
        .setEmail("not-an-email")
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidPhotoUrl() {
    ImportUserRecord.builder()
        .setUid("test")
        .setPhotoUrl("not a url")
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidPhoneNumber() {
    ImportUserRecord.builder()
        .setUid("test")
        .setPhoneNumber("not a phone number")
        .build();
  }

  @Test
  public void testNullUserProvider() {
    try {
      ImportUserRecord.builder()
          .setUid("test")
          .addUserProvider(null).build();
      fail("No error thrown for null provider");
    } catch (NullPointerException expected) {
      // expected
    }

    try {
      List<UserProvider> providers = new ArrayList<>();
      providers.add(null);
      ImportUserRecord.builder()
          .setUid("test")
          .addAllUserProviders(providers).build();
      fail("No error thrown for null provider");
    } catch (NullPointerException expected) {
      // expected
    }
  }

  @Test
  public void testNullOrEmptyCustomClaims() {
    try {
      ImportUserRecord.builder()
          .setUid("test")
          .putCustomClaim("foo", null).build();
      fail("No error thrown for null claim value");
    } catch (NullPointerException expected) {
      // expected
    }

    try {
      ImportUserRecord.builder()
          .setUid("test")
          .putCustomClaim(null, "foo").build();
      fail("No error thrown for null claim name");
    } catch (NullPointerException expected) {
      // expected
    }

    try {
      ImportUserRecord.builder()
          .setUid("test")
          .putCustomClaim("", "foo").build();
      fail("No error thrown for empty claim name");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testReservedClaims() {
    for (String key : FirebaseUserManager.RESERVED_CLAIMS) {
      try {
        ImportUserRecord.builder()
            .setUid("test")
            .putCustomClaim(key, "foo").build();
        fail("No error thrown for reserved claim");
      } catch (IllegalArgumentException expected) {
        // expected
      }
    }
  }

  @Test
  public void testLargeCustomClaims() {
    ImportUserRecord user = ImportUserRecord.builder()
        .setUid("test")
        .putCustomClaim("foo", Strings.repeat("a", 1000))
        .build();
    try {
      user.getProperties(JSON_FACTORY);
      fail("No error thrown for large claim value");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }
}
