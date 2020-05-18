/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
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

package com.google.firebase.auth.internal;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import java.util.List;
import java.util.Map;

/**
 * Contains nested SAML provider config response messages sent by Google identity toolkit service.
 */
public final class SamlProviderConfigResponse {

  public static class IdpCertificate {
    @Key("x509Certificate")
    private String x509Certificate;

    public String getX509Certificate() {
      return x509Certificate;
    }
  }

  public static class IdpConfig {
    @Key("idpEntityId")
    private String idpEntityId;

    @Key("ssoUrl")
    private String ssoUrl;

    @Key("idpCertificates")
    private List<IdpCertificate> idpCertificates;

    public String getIdpEntityId() {
      return idpEntityId;
    }

    public String getSsoUrl() {
      return ssoUrl;
    }

    public List<IdpCertificate> getIdpCertificates() {
      return idpCertificates;
    }
  }

  public static class SpConfig {
    @Key("spEntityId")
    private String rpEntityId;

    @Key("callbackUri")
    private String callbackUrl;

    public String getRpEntityId() {
      return rpEntityId;
    }

    public String getCallbackUrl() {
      return callbackUrl;
    }
  }

  private SamlProviderConfigResponse() { }
}
