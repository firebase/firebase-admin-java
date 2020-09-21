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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Test;

public class FirebaseRemoteConfigTest {
  private static final FirebaseOptions TEST_OPTIONS = FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials("test-token"))
          .setProjectId("test-project")
          .build();
  private static final FirebaseRemoteConfigException TEST_EXCEPTION =
          new FirebaseRemoteConfigException(ErrorCode.INTERNAL, "Test error message");

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseApp.initializeApp(TEST_OPTIONS);

    FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();

    assertSame(remoteConfig, FirebaseRemoteConfig.getInstance());
  }

  @Test
  public void testGetInstanceByApp() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");

    FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance(app);

    assertSame(remoteConfig, FirebaseRemoteConfig.getInstance(app));
  }

  @Test
  public void testDefaultRemoteConfigClient() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");
    FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance(app);

    FirebaseRemoteConfigClient client = remoteConfig.getRemoteConfigClient();

    assertTrue(client instanceof FirebaseRemoteConfigClientImpl);
    assertSame(client, remoteConfig.getRemoteConfigClient());
    String expectedUrl = "https://firebaseremoteconfig.googleapis.com/v1/projects/test-project/remoteConfig";
    assertEquals(expectedUrl, ((FirebaseRemoteConfigClientImpl) client).getRemoteConfigUrl());
  }

  @Test
  public void testPostDeleteApp() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");

    app.delete();

    try {
      FirebaseRemoteConfig.getInstance(app);
      fail("No error thrown for deleted app");
    } catch (IllegalStateException expected) {
      // expected
    }
  }

  @Test
  public void testRemoteConfigClientWithoutProjectId() {
    FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(new MockGoogleCredentials("test-token"))
            .build();
    FirebaseApp.initializeApp(options);
    FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();

    try {
      remoteConfig.getRemoteConfigClient();
      fail("No error thrown for missing project ID");
    } catch (IllegalArgumentException expected) {
      String message = "Project ID is required to access Remote Config service. Use a service "
              + "account credential or set the project ID explicitly via FirebaseOptions. "
              + "Alternatively you can also set the project ID via the GOOGLE_CLOUD_PROJECT "
              + "environment variable.";
      assertEquals(message, expected.getMessage());
    }
  }

  private static final String TEST_ETAG = "etag-123456789012-1";

  @Test
  public void testGetTemplate() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new RemoteConfigTemplate().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(Suppliers.ofInstance(client));

    RemoteConfigTemplate template = remoteConfig.getTemplate();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(Suppliers.ofInstance(client));

    try {
      remoteConfig.getTemplate();
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testGetTemplateAsync() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new RemoteConfigTemplate().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(Suppliers.ofInstance(client));

    RemoteConfigTemplate template = remoteConfig.getTemplateAsync().get();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateAsyncFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(Suppliers.ofInstance(client));

    try {
      remoteConfig.getTemplateAsync().get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  private FirebaseRemoteConfig getRemoteConfig(
          Supplier<? extends FirebaseRemoteConfigClient> supplier) {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);
    return FirebaseRemoteConfig.builder()
            .setFirebaseApp(app)
            .setRemoteConfigClient(supplier)
            .build();
  }
}
