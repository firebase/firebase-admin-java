/*
 * Copyright 2025 Google LLC
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
import static org.junit.Assert.assertThrows;

import com.google.api.core.ApiFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.MockGoogleCredentials;
import com.google.firebase.testing.TestUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.BeforeClass;
import org.junit.Test;

/** Tests 
* for {@link ServerTemplateImpl}. 
* */
public class ServerTemplateImplTest {

  private static final FirebaseOptions TEST_OPTIONS =
      FirebaseOptions.builder()
          .setCredentials(new MockGoogleCredentials("test-token"))
          .setProjectId("test-project")
          .build();

  private static String cacheTemplate;

  @BeforeClass
  public static void setUpClass() {
    cacheTemplate = TestUtils.loadResource("getServerTemplateData.json");
  }

  @Test
  public void testServerTemplateWithoutCacheValueThrowsException()
      throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ServerTemplateImpl.Builder(null).defaultConfig(defaultConfig).build());

    assertEquals("JSON String must not be null or empty.", error.getMessage());
  }

  @Test
  public void testEvaluateWithoutContextReturnsDefaultValue() throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate();

    assertEquals("Default value", evaluatedConfig.getString("Custom"));
  }

  @Test
  public void testEvaluateCustomSignalReturnsDefaultValue() throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("users", "100").build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Custom"));
  }

  @Test
  public void testEvaluateCustomSignalReturnsConditionalValue()
      throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("users", "99").build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Conditional value", evaluatedConfig.getString("Custom"));
  }

  @Test
  public void testEvaluateCustomSignalWithoutContextReturnsDefaultValue()
      throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Custom"));
  }

  @Test
  public void testEvaluateCustomSignalWithInvalidContextReturnsDefaultValue()
      throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("users", "abc").build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Custom"));
  }

  @Test
  public void testEvaluatePercentWithoutRandomizationIdReturnsDefaultValue()
      throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Percent"));
  }

  @Test
  public void testEvaluatePercentReturnsConditionalValue() throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("randomizationId", "user").build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Conditional value", evaluatedConfig.getString("Percent"));
  }

  @Test
  public void testEvaluateWithoutDefaultValueReturnsEmptyString()
      throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("", evaluatedConfig.getString("Unset default value"));
  }

  @Test
  public void testEvaluateWithInvalidCacheValueThrowsException()
      throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    String invalidJsonString = "abc";
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(invalidJsonString)
            .build();

    FirebaseRemoteConfigException error =
        assertThrows(FirebaseRemoteConfigException.class, () -> template.evaluate(context));

    assertEquals(
        "No Remote Config Server template in cache. Call load() before " + "calling evaluate().",
        error.getMessage());
  }

  @Test
  public void testEvaluateWithInAppDefaultReturnsEmptyString() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("", evaluatedConfig.getString("In-app default"));
  }

  @Test
  public void testEvaluateWithDerivedInAppDefaultReturnsDefaultValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Derived in-app default"));
  }

  @Test
  public void testEvaluateWithMultipleConditionReturnsConditionalValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("users", "99").build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Conditional value 1", evaluatedConfig.getString("Multiple conditions"));
  }

  @Test
  public void testEvaluateWithChainedAndConditionReturnsDefaultValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context =
        new KeysAndValues.Builder()
            .put("users", "100")
            .put("premium users", 20)
            .put("randomizationId", "user")
            .build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Chained conditions"));
  }

  @Test
  public void testEvaluateWithChainedAndConditionReturnsConditionalValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context =
        new KeysAndValues.Builder().put("users", "99").put("premium users", "30").build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Conditional value", evaluatedConfig.getString("Chained conditions"));
  }

  @Test
  public void testGetEvaluateConfigOnInvalidTypeReturnsDefaultValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("randomizationId", "user").build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals(0L, evaluatedConfig.getLong("Percent"));
  }

  @Test
  public void testGetEvaluateConfigInvalidKeyReturnsStaticValueSource() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals(ValueSource.STATIC, evaluatedConfig.getValueSource("invalid"));
  }

  @Test
  public void testGetEvaluateConfigInAppDefaultConfigReturnsDefaultValueSource() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().put("In-app default", "abc").build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals(ValueSource.DEFAULT, evaluatedConfig.getValueSource("In-app default"));
  }

  @Test
  public void testGetEvaluateConfigUnsetDefaultConfigReturnsDefaultValueSource() throws Exception {
    KeysAndValues defaultConfig =
        new KeysAndValues.Builder().put("Unset default config", "abc").build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals(ValueSource.DEFAULT, evaluatedConfig.getValueSource("Unset default config"));
  }

  private static final String TEST_ETAG = "etag-123456789012-1";

  private FirebaseRemoteConfig getRemoteConfig(FirebaseRemoteConfigClient client) {
    FirebaseApp app = FirebaseApp.initializeApp(TEST_OPTIONS, "test-app");
    return new FirebaseRemoteConfig(app, client);
  }

  @Test
  public void testLoad() throws Exception {
    // 1. Define the template data that the mock client will return.
    // This is the EXPECTED state after `load()` is called.
    final String expectedTemplateJsonAfterLoad =
        new ServerTemplateData().setETag(TEST_ETAG).toJSON();

    // 2. Mock the HTTP client to return the predefined response.
    MockRemoteConfigClient client =
        MockRemoteConfigClient.fromServerTemplate(expectedTemplateJsonAfterLoad);
    FirebaseRemoteConfig remoteConfig = getRemoteConfig(client);

    // 3. Build the template instance.
    // It's initialized with a complex `cacheTemplate` to ensure `load()` properly
    // overwrites it.
    KeysAndValues defaultConfig =
        new KeysAndValues.Builder().put("Unset default config", "abc").build();
    ServerTemplate template =
        remoteConfig
            .serverTemplateBuilder()
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate) // This is the initial state before `load()`
            .build();

    // 4. Call the load method, which fetches the new template from the mock client.
    ApiFuture<Void> loadFuture = template.load();
    loadFuture.get(); // Wait for the async operation to complete.

    // 5. Get the ACTUAL state of the template after `load()` has executed.
    String actualJsonAfterLoad = template.toJson();

    // 6. Assert that the template's state has been updated to match what the mock
    // client returned.
    // Parsing to JsonElement performs a deep, order-insensitive comparison.
    JsonElement expectedJson = JsonParser.parseString(expectedTemplateJsonAfterLoad);
    JsonElement actualJson = JsonParser.parseString(actualJsonAfterLoad);

    assertEquals(expectedJson, actualJson);
  }

  @Test
  public void testBuilderParsesCachedTemplateCorrectly() throws FirebaseRemoteConfigException {
    // Arrange:
    // 1. Create a canonical JSON string by parsing the input file and then
    // re-serializing it. This gives us the precise expected output format,
    // accounting for any formatting or default value differences.
    ServerTemplateData canonicalData = ServerTemplateData.fromJSON(cacheTemplate);
    String expectedJsonString = canonicalData.toJSON();

    // Act:
    // 2. Build a ServerTemplate instance from the original cached JSON string,
    // which triggers the parsing logic we want to test.
    ServerTemplate template =
        new ServerTemplateImpl.Builder(null).cachedTemplate(cacheTemplate).build();

    // Assert:
    // 3. Compare the JSON from the newly built template against the canonical
    // version.
    // This verifies that the internal state was parsed and stored correctly.
    // Using JsonElement ensures the comparison is not affected by key order.
    JsonElement expectedJsonTree = JsonParser.parseString(expectedJsonString);
    JsonElement actualJsonTree = JsonParser.parseString(template.toJson());

    assertEquals(expectedJsonTree, actualJsonTree);
  }
}
