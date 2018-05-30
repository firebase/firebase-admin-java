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

abstract class Hmac extends UserImportHash {

  private final String key;

  Hmac(String name, Builder builder) {
    super(name);
    checkArgument(builder.key != null && builder.key.length > 0,
        "A non-empty key is required for HMAC algorithms");
    this.key = BaseEncoding.base64().encode(builder.key);
  }

  @Override
  protected final Map<String, Object> getOptions() {
    return ImmutableMap.<String, Object>of("signerKey", key);
  }

  abstract static class Builder<T extends Builder, U extends UserImportHash> {
    private byte[] key;

    protected abstract T getInstance();

    /**
     * Sets the signer key for the HMAC hash algorithm. Required field.
     *
     * @param key Signer key as a byte array.
     * @return This builder.
     */
    public T setKey(byte[] key) {
      this.key = key;
      return getInstance();
    }

    public abstract U build();
  }
}
