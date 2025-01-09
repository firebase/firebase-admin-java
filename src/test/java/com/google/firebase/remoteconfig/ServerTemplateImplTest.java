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

import com.google.firebase.testing.TestUtils;

import org.junit.BeforeClass;
import org.junit.Test;

public class ServerTemplateImplTest {

  private static String cacheTemplate;

  @BeforeClass
  public static void setUpClass() {
    cacheTemplate = TestUtils.loadResource("getServerTemplateData.json");
  }

  @Test
  public void testEvaluateCustomSignalReturnsDefaultValue() throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("users", "100").build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null).defaultConfig(defaultConfig)
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
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
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
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
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
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
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
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Percent"));
  }

  @Test
  public void testEvaluatePercentReturnsConditionalValue() throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder()
            .put("randomizationId", "user")
            .build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Conditional value", evaluatedConfig.getString("Percent"));
  }

  @Test(expected = NullPointerException.class)
  public void testEvaluateWithoutDefaultValueOmitsConfigValue()
    throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);
    evaluatedConfig.getString("Unset default value");
  }

  @Test
  public void testEvaluateWithoutDefaultValueReturnsDefaultConfigValue()
    throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder()
            .put("Unset default value", "Default config value")
            .build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default config value", evaluatedConfig.getString("Unset default value"));
  }

  @Test(expected = FirebaseRemoteConfigException.class)
  public void testEvaluateWithInvalidCacheValueThrowsException() 
    throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    String invalidJsonString = "abc";
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(invalidJsonString)
            .build();

    template.evaluate(context);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testEvaluateWithoutCacheValueThrowsException() throws FirebaseRemoteConfigException {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .build();

    template.evaluate(context);
  }

  @Test(expected = NullPointerException.class)
  public void testEvaluateWithInAppDefaultOmitsConfigValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);
    evaluatedConfig.getString("In-app default");
  }

  @Test(expected = NullPointerException.class)
  public void testEvaluateWithDerivedInAppDefaultOmitsConfigValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);
    evaluatedConfig.getString("Derived in-app default");
  }

  @Test
  public void testEvaluateWithMultipleConditionReturnsConditionalValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder().put("users", "99").build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();

    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Conditional value 1", evaluatedConfig.getString("Multiple conditions"));
  }

  @Test
  public void testEvaluateWithChainedAndConditionReturnsDefaultValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder()
            .put("users", "100")
            .put("randomizationId", "user")
            .build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();
    
    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Default value", evaluatedConfig.getString("Chained conditions"));
  }

  @Test
  public void testEvaluateWithChainedAndConditionReturnsConditionalValue() throws Exception {
    KeysAndValues defaultConfig = new KeysAndValues.Builder().build();
    KeysAndValues context = new KeysAndValues.Builder()
            .put("users", "99")
            .put("randomizationId", "user")
            .build();
    ServerTemplate template = new ServerTemplateImpl.Builder(null)
            .defaultConfig(defaultConfig)
            .cachedTemplate(cacheTemplate)
            .build();
    
    ServerConfig evaluatedConfig = template.evaluate(context);

    assertEquals("Conditional value", evaluatedConfig.getString("Chained conditions"));
  }
}
