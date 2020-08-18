/*
 * Copyright 2020 Google LLC
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

import com.google.api.gax.paging.Page;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.internal.ListOidcProviderConfigsResponse;
import com.google.firebase.auth.internal.ListProviderConfigsResponse;
import com.google.firebase.auth.internal.ListSamlProviderConfigsResponse;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Represents a page of {@link ProviderConfig} instances.
 *
 * <p>Provides methods for iterating over the provider configs in the current page, and calling up
 * subsequent pages of provider configs.
 *
 * <p>Instances of this class are thread-safe and immutable.
 */
public class ListProviderConfigsPage<T extends ProviderConfig> implements Page<T> {

  static final String END_OF_LIST = "";

  private final ListProviderConfigsResponse<T> currentBatch;
  private final ProviderConfigSource<T> source;
  private final int maxResults;

  private ListProviderConfigsPage(
      @NonNull ListProviderConfigsResponse<T> currentBatch,
      @NonNull ProviderConfigSource<T> source,
      int maxResults) {
    this.currentBatch = checkNotNull(currentBatch);
    this.source = checkNotNull(source);
    this.maxResults = maxResults;
  }

  /**
   * Checks if there is another page of provider configs available to retrieve.
   *
   * @return true if another page is available, or false otherwise.
   */
  @Override
  public boolean hasNextPage() {
    return !END_OF_LIST.equals(currentBatch.getPageToken());
  }

  /**
   * Returns the string token that identifies the next page.
   *
   * <p>Never returns null. Returns empty string if there are no more pages available to be
   * retrieved.
   *
   * @return A non-null string token (possibly empty, representing no more pages)
   */
  @NonNull
  @Override
  public String getNextPageToken() {
    return currentBatch.getPageToken();
  }

  /**
   * Returns the next page of provider configs.
   *
   * @return A new {@link ListProviderConfigsPage} instance, or null if there are no more pages.
   */
  @Nullable
  @Override
  public ListProviderConfigsPage<T> getNextPage() {
    if (hasNextPage()) {
      Factory<T> factory = new Factory<>(source, maxResults, currentBatch.getPageToken());
      try {
        return factory.create();
      } catch (FirebaseAuthException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  /**
   * Returns an {@code Iterable} that facilitates transparently iterating over all the provider
   * configs in the current Firebase project, starting from this page.
   *
   * <p>The {@code Iterator} instances produced by the returned {@code Iterable} never buffers more
   * than one page of provider configs at a time. It is safe to abandon the iterators (i.e. break
   * the loops) at any time.
   *
   * @return a new {@code Iterable} instance.
   */
  @NonNull
  @Override
  public Iterable<T> iterateAll() {
    return new ProviderConfigIterable<>(this);
  }

  /**
   * Returns an {@code Iterable} over the provider configs in this page.
   *
   * @return a {@code Iterable} instance.
   */
  @NonNull
  @Override
  public Iterable<T> getValues() {
    return currentBatch.getProviderConfigs();
  }

  private static class ProviderConfigIterable<T extends ProviderConfig> implements Iterable<T> {

    private final ListProviderConfigsPage<T> startingPage;

    ProviderConfigIterable(@NonNull ListProviderConfigsPage<T> startingPage) {
      this.startingPage = checkNotNull(startingPage, "starting page must not be null");
    }

    @Override
    @NonNull
    public Iterator<T> iterator() {
      return new ProviderConfigIterator<>(startingPage);
    }

    /**
     * An {@link Iterator} that cycles through provider configs, one at a time.
     *
     * <p>It buffers the last retrieved batch of provider configs in memory. The {@code maxResults}
     * parameter is an upper bound on the batch size.
     */
    private static class ProviderConfigIterator<T extends ProviderConfig> implements Iterator<T> {

      private ListProviderConfigsPage<T> currentPage;
      private List<T> batch;
      private int index = 0;

      private ProviderConfigIterator(ListProviderConfigsPage<T> startingPage) {
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
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return batch.get(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove operation not supported");
      }

      private void setCurrentPage(ListProviderConfigsPage<T> page) {
        this.currentPage = checkNotNull(page);
        this.batch = ImmutableList.copyOf(page.getValues());
        this.index = 0;
      }
    }
  }

  /**
   * Represents a source of provider config data that can be queried to load a batch of provider
   * configs.
   */
  interface ProviderConfigSource<T extends ProviderConfig> {
    @NonNull
    ListProviderConfigsResponse<T> fetch(int maxResults, String pageToken)
      throws FirebaseAuthException;
  }

  static class DefaultOidcProviderConfigSource implements ProviderConfigSource<OidcProviderConfig> {

    private final FirebaseUserManager userManager;

    DefaultOidcProviderConfigSource(FirebaseUserManager userManager) {
      this.userManager = checkNotNull(userManager, "User manager must not be null.");
    }

    @Override
    public ListOidcProviderConfigsResponse fetch(int maxResults, String pageToken)
        throws FirebaseAuthException {
      return userManager.listOidcProviderConfigs(maxResults, pageToken);
    }
  }

  static class DefaultSamlProviderConfigSource implements ProviderConfigSource<SamlProviderConfig> {

    private final FirebaseUserManager userManager;

    DefaultSamlProviderConfigSource(FirebaseUserManager userManager) {
      this.userManager = checkNotNull(userManager, "User manager must not be null.");
    }

    @Override
    public ListSamlProviderConfigsResponse fetch(int maxResults, String pageToken)
        throws FirebaseAuthException {
      return userManager.listSamlProviderConfigs(maxResults, pageToken);
    }
  }

  /**
   * A simple factory class for {@link ListProviderConfigsPage} instances.
   *
   * <p>Performs argument validation before attempting to load any provider config data (which is
   * expensive, and hence may be performed asynchronously on a separate thread).
   */
  static class Factory<T extends ProviderConfig> {

    private final ProviderConfigSource<T> source;
    private final int maxResults;
    private final String pageToken;

    Factory(@NonNull ProviderConfigSource<T> source) {
      this(source, FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS, null);
    }

    Factory(
        @NonNull ProviderConfigSource<T> source,
        int maxResults,
        @Nullable String pageToken) {
      checkArgument(
          maxResults > 0 && maxResults <= FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS,
          "maxResults must be a positive integer that does not exceed %s",
          FirebaseUserManager.MAX_LIST_PROVIDER_CONFIGS_RESULTS);
      checkArgument(!END_OF_LIST.equals(pageToken), "invalid end of list page token");
      this.source = checkNotNull(source, "source must not be null");
      this.maxResults = maxResults;
      this.pageToken = pageToken;
    }

    ListProviderConfigsPage<T> create() throws FirebaseAuthException {
      ListProviderConfigsResponse<T> batch = source.fetch(maxResults, pageToken);
      return new ListProviderConfigsPage<>(batch, source, maxResults);
    }
  }
}

