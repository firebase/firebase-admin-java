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
import com.google.firebase.auth.UserImportHash;
import java.util.Map;

/**
 * An abstract {@link UserImportHash} implementation that accepts a {@code rounds} parameter in
 * a given range.
 */
abstract class RepeatableHash extends UserImportHash {

  private final int rounds;

  RepeatableHash(String name, int min, int max, Builder builder) {
    super(name);
    checkArgument(builder.rounds >= min && builder.rounds <= max,
        "Rounds value must be between %s and %s (inclusive).", min, max);
    this.rounds = builder.rounds;
  }

  @Override
  protected Map<String, Object> getOptions() {
    return ImmutableMap.<String, Object>of("rounds", rounds);
  }

  abstract static class Builder<T extends Builder, U extends UserImportHash> {
    private int rounds;

    protected abstract T getInstance();

    /**
     * Sets the number of rounds for the hash algorithm.
     *
     * @param rounds an integer between 0 and 120000 (inclusive).
     * @return this builder.
     */
    public T setRounds(int rounds) {
      this.rounds = rounds;
      return getInstance();
    }

    public abstract U build();
  }
}
