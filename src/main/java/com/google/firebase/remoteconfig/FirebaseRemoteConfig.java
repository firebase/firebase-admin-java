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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.core.ApiFuture;
import com.google.common.annotations.VisibleForTesting;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.CallableOperation;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;

/**
 * This class is the entry point for all server-side Firebase Remote Config actions.
 *
 * <p>You can get an instance of {@link FirebaseRemoteConfig} via {@link #getInstance(FirebaseApp)},
 * and then use it to manage Remote Config templates.
 */
public final class FirebaseRemoteConfig {

  private static final String SERVICE_ID = FirebaseRemoteConfig.class.getName();
  private final FirebaseApp app;
  private final FirebaseRemoteConfigClient remoteConfigClient;

  @VisibleForTesting
  FirebaseRemoteConfig(FirebaseApp app, FirebaseRemoteConfigClient client) {
    this.app = checkNotNull(app);
    this.remoteConfigClient = checkNotNull(client);
  }

  private FirebaseRemoteConfig(FirebaseApp app) {
    this(app, FirebaseRemoteConfigClientImpl.fromApp(app));
  }

  /**
   * Gets the {@link FirebaseRemoteConfig} instance for the default {@link FirebaseApp}.
   *
   * @return The {@link FirebaseRemoteConfig} instance for the default {@link FirebaseApp}.
   */
  public static FirebaseRemoteConfig getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Gets the {@link FirebaseRemoteConfig} instance for the specified {@link FirebaseApp}.
   *
   * @return The {@link FirebaseRemoteConfig} instance for the specified {@link FirebaseApp}.
   */
  public static synchronized FirebaseRemoteConfig getInstance(FirebaseApp app) {
    FirebaseRemoteConfigService service = ImplFirebaseTrampolines.getService(app, SERVICE_ID,
            FirebaseRemoteConfigService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirebaseRemoteConfigService(app));
    }
    return service.getInstance();
  }

  /**
   * Gets the current active version of the Remote Config template.
   *
   * @return A {@link Template}.
   * @throws FirebaseRemoteConfigException If an error occurs while getting the template.
   */
  public Template getTemplate() throws FirebaseRemoteConfigException {
    return getTemplateOp().call();
  }

  /**
   * Similar to {@link #getTemplate()} but performs the operation asynchronously.
   *
   * @return An {@code ApiFuture} that completes with a {@link Template} when
   *      the template is available.
   */
  public ApiFuture<Template> getTemplateAsync() {
    return getTemplateOp().callAsync(app);
  }

  private CallableOperation<Template, FirebaseRemoteConfigException> getTemplateOp() {
    final FirebaseRemoteConfigClient remoteConfigClient = getRemoteConfigClient();
    return new CallableOperation<Template, FirebaseRemoteConfigException>() {
      @Override
      protected Template execute() throws FirebaseRemoteConfigException {
        return remoteConfigClient.getTemplate();
      }
    };
  }

  /**
   * Gets the requested version of the of the Remote Config template.
   *
   * @param versionNumber The version number of the Remote Config template to look up.
   * @return A {@link Template}.
   * @throws FirebaseRemoteConfigException If an error occurs while getting the template.
   */
  public Template getTemplateAtVersion(
          @NonNull String versionNumber) throws FirebaseRemoteConfigException {
    return getTemplateAtVersionOp(versionNumber).call();
  }

  /**
   * Gets the requested version of the of the Remote Config template.
   *
   * @param versionNumber The version number of the Remote Config template to look up.
   * @return A {@link Template}.
   * @throws FirebaseRemoteConfigException If an error occurs while getting the template.
   */
  public Template getTemplateAtVersion(long versionNumber) throws FirebaseRemoteConfigException {
    String versionNumberString = String.valueOf(versionNumber);
    return getTemplateAtVersionOp(versionNumberString).call();
  }

  /**
   * Similar to {@link #getTemplateAtVersion(String versionNumber)} but performs the operation
   * asynchronously.
   *
   * @param versionNumber The version number of the Remote Config template to look up.
   * @return An {@code ApiFuture} that completes with a {@link Template} when
   *     the requested template is available.
   */
  public ApiFuture<Template> getTemplateAtVersionAsync(@NonNull String versionNumber) {
    return getTemplateAtVersionOp(versionNumber).callAsync(app);
  }

  /**
   * Similar to {@link #getTemplateAtVersion(long versionNumber)} but performs the operation
   * asynchronously.
   *
   * @param versionNumber The version number of the Remote Config template to look up.
   * @return An {@code ApiFuture} that completes with a {@link Template} when
   *     the requested template is available.
   */
  public ApiFuture<Template> getTemplateAtVersionAsync(long versionNumber) {
    String versionNumberString = String.valueOf(versionNumber);
    return getTemplateAtVersionOp(versionNumberString).callAsync(app);
  }

  private CallableOperation<Template, FirebaseRemoteConfigException> getTemplateAtVersionOp(
          final String versionNumber) {
    final FirebaseRemoteConfigClient remoteConfigClient = getRemoteConfigClient();
    return new CallableOperation<Template, FirebaseRemoteConfigException>() {
      @Override
      protected Template execute() throws FirebaseRemoteConfigException {
        return remoteConfigClient.getTemplateAtVersion(versionNumber);
      }
    };
  }

  /**
   * Publishes a Remote Config template.
   *
   * @param template The Remote Config template to be published.
   * @return The published {@link Template}.
   * @throws FirebaseRemoteConfigException If an error occurs while publishing the template.
   */
  public Template publishTemplate(@NonNull Template template) throws FirebaseRemoteConfigException {
    return publishTemplateOp(template, false, false).call();
  }

  /**
   * Similar to {@link #publishTemplate(Template template)} but performs the operation
   * asynchronously.
   *
   * @param template The Remote Config template to be published.
   * @return An {@code ApiFuture} that completes with a {@link Template} when
   *     the provided template is published.
   */
  public ApiFuture<Template> publishTemplateAsync(@NonNull Template template) {
    return publishTemplateOp(template, false, false).callAsync(app);
  }

  /**
   * Validates a Remote Config template.
   *
   * @param template The Remote Config template to be validated.
   * @return The validated {@link Template}.
   * @throws FirebaseRemoteConfigException If an error occurs while validating the template.
   */
  public Template validateTemplate(
          @NonNull Template template) throws FirebaseRemoteConfigException {
    return publishTemplateOp(template, true, false).call();
  }

  /**
   * Similar to {@link #validateTemplate(Template template)} but performs the operation
   * asynchronously.
   *
   * @param template The Remote Config template to be validated.
   * @return An {@code ApiFuture} that completes with a {@link Template} when
   *     the provided template is validated.
   */
  public ApiFuture<Template> validateTemplateAsync(@NonNull Template template) {
    return publishTemplateOp(template, true, false).callAsync(app);
  }

  /**
   * Force publishes a Remote Config template.
   *
   * <p>This method forces the Remote Config template to be updated and circumvent the ETag.
   * This approach is not recommended because it risks causing the loss of updates to your
   * Remote Config template if multiple clients are updating the Remote Config template.
   * See <a href="https://firebase.google.com/docs/remote-config/use-config-rest#etag_usage_and_forced_updates">
   * ETag usage and forced updates</a>.
   *
   * @param template The Remote Config template to be forcefully published.
   * @return The published {@link Template}.
   * @throws FirebaseRemoteConfigException If an error occurs while publishing the template.
   */
  public Template forcePublishTemplate(
          @NonNull Template template) throws FirebaseRemoteConfigException {
    return publishTemplateOp(template, false, true).call();
  }

  /**
   * Similar to {@link #forcePublishTemplate(Template template)} but performs the operation
   * asynchronously.
   *
   * @param template The Remote Config template to be forcefully published.
   * @return An {@code ApiFuture} that completes with a {@link Template} when
   *     the provided template is published.
   */
  public ApiFuture<Template> forcePublishTemplateAsync(@NonNull Template template) {
    return publishTemplateOp(template, false, true).callAsync(app);
  }

  private CallableOperation<Template, FirebaseRemoteConfigException> publishTemplateOp(
          final Template template, final boolean validateOnly, final boolean forcePublish) {
    final FirebaseRemoteConfigClient remoteConfigClient = getRemoteConfigClient();
    return new CallableOperation<Template, FirebaseRemoteConfigException>() {
      @Override
      protected Template execute() throws FirebaseRemoteConfigException {
        return remoteConfigClient.publishTemplate(template, validateOnly, forcePublish);
      }
    };
  }

  @VisibleForTesting
  FirebaseRemoteConfigClient getRemoteConfigClient() {
    return remoteConfigClient;
  }

  private static class FirebaseRemoteConfigService extends FirebaseService<FirebaseRemoteConfig> {

    FirebaseRemoteConfigService(FirebaseApp app) {
      super(SERVICE_ID, new FirebaseRemoteConfig(app));
    }

    @Override
    public void destroy() {
      // NOTE: We don't explicitly tear down anything here, but public methods of
      // FirebaseRemoteConfig will now fail because calls to getOptions() and getToken()
      // will hit FirebaseApp, which will throw once the app is deleted.
    }
  }
}
