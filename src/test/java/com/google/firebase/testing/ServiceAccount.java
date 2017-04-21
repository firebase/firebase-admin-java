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

package com.google.firebase.testing;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.util.SecurityUtils;
import com.google.api.client.util.StringUtils;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

/**
 * Private key files, x509 certs, and email addresses for service accounts with different access
 * levels for the "mock-project-id" project defined in TestUtils.
 */
public enum ServiceAccount {
  OWNER(
      "mock-project-id-owner@mock-project-id.iam.gserviceaccount.com",
      TestUtils.loadResource("service_accounts/owner.json"),
      TestUtils.loadResource("service_accounts/owner_public_key.pem")),

  EDITOR(
      "mock-project-id-editor@mock-project-id.iam.gserviceaccount.com",
      TestUtils.loadResource("service_accounts/editor.json"),
      TestUtils.loadResource("service_accounts/editor_public_key.pem")),

  VIEWER(
      "mock-project-id-viewer@mock-project-id.iam.gserviceaccount.com",
      TestUtils.loadResource("service_accounts/viewer.json"),
      TestUtils.loadResource("service_accounts/viewer_public_key.pem")),

  NONE(
      "mock-project-id-none@mock-project-id.iam.gserviceaccount.com",
      TestUtils.loadResource("service_accounts/none.json"),
      TestUtils.loadResource("service_accounts/none_public_key.pem"));

  private final String json;
  private final String cert;
  private final String email;

  ServiceAccount(String email, String json, String cert) {
    this.json = json;
    this.cert = cert;
    this.email = email;
  }

  public String getPrivateKey() {
    // Extract the private key from the JSON data provided above (Note: \\n for linebreaks is
    // intentional).
    String beginMark = "-----BEGIN PRIVATE KEY-----\\n";
    String endMark = "-----END PRIVATE KEY-----\\n";

    return json.substring(json.indexOf(beginMark) + beginMark.length(), json.indexOf(endMark))
        .replace("\\n", "");
  }

  public String getCert() {
    return cert;
  }

  public String getEmail() {
    return email;
  }

  /** Returns the String representation of the service account JSON. */
  public String asString() {
    return json;
  }

  /** Returns a stream of the service account JSON. */
  public InputStream asStream() {
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }

  /** Returns true if the given token was signed by this account. */
  public boolean verifySignature(JsonWebSignature token) throws Exception {
    CertificateFactory factory = SecurityUtils.getX509CertificateFactory();
    X509Certificate x509Cert =
        (X509Certificate)
            factory.generateCertificate(new ByteArrayInputStream(StringUtils.getBytesUtf8(cert)));
    return token.verifySignature(x509Cert.getPublicKey());
  }
}
