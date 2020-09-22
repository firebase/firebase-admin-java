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

/**
 * This class is the entry point for all server-side Firebase Remote Config actions.
 *
 * <p>You can get an instance of FirebaseRemoteConfig via {@link #getInstance(FirebaseApp)}, and
 * then use it to manage Remote Config templates.
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
   * @return A {@link RemoteConfigTemplate}.
   * @throws FirebaseRemoteConfigException If an error occurs while getting the template.
   */
  public RemoteConfigTemplate getTemplate() throws FirebaseRemoteConfigException {
    return getTemplateOp().call();
  }

  /**
   * Similar to {@link #getTemplate()} but performs the operation asynchronously.
   *
   * @return An {@code ApiFuture} that will complete with a {@link RemoteConfigTemplate} when
   *      the template is available.
   */
  public ApiFuture<RemoteConfigTemplate> getTemplateAsync() {
    return getTemplateOp().callAsync(app);
  }

  private CallableOperation<RemoteConfigTemplate, FirebaseRemoteConfigException> getTemplateOp() {
    final FirebaseRemoteConfigClient remoteConfigClient = getRemoteConfigClient();
    return new CallableOperation<RemoteConfigTemplate, FirebaseRemoteConfigException>() {
      @Override
      protected RemoteConfigTemplate execute() throws FirebaseRemoteConfigException {
        return remoteConfigClient.getTemplate();
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
