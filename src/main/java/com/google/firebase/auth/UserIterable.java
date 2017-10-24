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

import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;
import java.util.Iterator;
import java.util.List;
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
class UserIterable implements Iterable<ExportedUserRecord> {

  private final ListUsersPage startingPage;

  UserIterable(@NonNull ListUsersPage startingPage) {
    this.startingPage = checkNotNull(startingPage, "starting page must not be null");
  }

  @Override
  @NonNull
  public Iterator<ExportedUserRecord> iterator() {
    return new UserIterator(startingPage);
  }

  /**
   * An {@code Iterator} that cycles through user accounts, one at a time. It buffers the
   * last retrieved batch of user accounts in memory. The {@code maxResults} parameter is an
   * upper bound on the batch size.
   */
  private static class UserIterator implements Iterator<ExportedUserRecord> {

    private ListUsersPage currentPage;
    private List<ExportedUserRecord> batch;
    private int index = 0;

    private UserIterator(ListUsersPage startingPage) {
      setCurrentPage(startingPage);
    }

    @Override
    public boolean hasNext() {
      if (index == batch.size()) {
        if (currentPage.hasNextPage()) {
          setCurrentPage(currentPage.getNextPage());
        } else {
          return false;
        }
      }

      return index < batch.size();
    }

    @Override
    public ExportedUserRecord next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return batch.get(index++);
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove operation not supported");
    }

    private void setCurrentPage(ListUsersPage page) {
      this.currentPage = page;
      this.batch = ImmutableList.copyOf(page.getValues());
      this.index = 0;
    }
  }
}
