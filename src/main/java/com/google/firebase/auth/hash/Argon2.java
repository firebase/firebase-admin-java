/*
 * Copyright 2022 Google Inc.
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

package com.google.firebase.auth.hash;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.UserImportHash;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Represents the Argon2 password hashing algorithm. Can be used as an instance of {@link
 * com.google.firebase.auth.UserImportHash} when importing users.
 */
public final class Argon2 extends UserImportHash {

  private final int hashLengthBytes;
  private final Argon2HashType hashType;
  private final int parallelism;
  private final int iterations;
  private final int memoryCostKib;
  private final Argon2Version version;
  private final byte[] associatedData;

  private Argon2(Builder builder) {
    super("ARGON2");
    checkArgument(builder.hashLengthBytes >= 4 && builder.hashLengthBytes <= 1024,
        "hashLengthBytes is required for Argon2 and must be between 4 and 1024");
    checkArgument(builder.hashType != null,
        "A hashType is required for Argon2");
    checkArgument(builder.parallelism >= 1 && builder.parallelism <= 16,
        "parallelism is required for Argon2 and must be between 1 and 16");
    checkArgument(builder.iterations >= 1 && builder.iterations <= 16,
        "iterations is required for Argon2 and must be between 1 and 16");
    checkArgument(builder.memoryCostKib > 0 && builder.memoryCostKib <= 32768,
        "memoryCostKib is required for Argon2 and must be less than or equal to 32768");
    this.hashLengthBytes = builder.hashLengthBytes;
    this.hashType = builder.hashType;
    this.parallelism = builder.parallelism;
    this.iterations = builder.iterations;
    this.memoryCostKib = builder.memoryCostKib;
    if (builder.version != null) {
      this.version = builder.version;
    } else {
      this.version = Argon2Version.VERSION_13;
    }
    if (builder.associatedData != null) {
      this.associatedData = builder.associatedData;
    } else {
      this.associatedData = null;
    }
  }

  @Override
  protected Map<String, Object> getOptions() {
    ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
        .put("hashLengthBytes", hashLengthBytes)
        .put("hashType", hashType)
        .put("parallelism", parallelism)
        .put("iterations", iterations)
        .put("memoryCostKib", memoryCostKib)
        .put("version", version.getValue());
    if (this.associatedData != null) {
      builder.put("associatedData", associatedData);
    }
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private int hashLengthBytes;
    private Argon2HashType hashType;
    private int parallelism;
    private int iterations;
    private int memoryCostKib;
    private Argon2Version version;
    private byte[] associatedData;

    private Builder() {
    }

    public Builder setHashLengthBytes(int hashLengthBytes) {
      this.hashLengthBytes = hashLengthBytes;
      return this;
    }

    public Builder setHashType(Argon2HashType hashType) {
      this.hashType = hashType;
      return this;
    }

    public Builder setParallelism(int parallelism) {
      this.parallelism = parallelism;
      return this;
    }

    public Builder setIterations(int iterations) {
      this.iterations = iterations;
      return this;
    }

    public Builder setMemoryCostKib(int memoryCostKib) {
      this.memoryCostKib = memoryCostKib;
      return this;
    }

    public Builder setVersion(Argon2Version version) {
      this.version = version;
      return this;
    }

    public Builder setAssociatedData(byte[] associatedData) {
      this.associatedData = associatedData;
      return this;
    }

    public Argon2 build() {
      return new Argon2(this);
    }
  }

  public enum Argon2HashType {
    ARGON2_D,
    ARGON2_ID,
    ARGON2_I
  }

  public enum Argon2Version {
    VERSION_10(0x10),
    VERSION_13(0x13);

    private final int value;

    Argon2Version(final int newValue) {
      value = newValue;
    }

    public int getValue() {
      return value;
    }
  }
}