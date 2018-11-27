/*
 * Copyright 2017 Google Inc.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.testing.auth.oauth2.MockTokenServerTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.core.ApiFuture;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.base.Defaults;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.testing.ServiceAccount;
import com.google.firebase.testing.TestUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class FirebaseAuthTest {

  private static final FirebaseOptions firebaseOptions = FirebaseOptions.builder()
      .setCredentials(createCertificateCredential())
      .build();

  @Before
  public void setup() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseApp.initializeApp(firebaseOptions);
  }

  @After
  public void cleanup() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetInstance() {
    FirebaseAuth defaultAuth = FirebaseAuth.getInstance();
    assertNotNull(defaultAuth);
    assertSame(defaultAuth, FirebaseAuth.getInstance());
    String token = TestOnlyImplFirebaseTrampolines.getToken(FirebaseApp.getInstance(), false);
    Assert.assertTrue(!token.isEmpty());
  }

  @Test
  public void testGetInstanceForApp() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testGetInstanceForApp");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    assertSame(auth, FirebaseAuth.getInstance(app));
    String token = TestOnlyImplFirebaseTrampolines.getToken(app, false);
    Assert.assertTrue(!token.isEmpty());
  }

  @Test
  public void testAppDelete() {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testAppDelete");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    app.delete();
    try {
      FirebaseAuth.getInstance(app);
      fail("No error thrown when getting auth instance after deleting app");
    } catch (IllegalStateException expected) {
      // ignore
    }
  }

  @Test
  public void testInvokeAfterAppDelete() throws Exception {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInvokeAfterAppDelete");
    FirebaseAuth auth = FirebaseAuth.getInstance(app);
    assertNotNull(auth);
    app.delete();

    for (Method method : auth.getClass().getDeclaredMethods()) {
      int modifiers = method.getModifiers();
      if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
        continue;
      }

      List<Object> parameters = new ArrayList<>(method.getParameterTypes().length);
      for (Class<?> parameterType : method.getParameterTypes()) {
        parameters.add(Defaults.defaultValue(parameterType));
      }
      try {
        method.invoke(auth, parameters.toArray());
        fail("No error thrown when invoking auth after deleting app; method: " + method.getName());
      } catch (InvocationTargetException expected) {
        String message = "FirebaseAuth instance is no longer alive. This happens when "
            + "the parent FirebaseApp instance has been deleted.";
        Throwable cause = expected.getCause();
        assertTrue(cause instanceof IllegalStateException);
        assertEquals(message, cause.getMessage());
      }
    }
  }

  @Test
  public void testInitAfterAppDelete() throws ExecutionException, InterruptedException,
      TimeoutException {
    FirebaseApp app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseAuth auth1 = FirebaseAuth.getInstance(app);
    assertNotNull(auth1);
    app.delete();

    app = FirebaseApp.initializeApp(firebaseOptions, "testInitAfterAppDelete");
    FirebaseAuth auth2 = FirebaseAuth.getInstance(app);
    assertNotNull(auth2);
    assertNotSame(auth1, auth2);

    ApiFuture<String> future = auth2.createCustomTokenAsync("foo");
    assertNotNull(future);
    assertNotNull(future.get(TestUtils.TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
  }

  @Test
  public void testAppWithAuthVariableOverrides() {
    // TODO: Move this to somewhere more appropriate
    Map<String, Object> authVariableOverrides = Collections.singletonMap("uid", (Object) "uid1");
    FirebaseOptions options =
        new FirebaseOptions.Builder(firebaseOptions)
            .setDatabaseAuthVariableOverride(authVariableOverrides)
            .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "testGetAppWithUid");
    assertEquals("uid1", app.getOptions().getDatabaseAuthVariableOverride().get("uid"));
    String token = TestOnlyImplFirebaseTrampolines.getToken(app, false);
    Assert.assertTrue(!token.isEmpty());
  }

  @Test
  public void testProjectIdRequired() {
    FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(new MockGoogleCredentials())
        .build();
    FirebaseApp app = FirebaseApp.initializeApp(options, "testProjectIdRequired");
    try {
      FirebaseAuth.getInstance(app);
      fail("Expected exception.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals(
          "Project ID is required to access the auth service. Use a service account credential "
              + "or set the project ID explicitly via FirebaseOptions. Alternatively you can "
              + "also set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.",
          expected.getMessage());
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAuthExceptionNullErrorCode() {
    new FirebaseAuthException(null, "test");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAuthExceptionEmptyErrorCode() {
    new FirebaseAuthException("", "test");
  }

  private static GoogleCredentials createCertificateCredential() {
    final MockTokenServerTransport transport = new MockTokenServerTransport(
        "https://accounts.google.com/o/oauth2/token");
    transport.addServiceAccount(ServiceAccount.EDITOR.getEmail(), "test-token");
    try {
      return ServiceAccountCredentials.fromStream(ServiceAccount.EDITOR.asStream(),
          new HttpTransportFactory() {
            @Override
            public HttpTransport create() {
              return transport;
            }
          });
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize service account credential", e);
    }
  }
}
