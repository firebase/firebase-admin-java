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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Strings;
import com.google.firebase.testing.IntegrationTestUtils;
import java.util.List;
import java.util.Random;
import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseProjectManagementIT {

  private static final String TEST_APP_DISPLAY_NAME_PREFIX =
      "Created By Firebase AdminSDK Java Integration Testing";
  private static final String TEST_APP_BUNDLE_ID = "com.firebase.adminsdk-java-integration-test";
  private static final String TEST_APP_PACKAGE_NAME = "com.firebase.adminsdk_java_integration_test";
  private static final String TEST_SHA1_CERTIFICATE = "1111111111111111111111111111111111111111";
  private static final String TEST_SHA256_CERTIFICATE =
      "AAAACCCCAAAACCCCAAAACCCCAAAACCCCAAAACCCCAAAACCCCAAAACCCCAAAACCCC";
  private static final Random random = new Random();

  private static String testIosAppId;
  private static String testAndroidAppId;

  @BeforeClass
  public static void setUpClass() throws Exception {
    IntegrationTestUtils.ensureDefaultApp();
    FirebaseProjectManagement projectManagement = FirebaseProjectManagement.getInstance();
    // Ensure that we have created a Test iOS App.
    List<IosApp> iosApps = projectManagement.listIosApps();
    for (IosApp iosApp : iosApps) {
      if (iosApp.getMetadata().getDisplayName().startsWith(TEST_APP_DISPLAY_NAME_PREFIX)) {
        testIosAppId = iosApp.getAppId();
      }
    }
    if (Strings.isNullOrEmpty(testIosAppId)) {
      IosApp iosApp =
          projectManagement.createIosApp(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME_PREFIX);
      testIosAppId = iosApp.getAppId();
    }
    // Ensure that we have created a Test Android App.
    List<AndroidApp> androidApps = projectManagement.listAndroidApps();
    for (AndroidApp androidApp : androidApps) {
      if (androidApp.getMetadata().getDisplayName().startsWith(TEST_APP_DISPLAY_NAME_PREFIX)) {
        testAndroidAppId = androidApp.getAppId();
      }
    }
    if (Strings.isNullOrEmpty(testAndroidAppId)) {
      AndroidApp androidApp =
          projectManagement.createAndroidApp(TEST_APP_PACKAGE_NAME, TEST_APP_DISPLAY_NAME_PREFIX);
      testAndroidAppId = androidApp.getAppId();
    }
  }

  /* Android App Integration Tests */

  @Test
  public void testAndroidSetDisplayNameAndGetMetadata() throws Exception {
    FirebaseProjectManagement projectManagement = FirebaseProjectManagement.getInstance();
    AndroidApp androidApp = projectManagement.getAndroidApp(testAndroidAppId);

    // Use the synchronous version of the API.
    {
      int randomInt = Math.abs(random.nextInt());
      String newDisplayName = TEST_APP_DISPLAY_NAME_PREFIX + " helloworld " + randomInt;

      androidApp.setDisplayName(newDisplayName);
      AndroidAppMetadata metadata = androidApp.getMetadata();

      assertEquals(
          String.format(
              "projects/%s/androidApps/%s", IntegrationTestUtils.getProjectId(), testAndroidAppId),
          metadata.getName());
      assertEquals(IntegrationTestUtils.getProjectId(), metadata.getProjectId());
      assertEquals(testAndroidAppId, metadata.getAppId());
      assertEquals(newDisplayName, metadata.getDisplayName());
      assertEquals(TEST_APP_PACKAGE_NAME, metadata.getPackageName());
    }

    // Change the display name back when done. Use the asynchronous version.
    {
      androidApp.setDisplayNameAsync(TEST_APP_DISPLAY_NAME_PREFIX).get();
      AndroidAppMetadata metadata = androidApp.getMetadataAsync().get();

      assertEquals(
          String.format(
              "projects/%s/androidApps/%s", IntegrationTestUtils.getProjectId(), testAndroidAppId),
          metadata.getName());
      assertEquals(IntegrationTestUtils.getProjectId(), metadata.getProjectId());
      assertEquals(testAndroidAppId, metadata.getAppId());
      assertEquals(TEST_APP_DISPLAY_NAME_PREFIX, metadata.getDisplayName());
      assertEquals(TEST_APP_PACKAGE_NAME, metadata.getPackageName());
    }
  }

  @Test
  public void testAndroidCertificates() throws Exception {
    FirebaseProjectManagement projectManagement = FirebaseProjectManagement.getInstance();
    AndroidApp androidApp = projectManagement.getAndroidApp(testAndroidAppId);

    // Use the Synchronous version of the API.
    {
      // Add SHA-1 certificate.
      androidApp.createShaCertificate(ShaCertificate.create(TEST_SHA1_CERTIFICATE));
      List<ShaCertificate> certificates = androidApp.getShaCertificates();
      ShaCertificate expectedCertificate = null;
      for (ShaCertificate certificate : certificates) {
        if (certificate.getShaHash().equals(TEST_SHA1_CERTIFICATE.toLowerCase())) {
          expectedCertificate  = certificate;
        }
      }
      assertNotNull(expectedCertificate);
      assertEquals(expectedCertificate.getCertType(), ShaCertificateType.SHA_1);

      // Delete SHA-1 certificate.
      androidApp.deleteShaCertificate(expectedCertificate);
      for (ShaCertificate certificate : androidApp.getShaCertificates()) {
        if (certificate.getShaHash().equals(TEST_SHA1_CERTIFICATE)) {
          fail("Test SHA-1 certificate is not deleted.");
        }
      }
    }

    // Use the asynchronous version of the API.
    {
      // Add SHA-256 certificate.
      androidApp.createShaCertificateAsync(ShaCertificate.create(TEST_SHA256_CERTIFICATE)).get();
      List<ShaCertificate> certificates = androidApp.getShaCertificatesAsync().get();
      ShaCertificate expectedCertificate = null;
      for (ShaCertificate certificate : certificates) {
        if (certificate.getShaHash().equals(TEST_SHA256_CERTIFICATE.toLowerCase())) {
          expectedCertificate  = certificate;
        }
      }
      assertNotNull(expectedCertificate);
      assertEquals(expectedCertificate.getCertType(), ShaCertificateType.SHA_256);

      // Delete SHA-256 certificate.
      androidApp.deleteShaCertificateAsync(expectedCertificate).get();
      for (ShaCertificate certificate : androidApp.getShaCertificatesAsync().get()) {
        if (certificate.getShaHash().equals(TEST_SHA256_CERTIFICATE)) {
          fail("Test SHA-1 certificate is not deleted.");
        }
      }
    }
  }

  @Test
  public void testAndroidGetConfig() throws Exception {
    FirebaseProjectManagement projectManagement = FirebaseProjectManagement.getInstance();
    AndroidApp androidApp = projectManagement.getAndroidApp(testAndroidAppId);

    // Test the synchronous version.
    {
      String config = androidApp.getConfig();

      assertTrue(config.contains(IntegrationTestUtils.getProjectId()));
      assertTrue(config.contains(testAndroidAppId));
    }

    // Test the asynchronous version.
    {
      String config = androidApp.getConfigAsync().get();

      assertTrue(config.contains(IntegrationTestUtils.getProjectId()));
      assertTrue(config.contains(testAndroidAppId));
    }
  }

  /* iOS App Integration Tests */

  @Test
  public void testIosSetDisplayNameAndGetMetadata() throws Exception {
    FirebaseProjectManagement projectManagement = FirebaseProjectManagement.getInstance();
    IosApp iosApp = projectManagement.getIosApp(testIosAppId);

    // Use the synchronous version of the API.
    {
      int randomInt = Math.abs(random.nextInt());
      String newDisplayName = TEST_APP_DISPLAY_NAME_PREFIX + " helloworld " + randomInt;

      iosApp.setDisplayName(newDisplayName);
      IosAppMetadata metadata = iosApp.getMetadata();

      assertEquals(
          String.format(
              "projects/%s/iosApps/%s", IntegrationTestUtils.getProjectId(), testIosAppId),
          metadata.getName());
      assertEquals(IntegrationTestUtils.getProjectId(), metadata.getProjectId());
      assertEquals(testIosAppId, metadata.getAppId());
      assertEquals(newDisplayName, metadata.getDisplayName());
      assertEquals(TEST_APP_BUNDLE_ID, metadata.getBundleId());
    }

    // Change the display name back when done. Use the asynchronous version.
    {
      iosApp.setDisplayNameAsync(TEST_APP_DISPLAY_NAME_PREFIX).get();
      IosAppMetadata metadata = iosApp.getMetadataAsync().get();

      assertEquals(
          String.format(
              "projects/%s/iosApps/%s", IntegrationTestUtils.getProjectId(), testIosAppId),
          metadata.getName());
      assertEquals(IntegrationTestUtils.getProjectId(), metadata.getProjectId());
      assertEquals(testIosAppId, metadata.getAppId());
      assertEquals(TEST_APP_DISPLAY_NAME_PREFIX, metadata.getDisplayName());
      assertEquals(TEST_APP_BUNDLE_ID, metadata.getBundleId());
    }
  }

  @Test
  public void testIosGetConfig() throws Exception {
    FirebaseProjectManagement projectManagement = FirebaseProjectManagement.getInstance();
    IosApp iosApp = projectManagement.getIosApp(testIosAppId);

    // Test the synchronous version.
    {
      String config = iosApp.getConfig();

      assertTrue(config.contains(IntegrationTestUtils.getProjectId()));
      assertTrue(config.contains(testIosAppId));
    }

    // Test the asynchronous version.
    {
      String config = iosApp.getConfigAsync().get();

      assertTrue(config.contains(IntegrationTestUtils.getProjectId()));
      assertTrue(config.contains(testIosAppId));
    }
  }
}
