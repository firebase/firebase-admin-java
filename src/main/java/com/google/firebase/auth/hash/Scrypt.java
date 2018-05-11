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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.UserImportHash;
import java.util.Map;

/**
 * Represents the Scrypt password hashing algorithm. This is the
 * <a href="https://github.com/firebase/scrypt">modified Scrypt algorithm</a> used by
 * Firebase Auth. See {@link StandardScrypt} for the standard Scrypt algorithm. Can be used as an
 * instance of {@link com.google.firebase.auth.UserImportHash} when importing users.
 */
public final class Scrypt extends UserImportHash {

  private final String key;
  private final String saltSeparator;
  private final int rounds;
  private final int memoryCost;

  private Scrypt(Builder builder) {
    super("SCRYPT");
    checkArgument(builder.key != null && builder.key.length > 0,
        "A non-empty key is required for Scrypt");
    checkArgument(builder.rounds > 0 && builder.rounds <= 8, "rounds must be between 1 and 8");
    checkArgument(builder.memoryCost > 0 && builder.memoryCost <= 14,
        "memoryCost must be between 1 and 14");
    this.key = BaseEncoding.base64Url().encode(builder.key);
    if (builder.saltSeparator != null) {
      this.saltSeparator = BaseEncoding.base64Url().encode(builder.saltSeparator);
    } else {
      this.saltSeparator = BaseEncoding.base64Url().encode(new byte[0]);
    }
    this.rounds = builder.rounds;
    this.memoryCost = builder.memoryCost;
  }

  @Override
  protected Map<String, Object> getOptions() {
    return ImmutableMap.<String, Object>of(
        "signerKey", key,
        "rounds", rounds,
        "memoryCost", memoryCost,
        "saltSeparator", saltSeparator
    );
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private byte[] key;
    private byte[] saltSeparator;
    private int rounds;
    private int memoryCost;

    private Builder() {}

    /**
     * Sets the signer key. Required field.
     *
     * @param key Signer key as a byte array.
     * @return This builder.
     */
    public Builder setKey(byte[] key) {
      this.key = key;
      return this;
    }

    /**
     * Sets the salt separator.
     *
     * @param saltSeparator Salt separator as a byte array.
     * @return This builder.
     */
    public Builder setSaltSeparator(byte[] saltSeparator) {
      this.saltSeparator = saltSeparator;
      return this;
    }

    /**
     * Sets the number of rounds. Required field.
     *
     * @param rounds an integer between 1 and 8 (inclusive).
     * @return this builder.
     */
    public Builder setRounds(int rounds) {
      this.rounds = rounds;
      return this;
    }

    /**
     * Sets the memory cost. Required field.
     *
     * @param memoryCost an integer between 1 and 14 (inclusive).
     * @return this builder.
     */
    public Builder setMemoryCost(int memoryCost) {
      this.memoryCost = memoryCost;
      return this;
    }

    public Scrypt build() {
      return new Scrypt(this);
    }
  }
}
