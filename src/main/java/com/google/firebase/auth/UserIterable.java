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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * An {@code Iterable} that enables iterating over the user accounts of a Firebase project. It can
 * be used to obtain an {@code Iterator} over the user accounts. Or it can be used directly as
 * the target of a foreach loop.
 *
 * <p>The {@code maxResults} parameter governs the maximum number of user accounts an iterator is
 * allowed to keep in memory during iteration. It also controls the number of user accounts to
 * be retrieved in a single RPC call. The returned iterators transparently page through batches
 * of user accounts. No RPC calls are made until an iterator is used (i.e. {@code hasNext()}
 * method is called).
 *
 * <p>This {@code Iterable} is stateless. That is, its {@link #iterator()} method always returns
 * a new {@code Iterator} instance, which can be used to cycle through user accounts from the
 * start. The iterators themselves are stateful. This means, if the client code uses an iterator
 * for a while, but breaks out of the iteration before cycling through all user accounts, the
 * same iterator instance can be used to resume iterating from where it left off.
 */
public class UserIterable implements Iterable<ExportedUserRecord> {

  static final int MAX_LIST_USERS_RESULTS = 1000;

  private final UserSource source;
  private final int maxResults;

  UserIterable(@NonNull UserSource source) {
    this(source, MAX_LIST_USERS_RESULTS);
  }

  UserIterable(@NonNull UserSource source, int maxResults) {
    this.source = checkNotNull(source, "user source must not be null");
    checkArgument(maxResults > 0 && maxResults <= MAX_LIST_USERS_RESULTS,
        "max results must be a non-zero positive value which must not exceed "
            + MAX_LIST_USERS_RESULTS);
    this.maxResults = maxResults;
  }

  @Override
  @NonNull
  public Iterator<ExportedUserRecord> iterator() {
    return new UserIterator(source, maxResults);
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
   * An {@code Iterator} that cycles through batches of user accounts.
   */
  private static class UserBatchIterator implements Iterator<List<ExportedUserRecord>> {

    private final UserSource source;
    private final int maxResults;
    private UserSource.FetchResult fetchResult;

    private UserBatchIterator(UserSource source, int maxResults) {
      this.source = source;
      this.maxResults = maxResults;
    }

    @Override
    public boolean hasNext() {
      return fetchResult == null || !fetchResult.isEndOfList();
    }

    @Override
    public List<ExportedUserRecord> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      String pageToken = fetchResult != null ? fetchResult.getNextPageToken() : null;
      try {
        fetchResult = source.fetch(maxResults, pageToken);
        return fetchResult.getUsers();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove operation not supported");
    }
  }

  /**
   * An {@code Iterator} that cycles through user accounts, one at a time. This uses
   * {@link UserBatchIterator} under the hood to cycle through batches of users. It buffers the
   * last retrieved batch of user accounts in memory. The {@code maxResults} parameter is an
   * upper bound on the batch size.
   */
  private static class UserIterator implements Iterator<ExportedUserRecord> {

    private final Iterator<List<ExportedUserRecord>> batchIterator;
    private List<ExportedUserRecord> currentBatch = ImmutableList.of();
    private int index = 0;

    private UserIterator(UserSource source, int maxResults) {
      this.batchIterator = new UserBatchIterator(source, maxResults);
    }

    @Override
    public boolean hasNext() {
      if (index == currentBatch.size()) {
        if (batchIterator.hasNext()) {
          currentBatch = batchIterator.next();
          index = 0;
        } else {
          return false;
        }
      }

      return index < currentBatch.size();
    }

    @Override
    public ExportedUserRecord next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return currentBatch.get(index++);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove operation not supported");
    }
  }
}
