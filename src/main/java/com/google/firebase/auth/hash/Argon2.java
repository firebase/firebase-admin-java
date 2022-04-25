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

/**
 * Represents the Argon2 password hashing algorithm. Can be used as an instance of {@link
 * com.google.firebase.auth.UserImportHash} when importing users.
 */
public final class Argon2 extends UserImportHash {

  private static final int MIN_HASH_LENGTH_BYTES = 4;
  private static final int MAX_HASH_LENGTH_BYTES = 1024;
  private static final int MIN_PARALLELISM = 1;
  private static final int MAX_PARALLELISM = 16;
  private static final int MIN_ITERATIONS = 1;
  private static final int MAX_ITERATIONS = 16;
  private static final int MIN_MEMORY_COST_KIB = 1;
  private static final int MAX_MEMORY_COST_KIB = 32768;

  private final int hashLengthBytes;
  private final Argon2HashType hashType;
  private final int parallelism;
  private final int iterations;
  private final int memoryCostKib;
  private final Argon2Version version;
  private final String associatedData;

  private Argon2(Builder builder) {
    super("ARGON2");
    checkArgument(intShouldBeBetweenLimitsInclusive(builder.hashLengthBytes, MIN_HASH_LENGTH_BYTES,
            MAX_HASH_LENGTH_BYTES),
        "hashLengthBytes is required for Argon2 and must be between %s and %s",
        MIN_HASH_LENGTH_BYTES, MAX_HASH_LENGTH_BYTES);
    checkArgument(builder.hashType != null,
        "A hashType is required for Argon2");
    checkArgument(
        intShouldBeBetweenLimitsInclusive(builder.parallelism, MIN_PARALLELISM, MAX_PARALLELISM),
        "parallelism is required for Argon2 and must be between %s and %s", MIN_PARALLELISM,
        MAX_PARALLELISM);
    checkArgument(
        intShouldBeBetweenLimitsInclusive(builder.iterations, MIN_ITERATIONS, MAX_ITERATIONS),
        "iterations is required for Argon2 and must be between %s and %s", MIN_ITERATIONS,
        MAX_ITERATIONS);
    checkArgument(intShouldBeBetweenLimitsInclusive(builder.memoryCostKib, MIN_MEMORY_COST_KIB,
            MAX_MEMORY_COST_KIB),
        "memoryCostKib is required for Argon2 and must be less than or equal to %s",
        MAX_MEMORY_COST_KIB);
    this.hashLengthBytes = builder.hashLengthBytes;
    this.hashType = builder.hashType;
    this.parallelism = builder.parallelism;
    this.iterations = builder.iterations;
    this.memoryCostKib = builder.memoryCostKib;
    if (builder.version != null) {
      this.version = builder.version;
    } else {
      this.version = null;
    }
    if (builder.associatedData != null) {
      this.associatedData = BaseEncoding.base64Url().encode(builder.associatedData);
    } else {
      this.associatedData = null;
    }
  }

  private static boolean intShouldBeBetweenLimitsInclusive(int property, int fromInclusive,
      int toInclusive) {
    return property >= fromInclusive && property <= toInclusive;
  }

  @Override
  protected Map<String, Object> getOptions() {
    ImmutableMap.Builder<String, Object> argon2Parameters = ImmutableMap.<String, Object>builder()
        .put("hashLengthBytes", hashLengthBytes)
        .put("hashType", hashType.toString())
        .put("parallelism", parallelism)
        .put("iterations", iterations)
        .put("memoryCostKib", memoryCostKib);
    if (this.associatedData != null) {
      argon2Parameters.put("associatedData", associatedData);
    }
    if (this.version != null) {
      argon2Parameters.put("version", version.toString());
    }
    return ImmutableMap.<String, Object>of("argon2Parameters", argon2Parameters.build());
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

    private Builder() {}

    /**
     * Sets the hash length in bytes. Required field.
     *
     * @param hashLengthBytes an integer between 4 and 1024 (inclusive).
     * @return This builder.
     */
    public Builder setHashLengthBytes(int hashLengthBytes) {
      this.hashLengthBytes = hashLengthBytes;
      return this;
    }

    /**
     * Sets the Argon2 hash type. Required field.
     *
     * @param hashType a value from the {@link Argon2HashType} enum.
     * @return This builder.
     */
    public Builder setHashType(Argon2HashType hashType) {
      this.hashType = hashType;
      return this;
    }

    /**
     * Sets the degree of parallelism, also called threads or lanes. Required field.
     *
     * @param parallelism an integer between 1 and 16 (inclusive).
     * @return This builder.
     */
    public Builder setParallelism(int parallelism) {
      this.parallelism = parallelism;
      return this;
    }

    /**
     * Sets the number of iterations to perform. Required field.
     *
     * @param iterations an integer between 1 and 16 (inclusive).
     * @return This builder.
     */
    public Builder setIterations(int iterations) {
      this.iterations = iterations;
      return this;
    }

    /**
     * Sets the memory cost in kibibytes. Required field.
     *
     * @param memoryCostKib an integer between 1 and 32768 (inclusive).
     * @return This builder.
     */
    public Builder setMemoryCostKib(int memoryCostKib) {
      this.memoryCostKib = memoryCostKib;
      return this;
    }

    /**
     * Sets the version of the Argon2 algorithm.
     *
     * @param version a value from the {@link Argon2Version} enum.
     * @return This builder.
     */
    public Builder setVersion(Argon2Version version) {
      this.version = version;
      return this;
    }

    /**
     * Sets additional associated data, if provided, to append to the hash value for additional
     * security. This data is base64 encoded before it is sent to the API.
     *
     * @param associatedData Associated data as a byte array.
     * @return This builder.
     */
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
    VERSION_10,
    VERSION_13
  }
}
