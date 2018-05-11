/*
 * Copyright 2018 Google Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.UserImportHash;
import java.util.Map;

/**
 * Represents the Standard Scrypt password hashing algorithm. Can be used as an instance of
 * {@link com.google.firebase.auth.UserImportHash} when importing users.
 */
public class StandardScrypt extends UserImportHash {

  private final int derivedKeyLength;
  private final int blockSize;
  private final int parallelization;
  private final int memoryCost;

  private StandardScrypt(Builder builder) {
    super("STANDARD_SCRYPT");
    this.derivedKeyLength = builder.derivedKeyLength;
    this.blockSize = builder.blockSize;
    this.parallelization = builder.parallelization;
    this.memoryCost = builder.memoryCost;
  }

  @Override
  protected Map<String, Object> getOptions() {
    return ImmutableMap.<String, Object>of(
        "dkLen", derivedKeyLength,
        "blockSize", blockSize,
        "parallelization", parallelization,
        "memoryCost", memoryCost
    );
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private int derivedKeyLength;
    private int blockSize;
    private int parallelization;
    private int memoryCost;

    private Builder() {}

    public Builder setDerivedKeyLength(int derivedKeyLength) {
      this.derivedKeyLength = derivedKeyLength;
      return this;
    }

    public Builder setBlockSize(int blockSize) {
      this.blockSize = blockSize;
      return this;
    }

    public Builder setParallelization(int parallelization) {
      this.parallelization = parallelization;
      return this;
    }

    public Builder setMemoryCost(int memoryCost) {
      this.memoryCost = memoryCost;
      return this;
    }

    public StandardScrypt build() {
      return new StandardScrypt(this);
    }
  }
}
