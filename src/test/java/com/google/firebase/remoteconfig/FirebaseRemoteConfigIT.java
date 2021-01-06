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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.testing.IntegrationTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;

public class FirebaseRemoteConfigIT {

  private static FirebaseRemoteConfig remoteConfig;

  private static final List<Condition> CONDITIONS = ImmutableList.of(
          new Condition("ios_en", "device.os == 'ios' && device.country in ['us', 'uk']")
                  .setTagColor(TagColor.INDIGO),
          new Condition("android_en",
                  "device.os == 'android' && device.country in ['us', 'uk']"));

  @BeforeClass
  public static void setUpClass() {
    remoteConfig = FirebaseRemoteConfig.getInstance(IntegrationTestUtils.ensureDefaultApp());
  }

  @Test
  public void testValidateTemplate() throws FirebaseRemoteConfigException {
    final Template inputTemplate = remoteConfig.getTemplate();
    final Map<String, Parameter> expectedParameters = getParameters();
    final Map<String, ParameterGroup> expectedParameterGroups = getParameterGroups();
    final Version expectedVersion = getVersion();
    inputTemplate.setParameters(expectedParameters)
            .setParameterGroups(expectedParameterGroups)
            .setConditions(CONDITIONS)
            .setVersion(expectedVersion);

    Template validatedTemplate = remoteConfig.validateTemplate(inputTemplate);

    assertEquals(inputTemplate, validatedTemplate);
  }

  @Test
  public void testPublishTemplate() throws FirebaseRemoteConfigException {
    // get template to fetch the active template with correct etag
    final Template oldTemplate = remoteConfig.getTemplate();
    final Template inputTemplate = Template.fromJSON(oldTemplate.toJSON());
    final Map<String, Parameter> parameters = getParameters();
    final Map<String, ParameterGroup> parameterGroups = getParameterGroups();
    final Version version = getVersion();
    // modify template
    inputTemplate.setParameters(parameters)
            .setParameterGroups(parameterGroups)
            .setConditions(CONDITIONS)
            .setVersion(version);

    // publish template
    Template publishedTemplate = remoteConfig.publishTemplate(inputTemplate);

    assertNotEquals(inputTemplate.getETag(), publishedTemplate.getETag());
    assertEquals(parameters, publishedTemplate.getParameters());
    assertEquals(parameterGroups, publishedTemplate.getParameterGroups());
    assertEquals(CONDITIONS, publishedTemplate.getConditions());
    assertNotEquals(version, publishedTemplate.getVersion());
  }

  @Test
  public void testGetTemplate() throws FirebaseRemoteConfigException {
    // get template to fetch the active template with correct etag
    // modify and publish a known template to test get template operation.
    final Template oldTemplate = remoteConfig.getTemplate();
    final Template inputTemplate = Template.fromJSON(oldTemplate.toJSON());
    final Map<String, Parameter> parameters = getParameters();
    final Map<String, ParameterGroup> parameterGroups = getParameterGroups();
    final Version version = getVersion();
    inputTemplate.setParameters(parameters)
            .setParameterGroups(parameterGroups)
            .setConditions(CONDITIONS)
            .setVersion(version);
    // publish a known template
    Template publishedTemplate = remoteConfig.publishTemplate(inputTemplate);

    // get template
    Template currentTemplate = remoteConfig.getTemplate();

    assertEquals(publishedTemplate, currentTemplate);
  }

  @Test
  public void testGetTemplateAtVersion() throws FirebaseRemoteConfigException {
    // get template to fetch the active template with correct etag
    // store the template version number
    // publish a new template to test get template at version operation.
    final Template oldTemplate = remoteConfig.getTemplate();
    final Template inputTemplate = Template.fromJSON(oldTemplate.toJSON());
    final String versionNumber = oldTemplate.getVersion().getVersionNumber();
    final Map<String, Parameter> parameters = getParameters();
    final Map<String, ParameterGroup> parameterGroups = getParameterGroups();
    final Version version = getVersion();
    inputTemplate.setParameters(parameters)
            .setParameterGroups(parameterGroups)
            .setConditions(CONDITIONS)
            .setVersion(version);
    // publish a new template
    Template publishedTemplate = remoteConfig.publishTemplate(inputTemplate);

    // get template at version
    Template atVersionTemplate = remoteConfig.getTemplateAtVersion(versionNumber);

    assertEquals(oldTemplate, atVersionTemplate);
    assertEquals(versionNumber, atVersionTemplate.getVersion().getVersionNumber());
    assertNotEquals(publishedTemplate, atVersionTemplate);
  }

  @Test
  public void testRollbackTemplate() throws FirebaseRemoteConfigException {
    // get template to fetch the active template with correct etag.
    // store the template version number to rollback.
    final Template oldTemplate = remoteConfig.getTemplate();
    final Template inputTemplate = Template.fromJSON(oldTemplate.toJSON());
    final String versionNumber = oldTemplate.getVersion().getVersionNumber();
    final Map<String, Parameter> parameters = getParameters();
    final Map<String, ParameterGroup> parameterGroups = getParameterGroups();
    final Version version = getVersion();
    inputTemplate.setParameters(parameters)
            .setParameterGroups(parameterGroups)
            .setConditions(CONDITIONS)
            .setVersion(version);
    // publish a new template before rolling back to versionNumber
    Template publishedTemplate = remoteConfig.publishTemplate(inputTemplate);

    // rollback template
    Template rolledBackTemplate = remoteConfig.rollback(versionNumber);

    assertEquals(String.format("Rollback to version %s", versionNumber),
            rolledBackTemplate.getVersion().getDescription());

    // get template to verify rollback
    Template activeTemplate = remoteConfig.getTemplate();

    assertEquals(rolledBackTemplate, activeTemplate);
    assertNotEquals(publishedTemplate, activeTemplate);
  }

  @Test
  public void testListVersions() throws Exception {
    final List<String> versions = new ArrayList<>();
    Template template = remoteConfig.getTemplate();
    for (int i = 0; i < 3; i++) {
      template = remoteConfig.publishTemplate(template);
      versions.add(template.getVersion().getVersionNumber());
    }

    // Test list by batches
    final AtomicInteger collected = new AtomicInteger(0);
    ListVersionsPage page = remoteConfig.listVersions();
    while (page != null) {
      for (Version version : page.getValues()) {
        if (versions.contains(version.getVersionNumber())) {
          collected.incrementAndGet();
        }
      }
      page = page.getNextPage();
    }
    assertEquals(versions.size(), collected.get());

    // Test iterate all
    collected.set(0);
    page = remoteConfig.listVersions();
    for (Version version : page.iterateAll()) {
      if (versions.contains(version.getVersionNumber())) {
        collected.incrementAndGet();
      }
    }
    assertEquals(versions.size(), collected.get());

    // Test with list options
    collected.set(0);
    ListVersionsOptions listOptions = ListVersionsOptions.builder().setPageSize(2).build();
    page = remoteConfig.listVersions(listOptions);
    for (Version version : page.getValues()) {
      if (versions.contains(version.getVersionNumber())) {
        collected.incrementAndGet();
      }
    }
    assertEquals(2, collected.get());

    // Test iterate async
    collected.set(0);
    final Semaphore semaphore = new Semaphore(0);
    final AtomicReference<Throwable> error = new AtomicReference<>();
    ApiFuture<ListVersionsPage> pageFuture = remoteConfig.listVersionsAsync();
    ApiFutures.addCallback(pageFuture, new ApiFutureCallback<ListVersionsPage>() {
      @Override
      public void onFailure(Throwable t) {
        error.set(t);
        semaphore.release();
      }

      @Override
      public void onSuccess(ListVersionsPage result) {
        for (Version version : result.iterateAll()) {
          if (versions.contains(version.getVersionNumber())) {
            collected.incrementAndGet();
          }
        }
        semaphore.release();
      }
    }, MoreExecutors.directExecutor());
    semaphore.acquire();
    assertEquals(versions.size(), collected.get());
    assertNull(error.get());
  }

  private Map<String, Parameter> getParameters() {
    final long timestamp = System.currentTimeMillis();
    return ImmutableMap.of(
            "welcome_message_text", new Parameter()
                    .setDefaultValue(ParameterValue
                            .of(String.format("welcome to app %s", timestamp)))
                    .setConditionalValues(ImmutableMap.<String, ParameterValue>of(
                            "ios_en",
                            ParameterValue.of(String.format("welcome to app en %s", timestamp))
                    ))
                    .setDescription("text for welcome message!"),
            "header_text", new Parameter()
                    .setDefaultValue(ParameterValue.inAppDefault()));
  }

  private Map<String, ParameterGroup> getParameterGroups() {
    final long timestamp = System.currentTimeMillis();
    return ImmutableMap.of(
            "new menu", new ParameterGroup()
                    .setDescription(String.format("New Menu %s", timestamp))
                    .setParameters(ImmutableMap.of(
                            "pumpkin_spice_season", new Parameter()
                                    .setDefaultValue(ParameterValue.of("true"))
                                    .setDescription("Whether it's currently pumpkin spice season."))
                    ));
  }

  private Version getVersion() {
    final long timestamp = System.currentTimeMillis();
    return Version.withDescription(String.format("promo config %s", timestamp));
  }
}
