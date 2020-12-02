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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.MockGoogleCredentials;

import com.google.firebase.remoteconfig.internal.TemplateResponse;

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
  public void testAppDelete() {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "custom-app");
    FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance(app);
    assertNotNull(remoteConfig);

    app.delete();

    try {
      FirebaseRemoteConfig.getInstance(app);
      fail("No error thrown when getting remote config instance after deleting app");
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

    try {
      FirebaseRemoteConfig.getInstance();
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

  // Get template tests

  @Test
  public void testGetTemplate() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.getTemplate();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.getTemplate();
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testGetTemplateAsync() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.getTemplateAsync().get();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateAsyncFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.getTemplateAsync().get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  // Get template with version number tests

  @Test
  public void testGetTemplateAtVersionWithStringValue() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.getTemplateAtVersion("64");

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateAtVersionWithStringValueFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.getTemplateAtVersion("55");
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testGetTemplateAtVersionAsyncWithStringValue() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.getTemplateAtVersionAsync("55").get();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateAtVersionAsyncWithStringValueFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.getTemplateAtVersionAsync("55").get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testGetTemplateAtVersionWithLongValue() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.getTemplateAtVersion(64L);

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateAtVersionWithLongValueFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.getTemplateAtVersion(55L);
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testGetTemplateAtVersionAsyncWithLongValue() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.getTemplateAtVersionAsync(55L).get();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testGetTemplateAtVersionAsyncWithLongValueFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.getTemplateAtVersionAsync(55L).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  // Validate template tests

  @Test
  public void testValidateTemplate() throws FirebaseRemoteConfigException {
    Template template = new Template().setETag(TEST_ETAG);
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(template);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template validatedTemplate = remoteConfig.validateTemplate(template);

    assertEquals(TEST_ETAG, validatedTemplate.getETag());
  }

  @Test
  public void testValidateTemplateFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.validateTemplate(new Template());
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testValidateTemplateAsync() throws Exception {
    Template template = new Template().setETag(TEST_ETAG);
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(template);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template validatedTemplate = remoteConfig.validateTemplateAsync(template).get();

    assertEquals(TEST_ETAG, validatedTemplate.getETag());
  }

  @Test
  public void testValidateTemplateAsyncFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.validateTemplateAsync(new Template()).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  // Publish template tests

  @Test
  public void testPublishTemplate() throws FirebaseRemoteConfigException {
    Template template = new Template().setETag(TEST_ETAG);
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(template);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template publishedTemplate = remoteConfig.publishTemplate(template);

    assertEquals(TEST_ETAG, publishedTemplate.getETag());
  }

  @Test
  public void testPublishTemplateFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.publishTemplate(new Template());
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testPublishTemplateAsync() throws Exception {
    Template template = new Template().setETag(TEST_ETAG);
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(template);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template publishedTemplate = remoteConfig.publishTemplateAsync(template).get();

    assertEquals(TEST_ETAG, publishedTemplate.getETag());
  }

  @Test
  public void testPublishTemplateAsyncFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.publishTemplateAsync(new Template()).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  // Force publish template tests

  @Test
  public void testForcePublishTemplate() throws FirebaseRemoteConfigException {
    Template template = new Template().setETag(TEST_ETAG);
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(template);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template forcePublishedTemplate = remoteConfig.forcePublishTemplate(template);

    assertEquals(TEST_ETAG, forcePublishedTemplate.getETag());
  }

  @Test
  public void testForcePublishTemplateFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.forcePublishTemplate(new Template());
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testForcePublishTemplateAsync() throws Exception {
    Template template = new Template().setETag(TEST_ETAG);
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(template);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template forcePublishedTemplate = remoteConfig.forcePublishTemplateAsync(template).get();

    assertEquals(TEST_ETAG, forcePublishedTemplate.getETag());
  }

  @Test
  public void testForcePublishTemplateAsyncFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.forcePublishTemplateAsync(new Template()).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  // Rollback template tests

  @Test
  public void testRollbackWithStringValue() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.rollback("64");

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testRollbackWithStringValueFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.rollback("55");
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testRollbackAsyncWithStringValue() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.rollbackAsync("55").get();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testRollbackAsyncWithStringValueFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.rollbackAsync("55").get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testRollbackWithLongValue() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.rollback(64L);

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testRollbackWithLongValueFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.rollback(55L);
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testRollbackAsyncWithLongValue() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromTemplate(
            new Template().setETag(TEST_ETAG));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    Template template = remoteConfig.rollbackAsync(55L).get();

    assertEquals(TEST_ETAG, template.getETag());
  }

  @Test
  public void testRollbackAsyncWithLongValueFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.rollbackAsync(55L).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  // List versions tests

  @Test
  public void testListVersionsWithNoOptions() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromListVersionsResponse(
            new TemplateResponse.ListVersionsResponse().setNextPageToken("token"));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    ListVersionsPage listVersionsPage = remoteConfig.listVersions();

    assertEquals("token", listVersionsPage.getNextPageToken());
  }

  @Test
  public void testListVersionsWithNoOptionsFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.listVersions();
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testListVersionsAsyncWithNoOptions() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromListVersionsResponse(
            new TemplateResponse.ListVersionsResponse().setNextPageToken("token"));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    ListVersionsPage listVersionsPage = remoteConfig.listVersionsAsync().get();

    assertEquals("token", listVersionsPage.getNextPageToken());
  }

  @Test
  public void testListVersionsAsyncWithNoOptionsFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.listVersionsAsync().get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  @Test
  public void testListVersionsWithOptions() throws FirebaseRemoteConfigException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromListVersionsResponse(
            new TemplateResponse.ListVersionsResponse().setNextPageToken("token"));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    ListVersionsPage listVersionsPage = remoteConfig.listVersions(
            ListVersionsOptions.builder().build());

    assertEquals("token", listVersionsPage.getNextPageToken());
  }

  @Test
  public void testListVersionsWithOptionsFailure() {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.listVersions(ListVersionsOptions.builder().build());
    } catch (FirebaseRemoteConfigException e) {
      assertSame(TEST_EXCEPTION, e);
    }
  }

  @Test
  public void testListVersionsAsyncWithOptions() throws Exception {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromListVersionsResponse(
            new TemplateResponse.ListVersionsResponse().setNextPageToken("token"));
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    ListVersionsPage listVersionsPage = remoteConfig.listVersionsAsync(
            ListVersionsOptions.builder().build()).get();

    assertEquals("token", listVersionsPage.getNextPageToken());
  }

  @Test
  public void testListVersionsAsyncWithOptionsFailure() throws InterruptedException {
    MockRemoteConfigClient client = MockRemoteConfigClient.fromException(TEST_EXCEPTION);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    try {
      remoteConfig.listVersionsAsync(ListVersionsOptions.builder().build()).get();
    } catch (ExecutionException e) {
      assertSame(TEST_EXCEPTION, e.getCause());
    }
  }

  private FirebaseRemoteConfig getRemoteConfig(FirebaseRemoteConfigClient client) {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS);
    return new FirebaseRemoteConfig(app, client);
  }
}
