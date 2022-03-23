/*
 * Copyright 2022 Google LLC
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

package com.google.firebase.appcheck;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;

/**
 * A helper class for interacting with Firebase App Check service.
 */
final class FirebaseAppCheckClientImpl implements FirebaseAppCheckClient {
  private final AppCheckTokenVerifier tokenVerifier;

  private FirebaseAppCheckClientImpl(FirebaseAppCheckClientImpl.Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.projectId));
    checkArgument(builder.appCheckTokenVerifier != null);
    this.tokenVerifier = builder.appCheckTokenVerifier;
  }

  static FirebaseAppCheckClientImpl fromApp(FirebaseApp app) {
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
            "Project ID is required to access App Check service. Use a service "
                    + "account credential or set the project ID explicitly via FirebaseOptions. "
                    + "Alternatively you can also set the project ID via the GOOGLE_CLOUD_PROJECT "
                    + "environment variable.");
    AppCheckTokenVerifier appCheckTokenVerifier = AppCheckTokenVerifier.builder()
            .setProjectId(projectId)
            .build();
    return FirebaseAppCheckClientImpl.builder()
            .setProjectId(projectId)
            .setAppCheckTokenVerifier(appCheckTokenVerifier)
            .build();
  }

  static Builder builder() {
    return new Builder();
  }

  @Override
  public VerifyAppCheckTokenResponse verifyToken(String token) throws FirebaseAppCheckException {
    // Verify Token payload
    // Verify token signature
    // return the decoded token
    // new DecodedAppCheckToken(payload)
    // new VerifyAppCheckTokenResponse(token)
    DecodedAppCheckToken decodedAppCheckToken = tokenVerifier.verifyToken(token);
    return new VerifyAppCheckTokenResponse(decodedAppCheckToken);
  }

  static final class Builder {

    private String projectId;
    private AppCheckTokenVerifier appCheckTokenVerifier;

    private Builder() {
    }

    Builder setProjectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    Builder setAppCheckTokenVerifier(AppCheckTokenVerifier appCheckTokenVerifier) {
      this.appCheckTokenVerifier = appCheckTokenVerifier;
      return this;
    }

    FirebaseAppCheckClientImpl build() {
      return new FirebaseAppCheckClientImpl(this);
    }
  }
}
