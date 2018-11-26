/* Copyright 2018 Google Inc.
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

package com.google.firebase.projectmanagement;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import java.util.regex.Pattern;

/**
 * Contains detailed information of a SHA certificate, which can be associated to an Android app.
 */
public class ShaCertificate {

  private static final Pattern SHA1_PATTERN = Pattern.compile("[0-9a-fA-F]{40}");
  private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");

  private final String name;
  private final String shaHash;
  private final ShaCertificateType certType;

  private ShaCertificate(String name, String shaHash, ShaCertificateType certType) {
    this.name = Preconditions.checkNotNull(name, "Null name");
    this.shaHash = Preconditions.checkNotNull(shaHash, "Null shaHash");
    this.certType = Preconditions.checkNotNull(certType, "Null certType");
  }

  /**
   * Creates a {@link ShaCertificate} from certificate hash. Name will be left as empty string since
   * the certificate doesn't have a generated name yet.
   *
   * @param shaHash SHA hash of the certificate
   * @return a SHA certificate
   */
  public static ShaCertificate create(String shaHash) {
    return new ShaCertificate("", shaHash, getTypeFromHash(shaHash));
  }

  static ShaCertificate create(String name, String shaHash) {
    return new ShaCertificate(name, shaHash, getTypeFromHash(shaHash));
  }

  /**
   * Returns the type of the certificate based on its hash.
   *
   * @throws IllegalArgumentException if the SHA hash is neither SHA-1 nor SHA-256
   */
  @VisibleForTesting
  static ShaCertificateType getTypeFromHash(String shaHash) {
    Preconditions.checkNotNull(shaHash, "Null shaHash");
    shaHash = Ascii.toLowerCase(shaHash);
    if (SHA1_PATTERN.matcher(shaHash).matches()) {
      return ShaCertificateType.SHA_1;
    } else if (SHA256_PATTERN.matcher(shaHash).matches()) {
      return ShaCertificateType.SHA_256;
    }
    throw new IllegalArgumentException("Invalid SHA hash, it is neither SHA-1 nor SHA-256.");
  }

  /**
   * Returns the fully qualified resource name of this SHA certificate.
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the hash of this SHA certificate.
   */
  public String getShaHash() {
    return shaHash;
  }

  /**
   * Returns the type {@link ShaCertificateType} of this SHA certificate.
   */
  public ShaCertificateType getCertType() {
    return certType;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ShaCertificate) {
      ShaCertificate that = (ShaCertificate) o;
      return (this.name.equals(that.getName()))
          && (this.shaHash.equals(that.getShaHash()))
          && (this.certType.equals(that.getCertType()));
    }
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("ShaCertificate")
        .add("name", name)
        .add("shaHash", shaHash)
        .add("certType", certType)
        .toString();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, shaHash, certType);
  }
}
