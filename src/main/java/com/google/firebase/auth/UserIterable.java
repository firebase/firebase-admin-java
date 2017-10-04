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

public class UserIterable implements Iterable<ExportedUserRecord> {

  private static final int MAX_LIST_USERS_RESULTS = 1000;

  private final Iterator<ExportedUserRecord> iterator;

  UserIterable(@NonNull UserFetcher downloader) {
    this.iterator = new UserIterator(downloader, MAX_LIST_USERS_RESULTS);
  }

  UserIterable(@NonNull UserFetcher downloader, int maxResults) {
    this.iterator = new UserIterator(downloader, maxResults);
  }

  @Override
  public Iterator<ExportedUserRecord> iterator() {
    return this.iterator;
  }

  private static class UserBatchIterator implements Iterator<List<ExportedUserRecord>> {

    private final UserFetcher fetcher;
    private final int maxResults;
    private UserFetcher.FetchResult fetchResult;

    private UserBatchIterator(UserFetcher fetcher, int maxResults) {
      this.fetcher = checkNotNull(fetcher, "user fetcher must not be null");
      checkArgument(maxResults > 0 && maxResults <= MAX_LIST_USERS_RESULTS,
          "max results must be a non-zero positive value which must not exceed "
              + MAX_LIST_USERS_RESULTS);
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
        fetchResult = fetcher.fetch(maxResults, pageToken);
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

  private static class UserIterator implements Iterator<ExportedUserRecord> {

    private final Iterator<List<ExportedUserRecord>> batchIterator;
    private List<ExportedUserRecord> currentBatch = ImmutableList.of();
    private int index = 0;

    private UserIterator(UserFetcher downloader, int maxResults) {
      this.batchIterator = new UserBatchIterator(downloader, maxResults);
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
