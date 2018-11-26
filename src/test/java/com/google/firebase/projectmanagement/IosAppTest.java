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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class IosAppTest {

  private static final String TEST_APP_NAME =
      "projects/hello-world/iosApps/1:1234567890:ios:cafef00ddeadbeef";
  private static final String TEST_APP_ID = "1:1234567890:ios:cafef00ddeadbeef";
  private static final String TEST_APP_DISPLAY_NAME = "Hello World!";
  private static final String NEW_DISPLAY_NAME = "Hello?";
  private static final String TEST_PROJECT_ID = "hello-world";
  private static final String TEST_APP_BUNDLE_ID = "com.hello.world";
  private static final String TEST_APP_CONFIG =
      "<plist version=\"1.0\"><dict>"
          + "<key>SOME_KEY</key><string>some-value</string>"
          + "<key>SOME_OTHER_KEY</key><string>some-other-value</string>"
          + "</dict></plist>";
  private static final FirebaseProjectManagementException FIREBASE_PROJECT_MANAGEMENT_EXCEPTION =
      new FirebaseProjectManagementException("Error!", null);

  private static final IosAppMetadata TEST_IOS_APP_METADATA = new IosAppMetadata(
      TEST_APP_NAME,
      TEST_APP_ID,
      TEST_APP_DISPLAY_NAME,
      TEST_PROJECT_ID,
      TEST_APP_BUNDLE_ID);

  @Rule
  public final MockitoRule mockitoRule = MockitoJUnit.rule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock(answer = Answers.RETURNS_SMART_NULLS)
  private IosAppService iosAppService;

  private IosApp iosApp;

  @Before
  public void setUp() {
    iosApp = new IosApp(TEST_APP_ID, iosAppService);
  }

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetIosApp() throws Exception {
    assertEquals(TEST_APP_ID, iosApp.getAppId());
  }

  @Test
  public void testGetIosAppShouldSucceed() throws Exception {
    when(iosAppService.getIosApp(TEST_APP_ID)).thenReturn(TEST_IOS_APP_METADATA);

    assertEquals(TEST_IOS_APP_METADATA, iosApp.getMetadata());
  }

  @Test
  public void testGetIosAppShouldRethrow() throws Exception {
    when(iosAppService.getIosApp(TEST_APP_ID)).thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    thrown.expect(FirebaseProjectManagementException.class);

    iosApp.getMetadata();
  }

  @Test
  public void testGetIosAppAsyncShouldSucceed() throws Exception {
    when(iosAppService.getIosAppAsync(TEST_APP_ID))
        .thenReturn(immediateFuture(TEST_IOS_APP_METADATA));

    assertEquals(TEST_IOS_APP_METADATA, iosApp.getMetadataAsync().get());
  }

  @Test
  public void testGetIosAppAsyncShouldRethrow() throws Exception {
    when(iosAppService.getIosAppAsync(TEST_APP_ID))
        .thenReturn(immediateIosAppMetadataFailedFuture());

    try {
      iosApp.getMetadataAsync().get();
      fail("getMetadataAsync did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  @Test
  public void testSetDisplayNameShouldSucceed() throws Exception {
    iosApp.setDisplayName(NEW_DISPLAY_NAME);

    verify(iosAppService).setIosDisplayName(TEST_APP_ID, NEW_DISPLAY_NAME);
  }

  @Test
  public void testSetDisplayNameShouldRethrow() throws Exception {
    doThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION)
        .when(iosAppService)
        .setIosDisplayName(TEST_APP_ID, NEW_DISPLAY_NAME);

    thrown.expect(FirebaseProjectManagementException.class);

    iosApp.setDisplayName(NEW_DISPLAY_NAME);
  }

  @Test
  public void testSetDisplayNameAsyncShouldSucceed() throws Exception {
    when(iosAppService.setIosDisplayNameAsync(TEST_APP_ID, NEW_DISPLAY_NAME))
        .thenReturn(immediateFuture((Void) null));

    iosApp.setDisplayNameAsync(NEW_DISPLAY_NAME).get();
  }

  @Test
  public void testSetDisplayNameAsyncShouldRethrow() throws Exception {
    when(iosAppService.setIosDisplayNameAsync(TEST_APP_ID, NEW_DISPLAY_NAME))
        .thenReturn(immediateVoidFailedFuture());

    try {
      iosApp.setDisplayNameAsync(NEW_DISPLAY_NAME).get();
      fail("setDisplayNameAsync did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  @Test
  public void testGetConfigShouldSucceed() throws Exception {
    when(iosAppService.getIosConfig(TEST_APP_ID)).thenReturn(TEST_APP_CONFIG);

    assertEquals(TEST_APP_CONFIG, iosApp.getConfig());
  }

  @Test
  public void testGetConfigShouldRethrow() throws Exception {
    when(iosAppService.getIosConfig(TEST_APP_ID)).thenThrow(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);

    thrown.expect(FirebaseProjectManagementException.class);

    iosApp.getConfig();
  }

  @Test
  public void testGetConfigAsyncShouldSucceed() throws Exception {
    when(iosAppService.getIosConfigAsync(TEST_APP_ID)).thenReturn(immediateFuture(TEST_APP_CONFIG));

    assertEquals(TEST_APP_CONFIG, iosApp.getConfigAsync().get());
  }

  @Test
  public void testGetConfigAsyncShouldRethrow() throws Exception {
    when(iosAppService.getIosConfigAsync(TEST_APP_ID)).thenReturn(immediateStringFailedFuture());

    try {
      iosApp.getConfigAsync().get();
      fail("getConfigAsync did not rethrow");
    } catch (ExecutionException e) {
      assertTrue(e.getCause() instanceof FirebaseProjectManagementException);
    }
  }

  private ApiFuture<IosAppMetadata> immediateIosAppMetadataFailedFuture() {
    return ApiFutures.<IosAppMetadata>immediateFailedFuture(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);
  }

  private ApiFuture<Void> immediateVoidFailedFuture() {
    return ApiFutures.<Void>immediateFailedFuture(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);
  }

  private ApiFuture<String> immediateStringFailedFuture() {
    return ApiFutures.<String>immediateFailedFuture(FIREBASE_PROJECT_MANAGEMENT_EXCEPTION);
  }
}
