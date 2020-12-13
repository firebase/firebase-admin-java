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

package com.google.firebase.remoteconfig;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.gax.paging.Page;
import com.google.common.collect.ImmutableList;
import com.google.firebase.internal.NonNull;
import com.google.firebase.remoteconfig.internal.TemplateResponse;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Represents a page of {@link Version} instances. Provides methods for iterating
 * over the versions in the current page, and calling up subsequent pages of versions. Instances of
 * this class are thread-safe and immutable.
 */
public final class ListVersionsPage implements Page<Version> {

  static final String END_OF_LIST = "";

  private final VersionsResultBatch currentBatch;
  private final VersionSource source;
  private final ListVersionsOptions listVersionsOptions;

  private ListVersionsPage(
          @NonNull VersionsResultBatch currentBatch, @NonNull VersionSource source,
          @NonNull ListVersionsOptions listVersionsOptions) {
    this.currentBatch = checkNotNull(currentBatch);
    this.source = checkNotNull(source);
    this.listVersionsOptions = listVersionsOptions;
  }

  /**
   * Checks if there is another page of versions available to retrieve.
   *
   * @return true if another page is available, or false otherwise.
   */
  @Override
  public boolean hasNextPage() {
    return !END_OF_LIST.equals(currentBatch.getNextPageToken());
  }

  /**
   * Returns the next page of versions.
   *
   * @return A new {@link ListVersionsPage} instance, or null if there are no more pages.
   */
  @NonNull
  @Override
  public ListVersionsPage getNextPage() {
    if (hasNextPage()) {
      ListVersionsOptions options;
      if (listVersionsOptions != null) {
        options = listVersionsOptions.toBuilder().setPageToken(currentBatch.getNextPageToken())
                .build();
      } else {
        options = ListVersionsOptions.builder().setPageToken(currentBatch.getNextPageToken())
                .build();
      }
      Factory factory = new Factory(source, options);
      try {
        return factory.create();
      } catch (FirebaseRemoteConfigException e) {
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  /**
   * Returns the string token that identifies the next page. Never returns null. Returns an empty
   * string if there are no more pages available to be retrieved.
   *
   * @return A non-null string token (possibly empty, representing no more pages)
   */
  @NonNull
  @Override
  public String getNextPageToken() {
    return currentBatch.getNextPageToken();
  }

  /**
   * Returns an {@code Iterable} over the versions in this page.
   *
   * @return a {@code Iterable<Version>} instance.
   */
  @NonNull
  @Override
  public Iterable<Version> getValues() {
    return currentBatch.getVersions();
  }

  /**
   * Returns an {@code Iterable} that facilitates transparently iterating over all the versions in
   * the current Firebase project, starting from this page. The {@code Iterator} instances produced
   * by the returned {@code Iterable} never buffers more than one page of versions at a time. It is
   * safe to abandon the iterators (i.e. break the loops) at any time.
   *
   * @return a new {@code Iterable<Version>} instance.
   */
  @NonNull
  @Override
  public Iterable<Version> iterateAll() {
    return new VersionIterable(this);
  }

  private static class VersionIterable implements Iterable<Version> {

    private final ListVersionsPage startingPage;

    VersionIterable(@NonNull ListVersionsPage startingPage) {
      this.startingPage = checkNotNull(startingPage, "starting page must not be null");
    }

    @Override
    @NonNull
    public Iterator<Version> iterator() {
      return new VersionIterable.VersionIterator(startingPage);
    }

    /**
     * An {@code Iterator} that cycles through versions, one at a time. It buffers the
     * last retrieved batch of versions in memory.
     */
    private static class VersionIterator implements Iterator<Version> {

      private ListVersionsPage currentPage;
      private List<Version> batch;
      private int index = 0;

      private VersionIterator(ListVersionsPage startingPage) {
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
      public Version next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return batch.get(index++);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("remove operation not supported");
      }

      private void setCurrentPage(ListVersionsPage page) {
        this.currentPage = checkNotNull(page);
        this.batch = ImmutableList.copyOf(page.getValues());
        this.index = 0;
      }
    }
  }

  static final class VersionsResultBatch {

    private final List<Version> versions;
    private final String nextPageToken;

    VersionsResultBatch(@NonNull List<Version> versions, @NonNull String nextPageToken) {
      this.versions = checkNotNull(versions);
      this.nextPageToken = checkNotNull(nextPageToken); // Can be empty
    }

    @NonNull
    List<Version> getVersions() {
      return versions;
    }

    @NonNull
    String getNextPageToken() {
      return nextPageToken;
    }
  }

  /**
   * Represents a source of Remote Config version data that can be queried to load a batch
   * of versions.
   */
  interface VersionSource {
    @NonNull
    VersionsResultBatch fetch(
            ListVersionsOptions listVersionsOptions) throws FirebaseRemoteConfigException;
  }

  static class DefaultVersionSource implements VersionSource {

    private final FirebaseRemoteConfigClient remoteConfigClient;

    DefaultVersionSource(FirebaseRemoteConfigClient remoteConfigClient) {
      this.remoteConfigClient = checkNotNull(remoteConfigClient,
              "remote config client must not be null");
    }

    @Override
    public VersionsResultBatch fetch(
            ListVersionsOptions listVersionsOptions) throws FirebaseRemoteConfigException {
      TemplateResponse.ListVersionsResponse response = remoteConfigClient
              .listVersions(listVersionsOptions);
      ImmutableList.Builder<Version> builder = ImmutableList.builder();
      if (response.hasVersions()) {
        for (TemplateResponse.VersionResponse versionResponse : response.getVersions()) {
          builder.add(new Version(versionResponse));
        }
      }
      String nextPageToken = response.getNextPageToken() != null
              ? response.getNextPageToken() : END_OF_LIST;
      return new VersionsResultBatch(builder.build(), nextPageToken);
    }
  }

  /**
   * A simple factory class for {@link ListVersionsPage} instances. Performs argument validation
   * before attempting to load any version data (which is expensive, and hence may be performed
   * asynchronously on a separate thread).
   */
  static class Factory {

    private final VersionSource source;
    private final ListVersionsOptions listVersionsOptions;

    Factory(@NonNull VersionSource source) {
      this(source, null);
    }

    Factory(@NonNull VersionSource source, @NonNull ListVersionsOptions listVersionsOptions) {
      this.source = checkNotNull(source, "source must not be null");
      this.listVersionsOptions = listVersionsOptions;
    }

    ListVersionsPage create() throws FirebaseRemoteConfigException {
      VersionsResultBatch batch = source.fetch(listVersionsOptions);
      return new ListVersionsPage(batch, source, listVersionsOptions);
    }
  }
}
