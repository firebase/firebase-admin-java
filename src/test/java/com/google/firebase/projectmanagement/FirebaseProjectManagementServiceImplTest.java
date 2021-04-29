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

import static com.google.firebase.projectmanagement.FirebaseProjectManagementServiceImpl.FIREBASE_PROJECT_MANAGEMENT_URL;
import static com.google.firebase.projectmanagement.FirebaseProjectManagementServiceImpl.MAXIMUM_LIST_APPS_PAGE_SIZE;
import static com.google.firebase.projectmanagement.ShaCertificateType.SHA_1;
import static com.google.firebase.projectmanagement.ShaCertificateType.SHA_256;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseInterceptor;
import com.google.api.client.json.JsonParser;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.client.testing.util.MockSleeper;
import com.google.api.client.util.Base64;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.ApiClientUtils;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.internal.TestApiClientUtils;
import com.google.firebase.testing.MultiRequestMockHttpTransport;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

// TODO(weixifan): Add unit tests for create{Android|Ios}App.
public class FirebaseProjectManagementServiceImplTest {

  private static final String PROJECT_ID = "test-project-id";
  private static final String BUNDLE_ID = "test.ios.app";
  private static final String PACKAGE_NAME = "test.android.app";
  private static final String DISPLAY_NAME = "test-admin-sdk-app";
  private static final String ANDROID_APP_ID = "test-android-app-id";
  private static final String IOS_APP_ID = "test-ios-app-id";
  private static final String IOS_APP_RESOURCE_NAME = "ios/11111";
  private static final String ANDROID_APP_RESOURCE_NAME = "android/11111";
  private static final String CLIENT_VERSION = "Java/Admin/" + SdkUtils.getVersion();
  private static final IosAppMetadata IOS_APP_METADATA =
      new IosAppMetadata(IOS_APP_RESOURCE_NAME, IOS_APP_ID, DISPLAY_NAME, PROJECT_ID, BUNDLE_ID);
  private static final AndroidAppMetadata ANDROID_APP_METADATA =
      new AndroidAppMetadata(
          ANDROID_APP_RESOURCE_NAME, ANDROID_APP_ID, DISPLAY_NAME, PROJECT_ID, PACKAGE_NAME);
  private static final IosAppMetadata IOS_APP_NO_DISPLAY_NAME_METADATA =
      new IosAppMetadata(IOS_APP_RESOURCE_NAME, IOS_APP_ID, null, PROJECT_ID, BUNDLE_ID);
  private static final AndroidAppMetadata ANDROID_APP_NO_DISPLAY_NAME_METADATA =
      new AndroidAppMetadata(
          ANDROID_APP_RESOURCE_NAME, ANDROID_APP_ID, null, PROJECT_ID, PACKAGE_NAME);

  private static final String IOS_CONFIG_CONTENT = "ios-config-content";
  private static final String ANDROID_CONFIG_CONTENT = "android-config-content";
  private static final String SHA1_RESOURCE_NAME = "test-project/sha/11111";
  private static final String SHA1_HASH = "1111111111111111111111111111111111111111";
  private static final String SHA256_HASH =
      "2222222222222222222222222222222222222222222222222222222222222222";

  private static final String CREATE_IOS_RESPONSE =
      "{\"name\" : \"operations/projects/test-project-id/apps/SomeToken\", "
          + "\"done\" : \"false\"}";
  private static final String CREATE_IOS_GET_OPERATION_ATTEMPT_1_RESPONSE =
      "{\"name\" : \"operations/projects/test-project-id/apps/SomeToken\", "
          + "\"done\" : \"false\"}";
  private static final String CREATE_IOS_GET_OPERATION_ATTEMPT_2_RESPONSE =
      "{\"name\" : \"operations/projects/test-project-id/apps/SomeToken\", "
          + "\"done\" : \"true\", "
          + "\"response\" :"
          + "{\"name\" : \"test-project/sha/11111\", "
          + "\"appId\" : \"test-ios-app-id\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"bundleId\" : \"test.ios.app1\"}}";

  private static final String GET_IOS_RESPONSE =
      "{\"name\" : \"ios/11111\", "
          + "\"appId\" : \"test-ios-app-id\", "
          + "\"displayName\" : \"%s\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"bundleId\" : \"test.ios.app\"}";
  private static final String GET_IOS_NO_DISPLAY_NAME_RESPONSE =
      "{\"name\" : \"ios/11111\", "
          + "\"appId\" : \"test-ios-app-id\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"bundleId\" : \"test.ios.app\"}";
  private static final String GET_IOS_CONFIG_RESPONSE =
      "{\"configFilename\" : \"test-ios-app-config-name\", "
          + "\"configFileContents\" : \"ios-config-content\"}";
  private static final String LIST_IOS_APPS_RESPONSE =
      "{\"apps\": ["
          + "{\"name\" : \"test-project/sha/11111\", "
          + "\"appId\" : \"test-ios-app-id-1\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"bundleId\" : \"test.ios.app1\"}, "
          + "{\"name\" : \"test-project/sha/11112\", "
          + "\"appId\" : \"test-ios-app-id-2\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"bundleId\" : \"test.ios.app2\"}]}";

  private static final String LIST_IOS_APPS_PAGE_1_RESPONSE =
      "{\"apps\": ["
          + "{\"name\" : \"projects/test-project-id/iosApps/test-ios-app-id-1\", "
          + "\"appId\" : \"test-ios-app-id-1\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"bundleId\" : \"test.ios.app1\"}], "
          + "\"nextPageToken\" : \"next-page-token\"}";
  private static final String LIST_IOS_APPS_PAGE_2_RESPONSE =
      "{\"apps\": ["
          + "{\"name\" : \"projects/test-project-id/iosApps/test-ios-app-id-2\", "
          + "\"appId\" : \"test-ios-app-id-2\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"bundleId\" : \"test.ios.app2\"}]}";

  private static final String CREATE_ANDROID_RESPONSE =
      "{\"name\" : \"operations/projects/test-project-id/apps/SomeToken\", "
          + "\"done\" : \"false\"}";
  private static final String CREATE_ANDROID_GET_OPERATION_ATTEMPT_1_RESPONSE =
      "{\"name\" : \"operations/projects/test-project-id/apps/SomeToken\", "
          + "\"done\" : \"false\"}";
  private static final String CREATE_ANDROID_GET_OPERATION_ATTEMPT_2_RESPONSE =
      "{\"name\" : \"operations/projects/test-project-id/apps/SomeToken\", "
          + "\"done\" : \"true\", "
          + "\"response\" :"
          + "{\"name\" : \"test-project/sha/11111\", "
          + "\"appId\" : \"test-android-app-id\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"packageName\" : \"test.android.app1\"}}";

  private static final String GET_ANDROID_RESPONSE =
      "{\"name\" : \"android/11111\", "
          + "\"appId\" : \"test-android-app-id\", "
          + "\"displayName\" : \"%s\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"packageName\" : \"test.android.app\"}";
  private static final String GET_ANDROID_NO_DISPLAY_NAME_RESPONSE =
      "{\"name\" : \"android/11111\", "
          + "\"appId\" : \"test-android-app-id\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"packageName\" : \"test.android.app\"}";
  private static final String GET_ANDROID_CONFIG_RESPONSE =
      "{\"configFilename\" : \"test-android-app-config-name\", "
          + "\"configFileContents\" : \"android-config-content\"}";
  private static final String LIST_ANDROID_APPS_RESPONSE =
      "{\"apps\": ["
          + "{\"name\" : \"test-project/sha/11111\", "
          + "\"appId\" : \"test-android-app-id-1\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"packageName\" : \"test.android.app1\"}, "
          + "{\"name\" : \"test-project/sha/11112\", "
          + "\"appId\" : \"test-android-app-id-2\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"packageName\" : \"test.android.app2\"}]}";

  private static final String LIST_ANDROID_APPS_PAGE_1_RESPONSE =
      "{\"apps\": ["
          + "{\"name\" : \"projects/test-project-id/androidApps/test-android-app-id-1\", "
          + "\"appId\" : \"test-android-app-id-1\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"packageName\" : \"test.android.app1\"}], "
          + "\"nextPageToken\" : \"next-page-token\"}";
  private static final String LIST_ANDROID_APPS_PAGE_2_RESPONSE =
      "{\"apps\": ["
          + "{\"name\" : \"projects/test-project-id/androidApps/test-android-app-id-2\", "
          + "\"appId\" : \"test-android-app-id-2\", "
          + "\"displayName\" : \"display-name\", "
          + "\"projectId\" : \"test-project-id\", "
          + "\"packageName\" : \"test.android.app2\"}]}";

  private static final String GET_SHA_CERTIFICATES_RESPONSE =
      "{\"certificates\": ["
          + "{\"name\" : \"test-project/sha/11111\", "
          + "\"shaHash\" : \"1111111111111111111111111111111111111111\", "
          + "\"certType\" : \"SHA_1\"}, "
          + "{\"name\" : \"test-project/sha/11111\", "
          + "\"shaHash\" : \"2222222222222222222222222222222222222222222222222222222222222222\", "
          + "\"certType\" : \"SHA_256\"}]}";
  private static final String CREATE_SHA_CERTIFICATE_RESPONSE =
          "{\"name\" : \"test-project/sha/11111\", "
          + "\"shaHash\" : \"%s\", "
          + "\"certType\" : \"%s\"}";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private FirebaseProjectManagementServiceImpl serviceImpl;
  private MockLowLevelHttpResponse firstRpcResponse;
  private MultiRequestTestResponseInterceptor interceptor;

  @Before
  public void setUp() {
    interceptor = new MultiRequestTestResponseInterceptor();
    firstRpcResponse = new MockLowLevelHttpResponse();
  }

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void getIosApp() throws Exception {
    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, IOS_APP_ID);
    firstRpcResponse.setContent(String.format(GET_IOS_RESPONSE, DISPLAY_NAME));
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    IosAppMetadata iosAppMetadata = serviceImpl.getIosApp(IOS_APP_ID);

    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(iosAppMetadata, IOS_APP_METADATA);
  }

  @Test
  public void getIosAppAsync() throws Exception {
    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, IOS_APP_ID);
    firstRpcResponse.setContent(String.format(GET_IOS_RESPONSE, DISPLAY_NAME));
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    IosAppMetadata iosAppMetadata = serviceImpl.getIosAppAsync(IOS_APP_ID).get();

    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(iosAppMetadata, IOS_APP_METADATA);
  }

  @Test
  public void getIosAppHttpError() {
    firstRpcResponse.setStatusCode(500);
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    try {
      serviceImpl.getIosApp(IOS_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals(
          "App ID \"test-ios-app-id\": Unexpected HTTP response with status: 500\n{}",
          e.getMessage());
      assertNotNull(e.getCause());
      assertNotNull(e.getHttpResponse());
    }
  }

  @Test
  public void getIosAppHttpErrorWithCode() {
    firstRpcResponse.setStatusCode(500);
    firstRpcResponse.setContent(
        "{\"error\": {\"status\":\"NOT_FOUND\", \"message\":\"Test error\"}}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    try {
      serviceImpl.getIosApp(IOS_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
      assertEquals("App ID \"test-ios-app-id\": Test error", e.getMessage());
      assertNotNull(e.getCause());
      assertNotNull(e.getHttpResponse());
    }
  }

  @Test
  public void getIosAppParseError() {
    firstRpcResponse.setContent("not json");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    try {
      serviceImpl.getIosApp(IOS_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertTrue(e.getMessage().startsWith(
          "App ID \"test-ios-app-id\": Error while parsing HTTP response"));
      assertNotNull(e.getCause());
      assertNotNull(e.getHttpResponse());
    }
  }

  @Test
  public void getIosAppTransportError() {
    FirebaseProjectManagementServiceImpl serviceImpl = initServiceImplWithFaultyTransport();

    try {
      serviceImpl.getIosApp(IOS_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals(
          "App ID \"test-ios-app-id\": Unknown error while making a remote service call: "
              + "transport error",
          e.getMessage());
      assertNotNull(e.getCause());
      assertNull(e.getHttpResponse());
    }
  }

  @Test
  public void getIosAppNoDisplayName() throws Exception {
    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, IOS_APP_ID);
    firstRpcResponse.setContent(GET_IOS_NO_DISPLAY_NAME_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    IosAppMetadata iosAppMetadata = serviceImpl.getIosApp(IOS_APP_ID);

    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(iosAppMetadata, IOS_APP_NO_DISPLAY_NAME_METADATA);
  }

  @Test
  public void getIosAppAsyncNoDisplayName() throws Exception {
    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, IOS_APP_ID);
    firstRpcResponse.setContent(GET_IOS_NO_DISPLAY_NAME_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    IosAppMetadata iosAppMetadata = serviceImpl.getIosAppAsync(IOS_APP_ID).get();

    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(iosAppMetadata, IOS_APP_NO_DISPLAY_NAME_METADATA);
  }

  @Test
  public void listIosApps() throws Exception {
    String expectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    firstRpcResponse.setContent(LIST_IOS_APPS_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    List<IosApp> iosAppList = serviceImpl.listIosApps(PROJECT_ID);

    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(iosAppList.size(), 2);
    assertEquals(iosAppList.get(0).getAppId(), "test-ios-app-id-1");
    assertEquals(iosAppList.get(1).getAppId(), "test-ios-app-id-2");
  }

  @Test
  public void listIosAppsAsync() throws Exception {
    String expectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    firstRpcResponse.setContent(LIST_IOS_APPS_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    List<IosApp> iosAppList = serviceImpl.listIosAppsAsync(PROJECT_ID).get();

    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(iosAppList.size(), 2);
    assertEquals(iosAppList.get(0).getAppId(), "test-ios-app-id-1");
    assertEquals(iosAppList.get(1).getAppId(), "test-ios-app-id-2");
  }

  @Test
  public void listIosAppsMultiplePages() throws Exception {
    firstRpcResponse.setContent(LIST_IOS_APPS_PAGE_1_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(LIST_IOS_APPS_PAGE_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.of(firstRpcResponse, secondRpcResponse),
        interceptor);

    List<IosApp> iosAppList = serviceImpl.listIosApps(PROJECT_ID);

    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    String secondRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps?page_token=next-page-token&page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    assertEquals(iosAppList.size(), 2);
    assertEquals(iosAppList.get(0).getAppId(), "test-ios-app-id-1");
    assertEquals(iosAppList.get(1).getAppId(), "test-ios-app-id-2");
  }

  @Test
  public void listIosAppsAsyncMultiplePages() throws Exception {
    firstRpcResponse.setContent(LIST_IOS_APPS_PAGE_1_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(LIST_IOS_APPS_PAGE_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.of(firstRpcResponse, secondRpcResponse),
        interceptor);

    List<IosApp> iosAppList = serviceImpl.listIosAppsAsync(PROJECT_ID).get();

    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    String secondRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps?page_token=next-page-token&page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    assertEquals(iosAppList.size(), 2);
    assertEquals(iosAppList.get(0).getAppId(), "test-ios-app-id-1");
    assertEquals(iosAppList.get(1).getAppId(), "test-ios-app-id-2");
  }

  @Test
  public void createIosApp() throws Exception {
    firstRpcResponse.setContent(CREATE_IOS_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(CREATE_IOS_GET_OPERATION_ATTEMPT_1_RESPONSE);
    MockLowLevelHttpResponse thirdRpcResponse = new MockLowLevelHttpResponse();
    thirdRpcResponse.setContent(CREATE_IOS_GET_OPERATION_ATTEMPT_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.of(
            firstRpcResponse, secondRpcResponse, thirdRpcResponse),
        interceptor);

    IosApp iosApp = serviceImpl.createIosApp(PROJECT_ID, BUNDLE_ID, DISPLAY_NAME);

    assertEquals(IOS_APP_ID, iosApp.getAppId());
    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps", FIREBASE_PROJECT_MANAGEMENT_URL, PROJECT_ID);
    String secondRpcExpectedUrl = String.format(
        "%s/v1/operations/projects/test-project-id/apps/SomeToken",
        FIREBASE_PROJECT_MANAGEMENT_URL);
    String thirdRpcExpectedUrl = secondRpcExpectedUrl;
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.POST);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(2, thirdRpcExpectedUrl, HttpMethod.GET);
    ImmutableMap<String, String> firstRpcPayload = ImmutableMap.<String, String>builder()
        .put("bundle_id", BUNDLE_ID)
        .put("display_name", DISPLAY_NAME)
        .build();
    checkRequestPayload(0, firstRpcPayload);
  }

  @Test
  public void createIosAppAsync() throws Exception {
    firstRpcResponse.setContent(CREATE_IOS_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(CREATE_IOS_GET_OPERATION_ATTEMPT_1_RESPONSE);
    MockLowLevelHttpResponse thirdRpcResponse = new MockLowLevelHttpResponse();
    thirdRpcResponse.setContent(CREATE_IOS_GET_OPERATION_ATTEMPT_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.<MockLowLevelHttpResponse>of(
            firstRpcResponse, secondRpcResponse, thirdRpcResponse),
        interceptor);

    IosApp iosApp = serviceImpl.createIosAppAsync(PROJECT_ID, BUNDLE_ID, DISPLAY_NAME).get();

    assertEquals(IOS_APP_ID, iosApp.getAppId());
    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/iosApps", FIREBASE_PROJECT_MANAGEMENT_URL, PROJECT_ID);
    String secondRpcExpectedUrl = String.format(
        "%s/v1/operations/projects/test-project-id/apps/SomeToken",
        FIREBASE_PROJECT_MANAGEMENT_URL);
    String thirdRpcExpectedUrl = secondRpcExpectedUrl;
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.POST);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(2, thirdRpcExpectedUrl, HttpMethod.GET);
    ImmutableMap<String, String> firstRpcPayload = ImmutableMap.<String, String>builder()
        .put("bundle_id", BUNDLE_ID)
        .put("display_name", DISPLAY_NAME)
        .build();
    checkRequestPayload(0, firstRpcPayload);
  }

  @Test
  public void setIosDisplayName() throws Exception {
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    serviceImpl.setIosDisplayName(IOS_APP_ID, DISPLAY_NAME);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s?update_mask=display_name",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        IOS_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.PATCH);
    ImmutableMap<String, String> payload =
        ImmutableMap.<String, String>builder().put("display_name", DISPLAY_NAME).build();
    checkRequestPayload(payload);
  }

  @Test
  public void setIosDisplayNameAsync() throws Exception {
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    serviceImpl.setIosDisplayNameAsync(IOS_APP_ID, DISPLAY_NAME).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s?update_mask=display_name",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        IOS_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.PATCH);
    ImmutableMap<String, String> payload =
        ImmutableMap.<String, String>builder().put("display_name", DISPLAY_NAME).build();
    checkRequestPayload(payload);
  }

  @Test
  public void getIosConfig() throws Exception {
    firstRpcResponse.setContent(GET_IOS_CONFIG_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    String content = serviceImpl.getIosConfig(IOS_APP_ID);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s/config", FIREBASE_PROJECT_MANAGEMENT_URL, IOS_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(new String(Base64.decodeBase64(IOS_CONFIG_CONTENT), Charsets.UTF_8), content);
  }

  @Test
  public void getIosConfigAsync() throws Exception {
    firstRpcResponse.setContent(GET_IOS_CONFIG_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    String content = serviceImpl.getIosConfigAsync(IOS_APP_ID).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/iosApps/%s/config", FIREBASE_PROJECT_MANAGEMENT_URL, IOS_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(new String(Base64.decodeBase64(IOS_CONFIG_CONTENT), Charsets.UTF_8), content);
  }

  @Test
  public void getAndroidApp() throws Exception {
    firstRpcResponse.setContent(String.format(GET_ANDROID_RESPONSE, DISPLAY_NAME));
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    AndroidAppMetadata androidAppMetadata = serviceImpl.getAndroidApp(ANDROID_APP_ID);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(androidAppMetadata, ANDROID_APP_METADATA);
  }

  @Test
  public void getAndroidAppAsync() throws Exception {
    firstRpcResponse.setContent(String.format(GET_ANDROID_RESPONSE, DISPLAY_NAME));
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    AndroidAppMetadata androidAppMetadata = serviceImpl.getAndroidAppAsync(ANDROID_APP_ID).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(androidAppMetadata, ANDROID_APP_METADATA);
  }

  @Test
  public void getAndroidAppHttpError() {
    firstRpcResponse.setStatusCode(500);
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    try {
      serviceImpl.getAndroidApp(ANDROID_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.INTERNAL, e.getErrorCode());
      assertEquals(
          "App ID \"test-android-app-id\": Unexpected HTTP response with status: 500\n{}",
          e.getMessage());
      assertNotNull(e.getCause());
      assertNotNull(e.getHttpResponse());
    }
  }

  @Test
  public void getAndroidAppHttpErrorWithCode() {
    firstRpcResponse.setStatusCode(500);
    firstRpcResponse.setContent(
        "{\"error\": {\"status\":\"NOT_FOUND\", \"message\":\"Test error\"}}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    try {
      serviceImpl.getAndroidApp(ANDROID_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
      assertEquals("App ID \"test-android-app-id\": Test error", e.getMessage());
      assertNotNull(e.getCause());
      assertNotNull(e.getHttpResponse());
    }
  }

  @Test
  public void getAndroidAppParseError() {
    firstRpcResponse.setContent("not json");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    try {
      serviceImpl.getAndroidApp(ANDROID_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertTrue(e.getMessage().startsWith(
          "App ID \"test-android-app-id\": Error while parsing HTTP response"));
      assertNotNull(e.getCause());
      assertNotNull(e.getHttpResponse());
    }
  }

  @Test
  public void getAndroidAppTransportError() {
    FirebaseProjectManagementServiceImpl serviceImpl = initServiceImplWithFaultyTransport();

    try {
      serviceImpl.getAndroidApp(ANDROID_APP_ID);
      fail("No exception thrown for HTTP error");
    } catch (FirebaseProjectManagementException e) {
      assertEquals(ErrorCode.UNKNOWN, e.getErrorCode());
      assertEquals(
          "App ID \"test-android-app-id\": Unknown error while making a remote service call: "
              + "transport error",
          e.getMessage());
      assertNotNull(e.getCause());
      assertNull(e.getHttpResponse());
    }
  }

  @Test
  public void getAndroidAppNoDisplayName() throws Exception {
    firstRpcResponse.setContent(GET_ANDROID_NO_DISPLAY_NAME_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    AndroidAppMetadata androidAppMetadata = serviceImpl.getAndroidApp(ANDROID_APP_ID);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(androidAppMetadata, ANDROID_APP_NO_DISPLAY_NAME_METADATA);
  }

  @Test
  public void getAndroidAppAsyncNoDisplayName() throws Exception {
    firstRpcResponse.setContent(GET_ANDROID_NO_DISPLAY_NAME_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    AndroidAppMetadata androidAppMetadata = serviceImpl.getAndroidAppAsync(ANDROID_APP_ID).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s", FIREBASE_PROJECT_MANAGEMENT_URL, ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(androidAppMetadata, ANDROID_APP_NO_DISPLAY_NAME_METADATA);
  }

  @Test
  public void listAndroidApps() throws Exception {
    firstRpcResponse.setContent(LIST_ANDROID_APPS_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    List<AndroidApp> androidAppList = serviceImpl.listAndroidApps(PROJECT_ID);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(androidAppList.size(), 2);
    assertEquals(androidAppList.get(0).getAppId(), "test-android-app-id-1");
    assertEquals(androidAppList.get(1).getAppId(), "test-android-app-id-2");
  }

  @Test
  public void listAndroidAppsAsync() throws Exception {
    firstRpcResponse.setContent(LIST_ANDROID_APPS_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    List<AndroidApp> androidAppList = serviceImpl.listAndroidAppsAsync(PROJECT_ID).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(androidAppList.size(), 2);
    assertEquals(androidAppList.get(0).getAppId(), "test-android-app-id-1");
    assertEquals(androidAppList.get(1).getAppId(), "test-android-app-id-2");
  }

  @Test
  public void listAndroidAppsMultiplePages() throws Exception {
    firstRpcResponse.setContent(LIST_ANDROID_APPS_PAGE_1_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(LIST_ANDROID_APPS_PAGE_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.of(firstRpcResponse, secondRpcResponse),
        interceptor);

    List<AndroidApp> androidAppList = serviceImpl.listAndroidApps(PROJECT_ID);

    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    String secondRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps?page_token=next-page-token&page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    assertEquals(androidAppList.size(), 2);
    assertEquals(androidAppList.get(0).getAppId(), "test-android-app-id-1");
    assertEquals(androidAppList.get(1).getAppId(), "test-android-app-id-2");
  }

  @Test
  public void listAndroidAppsAsyncMultiplePages() throws Exception {
    firstRpcResponse.setContent(LIST_ANDROID_APPS_PAGE_1_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(LIST_ANDROID_APPS_PAGE_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.of(firstRpcResponse, secondRpcResponse),
        interceptor);

    List<AndroidApp> androidAppList = serviceImpl.listAndroidAppsAsync(PROJECT_ID).get();

    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps?page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    String secondRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps?page_token=next-page-token&page_size=%d",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        PROJECT_ID,
        MAXIMUM_LIST_APPS_PAGE_SIZE);
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    assertEquals(androidAppList.size(), 2);
    assertEquals(androidAppList.get(0).getAppId(), "test-android-app-id-1");
    assertEquals(androidAppList.get(1).getAppId(), "test-android-app-id-2");
  }

  @Test
  public void createAndroidApp() throws Exception {
    firstRpcResponse.setContent(CREATE_ANDROID_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(CREATE_ANDROID_GET_OPERATION_ATTEMPT_1_RESPONSE);
    MockLowLevelHttpResponse thirdRpcResponse = new MockLowLevelHttpResponse();
    thirdRpcResponse.setContent(CREATE_ANDROID_GET_OPERATION_ATTEMPT_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.of(
            firstRpcResponse, secondRpcResponse, thirdRpcResponse),
        interceptor);

    AndroidApp androidApp =
        serviceImpl.createAndroidApp(PROJECT_ID, PACKAGE_NAME, DISPLAY_NAME);

    assertEquals(ANDROID_APP_ID, androidApp.getAppId());
    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps", FIREBASE_PROJECT_MANAGEMENT_URL, PROJECT_ID);
    String secondRpcExpectedUrl = String.format(
        "%s/v1/operations/projects/test-project-id/apps/SomeToken",
        FIREBASE_PROJECT_MANAGEMENT_URL);
    String thirdRpcExpectedUrl = secondRpcExpectedUrl;
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.POST);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(2, thirdRpcExpectedUrl, HttpMethod.GET);
    ImmutableMap<String, String> firstRpcPayload = ImmutableMap.<String, String>builder()
        .put("package_name", PACKAGE_NAME)
        .put("display_name", DISPLAY_NAME)
        .build();
    checkRequestPayload(0, firstRpcPayload);
  }

  @Test
  public void createAndroidAppAsync() throws Exception {
    firstRpcResponse.setContent(CREATE_ANDROID_RESPONSE);
    MockLowLevelHttpResponse secondRpcResponse = new MockLowLevelHttpResponse();
    secondRpcResponse.setContent(CREATE_ANDROID_GET_OPERATION_ATTEMPT_1_RESPONSE);
    MockLowLevelHttpResponse thirdRpcResponse = new MockLowLevelHttpResponse();
    thirdRpcResponse.setContent(CREATE_ANDROID_GET_OPERATION_ATTEMPT_2_RESPONSE);
    serviceImpl = initServiceImpl(
        ImmutableList.of(
            firstRpcResponse, secondRpcResponse, thirdRpcResponse),
        interceptor);

    AndroidApp androidApp =
        serviceImpl.createAndroidAppAsync(PROJECT_ID, PACKAGE_NAME, DISPLAY_NAME).get();

    assertEquals(ANDROID_APP_ID, androidApp.getAppId());
    String firstRpcExpectedUrl = String.format(
        "%s/v1beta1/projects/%s/androidApps", FIREBASE_PROJECT_MANAGEMENT_URL, PROJECT_ID);
    String secondRpcExpectedUrl = String.format(
        "%s/v1/operations/projects/test-project-id/apps/SomeToken",
        FIREBASE_PROJECT_MANAGEMENT_URL);
    String thirdRpcExpectedUrl = secondRpcExpectedUrl;
    checkRequestHeader(0, firstRpcExpectedUrl, HttpMethod.POST);
    checkRequestHeader(1, secondRpcExpectedUrl, HttpMethod.GET);
    checkRequestHeader(2, thirdRpcExpectedUrl, HttpMethod.GET);
    ImmutableMap<String, String> firstRpcPayload = ImmutableMap.<String, String>builder()
        .put("package_name", PACKAGE_NAME)
        .put("display_name", DISPLAY_NAME)
        .build();
    checkRequestPayload(0, firstRpcPayload);
  }

  @Test
  public void setAndroidDisplayName() throws Exception {
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    serviceImpl.setAndroidDisplayName(ANDROID_APP_ID, DISPLAY_NAME);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s?update_mask=display_name",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.PATCH);
    ImmutableMap<String, String> payload =
        ImmutableMap.<String, String>builder().put("display_name", DISPLAY_NAME).build();
    checkRequestPayload(payload);
  }

  @Test
  public void setAndroidDisplayNameAsync() throws Exception {
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    serviceImpl.setAndroidDisplayNameAsync(ANDROID_APP_ID, DISPLAY_NAME).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s?update_mask=display_name",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.PATCH);
    ImmutableMap<String, String> payload =
        ImmutableMap.<String, String>builder().put("display_name", DISPLAY_NAME).build();
    checkRequestPayload(payload);
  }

  @Test
  public void getAndroidConfig() throws Exception {
    firstRpcResponse.setContent(GET_ANDROID_CONFIG_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    String content = serviceImpl.getAndroidConfig(ANDROID_APP_ID);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s/config",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(new String(Base64.decodeBase64(ANDROID_CONFIG_CONTENT), Charsets.UTF_8), content);
  }

  @Test
  public void getAndroidConfigAsync() throws Exception {
    firstRpcResponse.setContent(GET_ANDROID_CONFIG_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    String content = serviceImpl.getAndroidConfigAsync(ANDROID_APP_ID).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s/config",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(new String(Base64.decodeBase64(ANDROID_CONFIG_CONTENT), Charsets.UTF_8), content);
  }

  @Test
  public void getShaCertificates() throws Exception {
    firstRpcResponse.setContent(GET_SHA_CERTIFICATES_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    List<ShaCertificate> certificateList = serviceImpl.getShaCertificates(ANDROID_APP_ID);

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s/sha",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(certificateList.size(), 2);
    assertEquals(
        certificateList.get(0),
        ShaCertificate.create("test-project/sha/11111", SHA1_HASH));
    assertEquals(
        certificateList.get(1),
        ShaCertificate.create("test-project/sha/11111", SHA256_HASH));
  }

  @Test
  public void getShaCertificatesAsync() throws Exception {
    firstRpcResponse.setContent(GET_SHA_CERTIFICATES_RESPONSE);
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    List<ShaCertificate> certificateList =
        serviceImpl.getShaCertificatesAsync(ANDROID_APP_ID).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s/sha",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.GET);
    assertEquals(certificateList.size(), 2);
    assertEquals(
        certificateList.get(0),
        ShaCertificate.create("test-project/sha/11111", SHA1_HASH));
    assertEquals(
        certificateList.get(1),
        ShaCertificate.create("test-project/sha/11111", SHA256_HASH));
  }

  @Test
  public void createShaCertificate() throws Exception {
    firstRpcResponse.setContent(
        String.format(CREATE_SHA_CERTIFICATE_RESPONSE, SHA1_HASH, SHA_1.name()));
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    ShaCertificate certificate = serviceImpl
        .createShaCertificate(ANDROID_APP_ID, ShaCertificate.create(SHA1_HASH));

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s/sha",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.POST);
    ImmutableMap<String, String> payload = ImmutableMap.<String, String>builder()
        .put("sha_hash", SHA1_HASH)
        .put("cert_type", SHA_1.toString())
        .build();
    checkRequestPayload(payload);
    assertEquals(certificate, ShaCertificate.create("test-project/sha/11111", SHA1_HASH));
  }

  @Test
  public void createShaCertificateAsync() throws Exception {
    firstRpcResponse
        .setContent(String.format(CREATE_SHA_CERTIFICATE_RESPONSE, SHA256_HASH, SHA_256.name()));
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    ShaCertificate certificate = serviceImpl
        .createShaCertificateAsync(ANDROID_APP_ID, ShaCertificate.create(SHA256_HASH)).get();

    String expectedUrl = String.format(
        "%s/v1beta1/projects/-/androidApps/%s/sha",
        FIREBASE_PROJECT_MANAGEMENT_URL,
        ANDROID_APP_ID);
    checkRequestHeader(expectedUrl, HttpMethod.POST);
    ImmutableMap<String, String> payload = ImmutableMap.<String, String>builder()
        .put("sha_hash", SHA256_HASH)
        .put("cert_type", SHA_256.toString())
        .build();
    checkRequestPayload(payload);
    assertEquals(ShaCertificate.create("test-project/sha/11111", SHA256_HASH), certificate);
  }

  @Test
  public void deleteShaCertificate() throws Exception {
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    serviceImpl.deleteShaCertificate(SHA1_RESOURCE_NAME);

    String expectedUrl = String.format(
        "%s/v1beta1/%s", FIREBASE_PROJECT_MANAGEMENT_URL, SHA1_RESOURCE_NAME);
    checkRequestHeader(expectedUrl, HttpMethod.DELETE);
  }

  @Test
  public void deleteShaCertificateAsync() throws Exception {
    firstRpcResponse.setContent("{}");
    serviceImpl = initServiceImpl(firstRpcResponse, interceptor);

    serviceImpl.deleteShaCertificateAsync(SHA1_RESOURCE_NAME).get();

    String expectedUrl = String.format(
        "%s/v1beta1/%s", FIREBASE_PROJECT_MANAGEMENT_URL, SHA1_RESOURCE_NAME);
    checkRequestHeader(expectedUrl, HttpMethod.DELETE);
  }

  @Test
  public void testAuthAndRetriesSupport() throws Exception {
    List<MockLowLevelHttpResponse> mockResponses = ImmutableList.of(
        new MockLowLevelHttpResponse().setContent("{}"));
    MockHttpTransport transport = new MultiRequestMockHttpTransport(mockResponses);
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId(PROJECT_ID)
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);

    FirebaseProjectManagementServiceImpl serviceImpl =
        new FirebaseProjectManagementServiceImpl(app);
    serviceImpl.setInterceptor(interceptor);

    serviceImpl.deleteShaCertificate(SHA1_RESOURCE_NAME);

    assertEquals(1, interceptor.getNumberOfResponses());
    TestApiClientUtils.assertAuthAndRetrySupport(interceptor.getResponse(0).getRequest());
  }

  @Test
  public void testHttpRetries() throws Exception {
    List<MockLowLevelHttpResponse> mockResponses = ImmutableList.of(
        firstRpcResponse.setStatusCode(503).setContent("{}"),
        new MockLowLevelHttpResponse().setContent("{}"));
    MockHttpTransport transport = new MultiRequestMockHttpTransport(mockResponses);
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId(PROJECT_ID)
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    HttpRequestFactory requestFactory = TestApiClientUtils.delayBypassedRequestFactory(app);
    FirebaseProjectManagementServiceImpl serviceImpl = new FirebaseProjectManagementServiceImpl(
        app, new MockSleeper(), new MockScheduler(), requestFactory);
    serviceImpl.setInterceptor(interceptor);

    serviceImpl.deleteShaCertificate(SHA1_RESOURCE_NAME);

    String expectedUrl = String.format(
        "%s/v1beta1/%s", FIREBASE_PROJECT_MANAGEMENT_URL, SHA1_RESOURCE_NAME);
    checkRequestHeader(expectedUrl, HttpMethod.DELETE);
  }

  private static FirebaseProjectManagementServiceImpl initServiceImpl(
      MockLowLevelHttpResponse mockResponse,
      MultiRequestTestResponseInterceptor interceptor) {
    return initServiceImpl(ImmutableList.of(mockResponse), interceptor);
  }

  private static FirebaseProjectManagementServiceImpl initServiceImpl(
      List<MockLowLevelHttpResponse> mockResponses,
      MultiRequestTestResponseInterceptor interceptor) {
    MockHttpTransport transport = new MultiRequestMockHttpTransport(mockResponses);
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId(PROJECT_ID)
        .setHttpTransport(transport)
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    HttpRequestFactory requestFactory = TestApiClientUtils.retryDisabledRequestFactory(app);
    FirebaseProjectManagementServiceImpl serviceImpl = new FirebaseProjectManagementServiceImpl(
        app, new MockSleeper(), new MockScheduler(), requestFactory);
    serviceImpl.setInterceptor(interceptor);
    return serviceImpl;
  }

  private static FirebaseProjectManagementServiceImpl initServiceImplWithFaultyTransport() {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials("test-token"))
        .setProjectId(PROJECT_ID)
        .setHttpTransport(TestUtils.createFaultyHttpTransport())
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options);
    return new FirebaseProjectManagementServiceImpl(app);
  }

  private void checkRequestHeader(String expectedUrl, HttpMethod httpMethod) {
    assertEquals(
        "The number of HttpResponses is not equal to 1.", 1, interceptor.getNumberOfResponses());
    checkRequestHeader(0, expectedUrl, httpMethod);
  }

  private void checkRequestHeader(int index, String expectedUrl, HttpMethod httpMethod) {
    assertNotNull(interceptor.getResponse(index));
    HttpRequest request = interceptor.getResponse(index).getRequest();
    assertEquals(httpMethod.name(), request.getRequestMethod());
    assertEquals(expectedUrl, request.getUrl().toString());
    assertEquals("Bearer test-token", request.getHeaders().getAuthorization());
    assertEquals(CLIENT_VERSION, request.getHeaders().get("X-Client-Version"));
  }

  private void checkRequestPayload(Map<String, String> expected) throws IOException {
    assertEquals(
        "The number of HttpResponses is not equal to 1.", 1, interceptor.getNumberOfResponses());
    checkRequestPayload(0, expected);
  }

  private void checkRequestPayload(int index, Map<String, String> expected) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    interceptor.getResponse(index).getRequest().getContent().writeTo(out);
    JsonParser parser = ApiClientUtils.getDefaultJsonFactory().createJsonParser(out.toString());
    Map<String, String> parsed = new HashMap<>();
    parser.parseAndClose(parsed);
    assertEquals(expected, parsed);
  }

  private enum HttpMethod {
    GET,
    POST,
    DELETE,
    PATCH,
  }

  /**
   * Can be used to intercept multiple HTTP requests and responses made by the SDK during tests.
   */
  private static class MultiRequestTestResponseInterceptor implements HttpResponseInterceptor {
    private final List<HttpResponse> responsesList = new ArrayList<>();

    @Override
    public void interceptResponse(HttpResponse response) throws IOException {
      this.responsesList.add(response);
    }

    public int getNumberOfResponses() {
      return responsesList.size();
    }

    public HttpResponse getResponse(int index) {
      return responsesList.get(index);
    }
  }

  private static class MockScheduler implements Scheduler {
    @Override
    public void schedule(Runnable runnable, long delayMillis) {
      runnable.run();
    }
  }
}
