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

import static com.google.api.core.ApiFutures.immediateFuture;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class FirebaseProjectManagementTest {

  private static final String TEST_APP_ID = "1:1234567890:ios:cafef00ddeadbeef";
  private static final String TEST_APP_ID_2 = "1:1234567890:ios:f00ddeadbeefcafe";
  private static final String TEST_APP_ID_3 = "1:1234567890:ios:deadbeefcafef00d";
  private static final String TEST_APP_DISPLAY_NAME = "Hello World!";
  private static final String NULL_DISPLAY_NAME = null;
  private static final String TEST_PROJECT_ID = "hello-world";
  private static final String TEST_APP_BUNDLE_ID = "com.hello.world";
  private static final FirebaseProjectManagementException FIREBASE_PROJECT_MANAGEMENT_EXCEPTION =
      new FirebaseProjectManagementException("Error!", null);

  @Rule
  public final MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock(answer = Answers.RETURNS_SMART_NULLS)
  private AndroidAppService androidAppService;

  @Mock(answer = Answers.RETURNS_SMART_NULLS)
  private IosAppService iosAppService;

  private FirebaseProjectManagement projectManagement;

  @BeforeClass
  public static void setUpClass() {
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId(TEST_PROJECT_ID)
        .build();
    FirebaseApp.initializeApp(options);
  }

  @Before
  public void setUp() {
    projectManagement = FirebaseProjectManagement.getInstance();
    projectManagement.setAndroidAppService(androidAppService);
    projectManagement.setIosAppService(iosAppService);
  }

  @AfterClass
  public static void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  /* Android */

  @Test
  public void testCreateAndroidAppShouldSucceed() throws Exception {
    when(androidAppService.createAndroidApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenReturn(new AndroidApp(TEST_APP_ID, androidAppService));

    AndroidApp androidApp = projectManagement.createAndroidApp(TEST_APP_BUNDLE_ID);

    assertEquals(TEST_APP_ID, androidApp.getAppId());
  }

  @Test
  public void testCreateAndroidAppShouldRethrow() throws Exception {
    when(androidAppService.createAndroidApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    try {
      projectManagement.createAndroidApp(TEST_APP_BUNDLE_ID);
      fail("createAndroidApp did not rethrow");
    } catch (FirebaseProjectManagementException e) {
      // Pass.
    }
  }

  @Test
  public void testCreateAndroidAppWithDisplayNameShouldSucceed() throws Exception {
    when(androidAppService
            .createAndroidApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenReturn(new AndroidApp(TEST_APP_ID, androidAppService));

    AndroidApp androidApp =
        projectManagement.createAndroidApp(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME);

    assertEquals(TEST_APP_ID, androidApp.getAppId());
  }

  @Test
  public void testCreateAndroidAppWithDisplayNameShouldRethrow() throws Exception {
    when(androidAppService
            .createAndroidApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    try {
      projectManagement.createAndroidApp(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME);
      fail("createAndroidApp did not rethrow");
    } catch (FirebaseProjectManagementException e) {
      // Pass.
    }
  }

  @Test
  public void testCreateAndroidAppAsyncShouldSucceed() throws Exception {
    when(androidAppService
            .createAndroidAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenReturn(immediateFuture(new AndroidApp(TEST_APP_ID, androidAppService)));

    AndroidApp androidApp = projectManagement.createAndroidAppAsync(TEST_APP_BUNDLE_ID).get();

    assertEquals(TEST_APP_ID, androidApp.getAppId());
  }

  @Test
  public void testCreateAndroidAppAsyncShouldRethrow() throws Exception {
    when(androidAppService
            .createAndroidAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenReturn(immediateAndroidAppFailedFuture());

    try {
      projectManagement.createAndroidAppAsync(TEST_APP_BUNDLE_ID).get();
      fail("createAndroidAppAsync did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  @Test
  public void testCreateAndroidAppAsyncWithDisplayNameShouldSucceed() throws Exception {
    when(androidAppService
            .createAndroidAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenReturn(immediateFuture(new AndroidApp(TEST_APP_ID, androidAppService)));

    AndroidApp androidApp =
        projectManagement.createAndroidAppAsync(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME).get();

    assertEquals(TEST_APP_ID, androidApp.getAppId());
  }

  @Test
  public void testCreateAndroidAppAsyncWithDisplayNameShouldRethrow() throws Exception {
    when(androidAppService
            .createAndroidAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenReturn(immediateAndroidAppFailedFuture());

    try {
      projectManagement.createAndroidAppAsync(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME).get();
      fail("createAndroidAppAsync (with display name) did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  @Test
  public void testListAndroidAppsShouldSucceed() throws Exception {
    AndroidApp app1 = new AndroidApp(TEST_APP_ID, androidAppService);
    AndroidApp app2 = new AndroidApp(TEST_APP_ID_2, androidAppService);
    AndroidApp app3 = new AndroidApp(TEST_APP_ID_3, androidAppService);
    when(androidAppService.listAndroidApps(TEST_PROJECT_ID))
        .thenReturn(ImmutableList.of(app1, app2, app3));

    List<AndroidApp> androidApps = projectManagement.listAndroidApps();

    ImmutableSet.Builder<String> appIdsBuilder = ImmutableSet.<String>builder();
    for (AndroidApp androidApp : androidApps) {
      appIdsBuilder.add(androidApp.getAppId());
    }
    assertEquals(ImmutableSet.of(TEST_APP_ID, TEST_APP_ID_2, TEST_APP_ID_3), appIdsBuilder.build());
  }

  @Test
  public void testListAndroidAppsShouldRethrow() throws Exception {
    when(androidAppService.listAndroidApps(TEST_PROJECT_ID))
        .thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    try {
      projectManagement.listAndroidApps();
      fail("listAndroidApps did not rethrow");
    } catch (FirebaseProjectManagementException e) {
      // Pass.
    }
  }

  @Test
  public void testListAndroidAppsAsyncShouldSucceed() throws Exception {
    AndroidApp app1 = new AndroidApp(TEST_APP_ID, androidAppService);
    AndroidApp app2 = new AndroidApp(TEST_APP_ID_2, androidAppService);
    AndroidApp app3 = new AndroidApp(TEST_APP_ID_3, androidAppService);
    List<AndroidApp> androidApps = ImmutableList.of(app1, app2, app3);
    when(androidAppService.listAndroidAppsAsync(TEST_PROJECT_ID))
        .thenReturn(immediateFuture(androidApps));

    List<AndroidApp> actualAndroidApps = projectManagement.listAndroidAppsAsync().get();

    ImmutableSet.Builder<String> appIdsBuilder = ImmutableSet.<String>builder();
    for (AndroidApp androidApp : actualAndroidApps) {
      appIdsBuilder.add(androidApp.getAppId());
    }
    assertEquals(ImmutableSet.of(TEST_APP_ID, TEST_APP_ID_2, TEST_APP_ID_3), appIdsBuilder.build());
  }

  @Test
  public void testListAndroidAppsAsyncShouldRethrow() throws Exception {
    when(androidAppService.listAndroidAppsAsync(TEST_PROJECT_ID))
        .thenReturn(immediateListAndroidAppFailedFuture());

    try {
      projectManagement.listAndroidAppsAsync().get();
      fail("listAndroidAppsAsync did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  /* iOS */

  @Test
  public void testCreateIosAppShouldSucceed() throws Exception {
    when(iosAppService.createIosApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenReturn(new IosApp(TEST_APP_ID, iosAppService));

    IosApp iosApp = projectManagement.createIosApp(TEST_APP_BUNDLE_ID);

    assertEquals(TEST_APP_ID, iosApp.getAppId());
  }

  @Test
  public void testCreateIosAppShouldRethrow() throws Exception {
    when(iosAppService.createIosApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    try {
      projectManagement.createIosApp(TEST_APP_BUNDLE_ID);
      fail("createIosApp did not rethrow");
    } catch (FirebaseProjectManagementException e) {
      // Pass.
    }
  }

  @Test
  public void testCreateIosAppWithDisplayNameShouldSucceed() throws Exception {
    when(iosAppService.createIosApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenReturn(new IosApp(TEST_APP_ID, iosAppService));

    IosApp iosApp = projectManagement.createIosApp(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME);

    assertEquals(TEST_APP_ID, iosApp.getAppId());
  }

  @Test
  public void testCreateIosAppWithDisplayNameShouldRethrow() throws Exception {
    when(iosAppService.createIosApp(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    try {
      projectManagement.createIosApp(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME);
      fail("createIosApp did not rethrow");
    } catch (FirebaseProjectManagementException e) {
      // Pass.
    }
  }

  @Test
  public void testCreateIosAppAsyncShouldSucceed() throws Exception {
    when(iosAppService.createIosAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenReturn(immediateFuture(new IosApp(TEST_APP_ID, iosAppService)));

    IosApp iosApp = projectManagement.createIosAppAsync(TEST_APP_BUNDLE_ID).get();

    assertEquals(TEST_APP_ID, iosApp.getAppId());
  }

  @Test
  public void testCreateIosAppAsyncShouldRethrow() throws Exception {
    when(iosAppService.createIosAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, NULL_DISPLAY_NAME))
        .thenReturn(immediateIosAppFailedFuture());

    try {
      projectManagement.createIosAppAsync(TEST_APP_BUNDLE_ID).get();
      fail("createIosAppAsync did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  @Test
  public void testCreateIosAppAsyncWithDisplayNameShouldSucceed() throws Exception {
    when(iosAppService
            .createIosAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenReturn(immediateFuture(new IosApp(TEST_APP_ID, iosAppService)));

    IosApp iosApp =
        projectManagement.createIosAppAsync(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME).get();

    assertEquals(TEST_APP_ID, iosApp.getAppId());
  }

  @Test
  public void testCreateIosAppAsyncWithDisplayNameShouldRethrow() throws Exception {
    when(iosAppService
            .createIosAppAsync(TEST_PROJECT_ID, TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME))
        .thenReturn(immediateIosAppFailedFuture());

    try {
      projectManagement.createIosAppAsync(TEST_APP_BUNDLE_ID, TEST_APP_DISPLAY_NAME).get();
      fail("createIosAppAsync (with display name) did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  @Test
  public void testListIosAppsShouldSucceed() throws Exception {
    IosApp app1 = new IosApp(TEST_APP_ID, iosAppService);
    IosApp app2 = new IosApp(TEST_APP_ID_2, iosAppService);
    IosApp app3 = new IosApp(TEST_APP_ID_3, iosAppService);
    when(iosAppService.listIosApps(TEST_PROJECT_ID)).thenReturn(ImmutableList.of(app1, app2, app3));

    List<IosApp> iosApps = projectManagement.listIosApps();

    ImmutableSet.Builder<String> appIdsBuilder = ImmutableSet.<String>builder();
    for (IosApp iosApp : iosApps) {
      appIdsBuilder.add(iosApp.getAppId());
    }
    assertEquals(ImmutableSet.of(TEST_APP_ID, TEST_APP_ID_2, TEST_APP_ID_3), appIdsBuilder.build());
  }

  @Test
  public void testListIosAppsShouldRethrow() throws Exception {
    when(iosAppService.listIosApps(TEST_PROJECT_ID))
        .thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    try {
      projectManagement.listIosApps();
      fail("listIosApps did not rethrow");
    } catch (FirebaseProjectManagementException e) {
      // Pass.
    }
  }

  @Test
  public void testListIosAppsAsyncShouldSucceed() throws Exception {
    IosApp app1 = new IosApp(TEST_APP_ID, iosAppService);
    IosApp app2 = new IosApp(TEST_APP_ID_2, iosAppService);
    IosApp app3 = new IosApp(TEST_APP_ID_3, iosAppService);
    List<IosApp> iosApps = ImmutableList.of(app1, app2, app3);
    when(iosAppService.listIosAppsAsync(TEST_PROJECT_ID)).thenReturn(immediateFuture(iosApps));

    List<IosApp> actualIosApps = projectManagement.listIosAppsAsync().get();

    ImmutableSet.Builder<String> appIdsBuilder = ImmutableSet.<String>builder();
    for (IosApp iosApp : actualIosApps) {
      appIdsBuilder.add(iosApp.getAppId());
    }
    assertEquals(ImmutableSet.of(TEST_APP_ID, TEST_APP_ID_2, TEST_APP_ID_3), appIdsBuilder.build());
  }

  @Test
  public void testListIosAppsAsyncShouldRethrow() throws Exception {
    when(iosAppService.listIosAppsAsync(TEST_PROJECT_ID))
        .thenReturn(immediateListIosAppFailedFuture());

    try {
      projectManagement.listIosAppsAsync().get();
      fail("listIosAppsAsync did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  private ApiFuture<AndroidApp> immediateAndroidAppFailedFuture() {
    return ApiFutures.<AndroidApp>immediateFailedFuture(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);
  }

  private ApiFuture<IosApp> immediateIosAppFailedFuture() {
    return ApiFutures.<IosApp>immediateFailedFuture(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);
  }

  private ApiFuture<List<AndroidApp>> immediateListAndroidAppFailedFuture() {
    return ApiFutures.<List<AndroidApp>>immediateFailedFuture(
        FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);
  }

  private ApiFuture<List<IosApp>> immediateListIosAppFailedFuture() {
    return ApiFutures.<List<IosApp>>immediateFailedFuture(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);
  }
}
