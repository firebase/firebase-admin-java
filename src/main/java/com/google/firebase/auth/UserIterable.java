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

package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.firebase.internal.NonNull;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An {@code Iterable} that enables iterating over the user accounts of a Firebase project. It can
 * be used to obtain an {@code Iterator} over the user accounts, or it can be used directly as
 * the target of a foreach loop.
 *
 * <p>The {@code maxResults} parameter governs the maximum number of user accounts an iterator is
 * allowed to keep in memory during iteration. It also controls the number of user accounts to
 * be retrieved in a single RPC call. The returned iterators transparently page through user
 * accounts. No RPC calls are made until an iterator is used (i.e. until the {@code hasNext()}
 * method is called).
 *
 * <p>This {@code Iterable} is stateless. That is, its {@link #iterator()} method always returns
 * a new {@code Iterator} instance, which can be used to cycle through user accounts from the
 * start. The iterators themselves are stateful. This means, if the client code uses an iterator
 * for a while, but breaks out of the iteration before cycling through all user accounts, the
 * same iterator instance can be used to resume iterating from where it left off.
 */
public class UserIterable implements Iterable<ExportedUserRecord> {

  private final UserSource source;

  UserIterable(@NonNull final FirebaseAuth auth) {
    this(new DefaultUserSource(auth));
  }

  UserIterable(@NonNull UserSource source) {
    this.source = checkNotNull(source, "user source must not be null");
  }

  @Override
  @NonNull
  public Iterator<ExportedUserRecord> iterator() {
    return new UserIterator(source);
  }

  void iterateWithCallback(@NonNull ListUsersCallback callback) {
    try {
      for (ExportedUserRecord user : this) {
        if (!callback.onResult(user)) {
          break;
        }
      }
      callback.onComplete();
    } catch (Exception e) {
      callback.onError(e);
    }
  }

  /**
   * An {@code Iterator} that cycles through user accounts, one at a time. It buffers the
   * last retrieved batch of user accounts in memory. The {@code maxResults} parameter is an
   * upper bound on the batch size.
   */
  private static class UserIterator implements Iterator<ExportedUserRecord> {

    private final UserSource source;
    private ListUsersResult currentBatch;
    private int index = 0;

    private UserIterator(UserSource source) {
      this.source = source;
    }

    @Override
    public boolean hasNext() {
      if (currentBatch == null) {
        // Load the initial batch
        currentBatch = source.fetch(null);
      }
      if (index == currentBatch.getUsers().size()) {
        if (!currentBatch.isEndOfList()) {
          currentBatch = source.fetch(currentBatch.getNextPageToken());
          index = 0;
        } else {
          return false;
        }
      }

      return index < currentBatch.getUsers().size();
    }

    @Override
    public ExportedUserRecord next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return currentBatch.getUsers().get(index++);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove operation not supported");
    }
  }

  interface UserSource {
    @NonNull ListUsersResult fetch(PageToken pageToken);
  }

  static class DefaultUserSource implements UserSource {
    private final FirebaseAuth auth;

    DefaultUserSource(FirebaseAuth auth) {
      this.auth = checkNotNull(auth, "auth must not be null");
    }

    @Override
    public ListUsersResult fetch(PageToken pageToken) {
      try {
        return auth.listUsersAsync(pageToken).get();
      } catch (Exception e) {
        throw new RuntimeException("Error while downloading user accounts", e);
      }
    }
  }
}
