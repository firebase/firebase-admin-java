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

package com.google.firebase.auth.multitenancy;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.gax.paging.Page;
import com.google.common.collect.ImmutableList;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.internal.ListTenantsResponse;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Represents a page of {@link Tenant} instances.
 *
 * <p>Provides methods for iterating over the tenants in the current page, and calling up
 * subsequent pages of tenants.
 *
 * <p>Instances of this class are thread-safe and immutable.
 */
public class ListTenantsPage implements Page<Tenant> {

  static final String END_OF_LIST = "";

  private final ListTenantsResponse currentBatch;
  private final TenantSource source;
  private final int maxResults;

  private ListTenantsPage(
      @NonNull ListTenantsResponse currentBatch, @NonNull TenantSource source, int maxResults) {
    this.currentBatch = checkNotNull(currentBatch);
    this.source = checkNotNull(source);
    this.maxResults = maxResults;
  }

  /**
   * Checks if there is another page of tenants available to retrieve.
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
   * Returns the next page of tenants.
   *
   * @return A new {@link ListTenantsPage} instance, or null if there are no more pages.
   */
  @Nullable
  @Override
  public ListTenantsPage getNextPage() {
    if (hasNextPage()) {
      PageFactory factory = new PageFactory(source, maxResults, currentBatch.getPageToken());
      try {
        return factory.create();
      } catch (FirebaseAuthException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  /**
   * Returns an {@code Iterable} that facilitates transparently iterating over all the tenants in
   * the current Firebase project, starting from this page.
   *
   * <p>The {@code Iterator} instances produced by the returned {@code Iterable} never buffers more
   * than one page of tenants at a time. It is safe to abandon the iterators (i.e. break the loops)
   * at any time.
   *
   * @return a new {@code Iterable} instance.
   */
  @NonNull
  @Override
  public Iterable<Tenant> iterateAll() {
    return new TenantIterable(this);
  }

  /**
   * Returns an {@code Iterable} over the tenants in this page.
   *
   * @return a {@code Iterable} instance.
   */
  @NonNull
  @Override
  public Iterable<Tenant> getValues() {
    return currentBatch.getTenants();
  }

  private static class TenantIterable implements Iterable<Tenant> {

    private final ListTenantsPage startingPage;

    TenantIterable(@NonNull ListTenantsPage startingPage) {
      this.startingPage = checkNotNull(startingPage, "starting page must not be null");
    }

    @Override
    @NonNull
    public Iterator<Tenant> iterator() {
      return new TenantIterator(startingPage);
    }

    /**
     * An {@code Iterator} that cycles through tenants, one at a time.
     *
     * <p>It buffers the last retrieved batch of tenants in memory. The {@code maxResults} parameter
     * is an upper bound on the batch size.
     */
    private static class TenantIterator implements Iterator<Tenant> {

      private ListTenantsPage currentPage;
      private List<Tenant> batch;
      private int index = 0;

      private TenantIterator(ListTenantsPage startingPage) {
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
      public Tenant next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return batch.get(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove operation not supported");
      }

      private void setCurrentPage(ListTenantsPage page) {
        this.currentPage = checkNotNull(page);
        this.batch = ImmutableList.copyOf(page.getValues());
        this.index = 0;
      }
    }
  }

  /**
   * Represents a source of tenant data that can be queried to load a batch of tenants.
   */
  interface TenantSource {
    @NonNull
    ListTenantsResponse fetch(int maxResults, String pageToken)
      throws FirebaseAuthException;
  }

  static class DefaultTenantSource implements TenantSource {

    private final FirebaseTenantClient tenantClient;

    DefaultTenantSource(FirebaseTenantClient tenantClient) {
      this.tenantClient = checkNotNull(tenantClient, "Tenant client must not be null.");
    }

    @Override
    public ListTenantsResponse fetch(int maxResults, String pageToken)
        throws FirebaseAuthException {
      return tenantClient.listTenants(maxResults, pageToken);
    }
  }

  /**
   * A simple factory class for {@link ListTenantsPage} instances.
   *
   * <p>Performs argument validation before attempting to load any tenant data (which is expensive,
   * and hence may be performed asynchronously on a separate thread).
   */
  static class PageFactory {

    private final TenantSource source;
    private final int maxResults;
    private final String pageToken;

    PageFactory(@NonNull TenantSource source) {
      this(source, FirebaseTenantClient.MAX_LIST_TENANTS_RESULTS, null);
    }

    PageFactory(@NonNull TenantSource source, int maxResults, @Nullable String pageToken) {
      checkArgument(maxResults > 0 && maxResults <= FirebaseTenantClient.MAX_LIST_TENANTS_RESULTS,
          "maxResults must be a positive integer that does not exceed %s",
          FirebaseTenantClient.MAX_LIST_TENANTS_RESULTS);
      checkArgument(!END_OF_LIST.equals(pageToken), "Invalid end of list page token.");
      this.source = checkNotNull(source, "Source must not be null.");
      this.maxResults = maxResults;
      this.pageToken = pageToken;
    }

    ListTenantsPage create() throws FirebaseAuthException {
      ListTenantsResponse batch = source.fetch(maxResults, pageToken);
      return new ListTenantsPage(batch, source, maxResults);
    }
  }
}

