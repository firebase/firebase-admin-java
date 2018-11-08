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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.google.api.core.SettableApiFuture;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AndroidAppTest {

  private static final String APP_NAME = "mock-name";
  private static final String APP_ID = "1:12345:android:deadbeef";
  private static final String APP_DISPLAY_NAME = "test-android-app";
  private static final String NEW_DISPLAY_NAME = "new-test-android-app";
  private static final String APP_PACKAGE_NAME = "abc.def";
  private static final String PROJECT_ID = "test-project-id";
  private static final String ANDROID_CONFIG = "android.config";
  private static final String CERTIFICATE_NAME =
      "projects/test-project/androidApps/1:11111:android:11111/sha/111111";
  private static final String SHA_HASH = "1111111111111111111111111111111111111111";
  private static final AndroidAppMetadata ANDROID_APP_METADATA =
      new AndroidAppMetadata(APP_NAME, APP_ID, APP_DISPLAY_NAME, PROJECT_ID, APP_PACKAGE_NAME);
  private static final ShaCertificate SHA_CERTIFICATE =
      ShaCertificate.create(CERTIFICATE_NAME, SHA_HASH);

  @Rule
  public final MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock(answer = Answers.RETURNS_SMART_NULLS)
  private AndroidAppService androidAppService;

  private AndroidApp androidApp;

  @Before
  public void setUp() {
    androidApp = new AndroidApp(APP_ID, androidAppService);
  }

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetMetadata() throws Exception {
    when(androidAppService.getAndroidApp(APP_ID)).thenReturn(ANDROID_APP_METADATA);

    AndroidAppMetadata metadata = androidApp.getMetadata();

    assertEquals(metadata, ANDROID_APP_METADATA);
  }

  @Test
  public void testGetMetadataAsync() throws Exception {
    when(androidAppService.getAndroidAppAsync(APP_ID))
        .thenReturn(createApiFuture(ANDROID_APP_METADATA));

    AndroidAppMetadata metadata = androidApp.getMetadataAsync().get();

    assertEquals(metadata, ANDROID_APP_METADATA);
  }

  @Test
  public void testSetDisplayName() throws Exception {
    doNothing().when(androidAppService).setAndroidDisplayName(APP_ID, NEW_DISPLAY_NAME);

    androidApp.setDisplayName(NEW_DISPLAY_NAME);

    Mockito.verify(androidAppService).setAndroidDisplayName(APP_ID, NEW_DISPLAY_NAME);
  }

  @Test
  public void testSetDisplayNameAsync() throws Exception {
    when(androidAppService.setAndroidDisplayNameAsync(APP_ID, NEW_DISPLAY_NAME))
        .thenReturn(createApiFuture((Void) null));

    androidApp.setDisplayNameAsync(NEW_DISPLAY_NAME).get();

    Mockito.verify(androidAppService).setAndroidDisplayNameAsync(APP_ID, NEW_DISPLAY_NAME);
  }

  @Test
  public void testGetConfig() throws Exception {
    when(androidAppService.getAndroidConfig(APP_ID)).thenReturn(ANDROID_CONFIG);

    String config = androidApp.getConfig();

    assertEquals(config, ANDROID_CONFIG);
  }

  @Test
  public void testGetConfigAsync() throws Exception {
    when(androidAppService.getAndroidConfigAsync(APP_ID))
        .thenReturn(createApiFuture(ANDROID_CONFIG));

    String config = androidApp.getConfigAsync().get();

    assertEquals(config, ANDROID_CONFIG);
  }

  @Test
  public void testGetShaCertificates() throws Exception {
    List<ShaCertificate> certificateList = new ArrayList<>();
    certificateList.add(SHA_CERTIFICATE);
    when(androidAppService.getShaCertificates(APP_ID)).thenReturn(certificateList);

    List<ShaCertificate> res = androidApp.getShaCertificates();

    assertEquals(res, certificateList);
  }

  @Test
  public void testGetShaCertificatesAsync() throws Exception {
    List<ShaCertificate> certificateList = new ArrayList<>();
    certificateList.add(SHA_CERTIFICATE);
    when(androidAppService.getShaCertificatesAsync(APP_ID))
        .thenReturn(createApiFuture(certificateList));

    List<ShaCertificate> res = androidApp.getShaCertificatesAsync().get();

    assertEquals(res, certificateList);
  }

  @Test
  public void testCreateShaCertificate() throws Exception {
    when(androidAppService.createShaCertificate(APP_ID, ShaCertificate.create(SHA_HASH)))
        .thenReturn(SHA_CERTIFICATE);

    ShaCertificate certificate = androidApp.createShaCertificate(ShaCertificate.create(SHA_HASH));

    assertEquals(certificate, SHA_CERTIFICATE);
  }

  @Test
  public void testCreateShaCertificateAsync() throws Exception {
    when(androidAppService.createShaCertificateAsync(APP_ID, ShaCertificate.create(SHA_HASH)))
        .thenReturn(createApiFuture(SHA_CERTIFICATE));

    ShaCertificate certificate = androidApp
        .createShaCertificateAsync(ShaCertificate.create(SHA_HASH)).get();

    assertEquals(certificate, SHA_CERTIFICATE);
  }

  @Test
  public void testDeleteShaCertificate() throws Exception {
    doNothing().when(androidAppService).deleteShaCertificate(CERTIFICATE_NAME);

    androidApp.deleteShaCertificate(ShaCertificate.create(CERTIFICATE_NAME, SHA_HASH));

    Mockito.verify(androidAppService).deleteShaCertificate(CERTIFICATE_NAME);
  }

  @Test
  public void testDeleteShaCertificateAsync() throws Exception {
    when(androidAppService.deleteShaCertificateAsync(CERTIFICATE_NAME))
        .thenReturn(createApiFuture((Void) null));

    androidApp.deleteShaCertificateAsync(ShaCertificate.create(CERTIFICATE_NAME, SHA_HASH)).get();

    Mockito.verify(androidAppService).deleteShaCertificateAsync(CERTIFICATE_NAME);
  }

  private <T> SettableApiFuture<T> createApiFuture(T value) {
    final SettableApiFuture<T> future = SettableApiFuture.create();
    future.set(value);
    return future;
  }
}
