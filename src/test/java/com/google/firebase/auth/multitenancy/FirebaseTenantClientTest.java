package com.google.firebase.auth.multitenancy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.api.client.googleapis.util.Utils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.internal.SdkUtils;
import com.google.firebase.testing.MultiRequestMockHttpTransport;
import com.google.firebase.testing.TestResponseInterceptor;
import com.google.firebase.testing.TestUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;

public class FirebaseTenantClientTest {

  private static final JsonFactory JSON_FACTORY = Utils.getDefaultJsonFactory();

  private static final String TEST_TOKEN = "token";

  private static final GoogleCredentials credentials = new MockGoogleCredentials(TEST_TOKEN);

  private static final String PROJECT_BASE_URL =
      "https://identitytoolkit.googleapis.com/v2/projects/test-project-id";

  private static final String TENANTS_BASE_URL = PROJECT_BASE_URL + "/tenants";

  @After
  public void tearDown() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
  }

  @Test
  public void testGetTenant() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForTenantManagement(
        TestUtils.loadResource("tenant.json"));

    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().getTenant("TENANT_1");

    checkTenant(tenant, "TENANT_1");
    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "GET", TENANTS_BASE_URL + "/TENANT_1");
  }

  @Test
  public void testGetTenantWithNotFoundError() {
    TestResponseInterceptor interceptor =
        initializeAppForTenantManagementWithStatusCode(404,
            "{\"error\": {\"message\": \"TENANT_NOT_FOUND\"}}");
    try {
      FirebaseAuth.getInstance().getTenantManager().getTenant("UNKNOWN");
      fail("No error thrown for invalid response");
    } catch (FirebaseAuthException e) {
      assertEquals(FirebaseTenantClient.TENANT_NOT_FOUND_ERROR, e.getErrorCode());
    }
    checkUrl(interceptor, "GET", TENANTS_BASE_URL + "/UNKNOWN");
  }

  @Test
  public void testListTenants() throws Exception {
    final TestResponseInterceptor interceptor = initializeAppForTenantManagement(
        TestUtils.loadResource("listTenants.json"));

    ListTenantsPage page = FirebaseAuth.getInstance().getTenantManager().listTenants(null, 99);

    ImmutableList<Tenant> tenants = ImmutableList.copyOf(page.getValues());
    assertEquals(2, tenants.size());
    checkTenant(tenants.get(0), "TENANT_1");
    checkTenant(tenants.get(1), "TENANT_2");
    assertEquals("", page.getNextPageToken());
    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "GET", TENANTS_BASE_URL);
    GenericUrl url = interceptor.getResponse().getRequest().getUrl();
    assertEquals(99, url.getFirst("pageSize"));
    assertNull(url.getFirst("pageToken"));
  }

  @Test
  public void testListTenantsWithPageToken() throws Exception {
    final TestResponseInterceptor interceptor = initializeAppForTenantManagement(
        TestUtils.loadResource("listTenants.json"));

    ListTenantsPage page = FirebaseAuth.getInstance().getTenantManager().listTenants("token", 99);

    ImmutableList<Tenant> tenants = ImmutableList.copyOf(page.getValues());
    assertEquals(2, tenants.size());
    checkTenant(tenants.get(0), "TENANT_1");
    checkTenant(tenants.get(1), "TENANT_2");
    assertEquals("", page.getNextPageToken());
    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "GET", TENANTS_BASE_URL);
    GenericUrl url = interceptor.getResponse().getRequest().getUrl();
    assertEquals(99, url.getFirst("pageSize"));
    assertEquals("token", url.getFirst("pageToken"));
  }

  @Test
  public void testListZeroTenants() throws Exception {
    final TestResponseInterceptor interceptor = initializeAppForTenantManagement("{}");

    ListTenantsPage page = FirebaseAuth.getInstance().getTenantManager().listTenants(null);

    assertTrue(Iterables.isEmpty(page.getValues()));
    assertEquals("", page.getNextPageToken());
    checkRequestHeaders(interceptor);
  }

  @Test
  public void testCreateTenant() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForTenantManagement(
        TestUtils.loadResource("tenant.json"));
    Tenant.CreateRequest request = new Tenant.CreateRequest()
        .setDisplayName("DISPLAY_NAME")
        .setPasswordSignInAllowed(true)
        .setEmailLinkSignInEnabled(false);

    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().createTenant(request);

    checkTenant(tenant, "TENANT_1");
    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "POST", TENANTS_BASE_URL);
    GenericJson parsed = parseRequestContent(interceptor);
    assertEquals("DISPLAY_NAME", parsed.get("displayName"));
    assertEquals(true, parsed.get("allowPasswordSignup"));
    assertEquals(false, parsed.get("enableEmailLinkSignin"));
  }

  @Test
  public void testCreateTenantMinimal() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForTenantManagement(
        TestUtils.loadResource("tenant.json"));
    Tenant.CreateRequest request = new Tenant.CreateRequest();

    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().createTenant(request);

    checkTenant(tenant, "TENANT_1");
    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "POST", TENANTS_BASE_URL);
    GenericJson parsed = parseRequestContent(interceptor);
    assertNull(parsed.get("displayName"));
    assertNull(parsed.get("allowPasswordSignup"));
    assertNull(parsed.get("enableEmailLinkSignin"));
  }

  @Test
  public void testCreateTenantError() {
    TestResponseInterceptor interceptor =
        initializeAppForTenantManagementWithStatusCode(404,
            "{\"error\": {\"message\": \"INTERNAL_ERROR\"}}");
    try {
      FirebaseAuth.getInstance().getTenantManager().createTenant(new Tenant.CreateRequest());
      fail("No error thrown for invalid response");
    } catch (FirebaseAuthException e) {
      assertEquals(FirebaseTenantClient.INTERNAL_ERROR, e.getErrorCode());
    }
    checkUrl(interceptor, "POST", TENANTS_BASE_URL);
  }

  @Test
  public void testUpdateTenant() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForTenantManagement(
        TestUtils.loadResource("tenant.json"));
    Tenant.UpdateRequest request = new Tenant.UpdateRequest("TENANT_1")
        .setDisplayName("DISPLAY_NAME")
        .setPasswordSignInAllowed(true)
        .setEmailLinkSignInEnabled(false);

    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().updateTenant(request);

    checkTenant(tenant, "TENANT_1");
    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "PATCH", TENANTS_BASE_URL + "/TENANT_1");
    GenericUrl url = interceptor.getResponse().getRequest().getUrl();
    assertEquals("allowPasswordSignup,displayName,enableEmailLinkSignin",
        url.getFirst("updateMask"));
    GenericJson parsed = parseRequestContent(interceptor);
    assertEquals("DISPLAY_NAME", parsed.get("displayName"));
    assertEquals(true, parsed.get("allowPasswordSignup"));
    assertEquals(false, parsed.get("enableEmailLinkSignin"));
  }

  @Test
  public void testUpdateTenantMinimal() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForTenantManagement(
        TestUtils.loadResource("tenant.json"));
    Tenant.UpdateRequest request =
        new Tenant.UpdateRequest("TENANT_1").setDisplayName("DISPLAY_NAME");

    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().updateTenant(request);

    checkTenant(tenant, "TENANT_1");
    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "PATCH", TENANTS_BASE_URL + "/TENANT_1");
    GenericUrl url = interceptor.getResponse().getRequest().getUrl();
    assertEquals("displayName", url.getFirst("updateMask"));
    GenericJson parsed = parseRequestContent(interceptor);
    assertEquals("DISPLAY_NAME", parsed.get("displayName"));
    assertNull(parsed.get("allowPasswordSignup"));
    assertNull(parsed.get("enableEmailLinkSignin"));
  }

  @Test
  public void testUpdateTenantNoValues() throws Exception {
    initializeAppForTenantManagement(TestUtils.loadResource("tenant.json"));
    TenantManager tenantManager = FirebaseAuth.getInstance().getTenantManager();
    try {
      tenantManager.updateTenant(new Tenant.UpdateRequest("TENANT_1"));
      fail("No error thrown for empty tenant update");
    } catch (IllegalArgumentException e) {
      // expected
    }
  }

  @Test
  public void testUpdateTenantError() {
    TestResponseInterceptor interceptor =
        initializeAppForTenantManagementWithStatusCode(404,
            "{\"error\": {\"message\": \"INTERNAL_ERROR\"}}");
    Tenant.UpdateRequest request =
        new Tenant.UpdateRequest("TENANT_1").setDisplayName("DISPLAY_NAME");
    try {
      FirebaseAuth.getInstance().getTenantManager().updateTenant(request);
      fail("No error thrown for invalid response");
    } catch (FirebaseAuthException e) {
      assertEquals(FirebaseTenantClient.INTERNAL_ERROR, e.getErrorCode());
    }
    checkUrl(interceptor, "PATCH", TENANTS_BASE_URL + "/TENANT_1");
  }

  @Test
  public void testDeleteTenant() throws Exception {
    TestResponseInterceptor interceptor = initializeAppForTenantManagement("{}");

    FirebaseAuth.getInstance().getTenantManager().deleteTenant("TENANT_1");

    checkRequestHeaders(interceptor);
    checkUrl(interceptor, "DELETE", TENANTS_BASE_URL + "/TENANT_1");
  }

  @Test
  public void testDeleteTenantWithNotFoundError() {
    TestResponseInterceptor interceptor =
        initializeAppForTenantManagementWithStatusCode(404,
            "{\"error\": {\"message\": \"TENANT_NOT_FOUND\"}}");
    try {
      FirebaseAuth.getInstance().getTenantManager().deleteTenant("UNKNOWN");
      fail("No error thrown for invalid response");
    } catch (FirebaseAuthException e) {
      assertEquals(FirebaseTenantClient.TENANT_NOT_FOUND_ERROR, e.getErrorCode());
    }
    checkUrl(interceptor, "DELETE", TENANTS_BASE_URL + "/UNKNOWN");
  }

  private static void checkTenant(Tenant tenant, String tenantId) {
    assertEquals(tenantId, tenant.getTenantId());
    assertEquals("DISPLAY_NAME", tenant.getDisplayName());
    assertTrue(tenant.isPasswordSignInAllowed());
    assertFalse(tenant.isEmailLinkSignInEnabled());
  }

  private static void checkRequestHeaders(TestResponseInterceptor interceptor) {
    HttpHeaders headers = interceptor.getResponse().getRequest().getHeaders();
    String auth = "Bearer " + TEST_TOKEN;
    assertEquals(auth, headers.getFirstHeaderStringValue("Authorization"));

    String clientVersion = "Java/Admin/" + SdkUtils.getVersion();
    assertEquals(clientVersion, headers.getFirstHeaderStringValue("X-Client-Version"));
  }

  private static void checkUrl(TestResponseInterceptor interceptor, String method, String url) {
    HttpRequest request = interceptor.getResponse().getRequest();
    if (method.equals("PATCH")) {
      assertEquals("PATCH",
          request.getHeaders().getFirstHeaderStringValue("X-HTTP-Method-Override"));
      assertEquals("POST", request.getRequestMethod());
    } else {
      assertEquals(method, request.getRequestMethod());
    }
    assertEquals(url, request.getUrl().toString().split("\\?")[0]);
  }

  private static TestResponseInterceptor initializeAppForTenantManagement(String... responses) {
    initializeAppWithResponses(responses);
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseAuth.getInstance().getTenantManager().setInterceptor(interceptor);
    return interceptor;
  }

  private static TestResponseInterceptor initializeAppForTenantManagementWithStatusCode(
      int statusCode, String response) {
    FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .setHttpTransport(
            new MockHttpTransport.Builder()
                .setLowLevelHttpResponse(
                  new MockLowLevelHttpResponse().setContent(response).setStatusCode(statusCode))
                .build())
        .setProjectId("test-project-id")
        .build());
    TestResponseInterceptor interceptor = new TestResponseInterceptor();
    FirebaseAuth.getInstance().getTenantManager().setInterceptor(interceptor);
    return interceptor;
  }

  private static void initializeAppWithResponses(String... responses) {
    List<MockLowLevelHttpResponse> mocks = new ArrayList<>();
    for (String response : responses) {
      mocks.add(new MockLowLevelHttpResponse().setContent(response));
    }
    MockHttpTransport transport = new MultiRequestMockHttpTransport(mocks);
    FirebaseApp.initializeApp(new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .setHttpTransport(transport)
        .setProjectId("test-project-id")
        .build());
  }

  private static GenericJson parseRequestContent(TestResponseInterceptor interceptor)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    interceptor.getResponse().getRequest().getContent().writeTo(out);
    return JSON_FACTORY.fromString(new String(out.toByteArray()), GenericJson.class);
  }
}
