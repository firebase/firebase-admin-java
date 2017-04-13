package com.google.firebase.database.core.persistence;

public class LRUCachePolicy implements CachePolicy {

  private static final long SERVER_UPDATES_BETWEEN_CACHE_SIZE_CHECKS = 1000;
  private static final long MAX_NUMBER_OF_PRUNABLE_QUERIES_TO_KEEP = 1000;
  private static final float PERCENT_OF_QUERIES_TO_PRUNE_AT_ONCE =
      0.2f; // 20% at a time until we're below our max.

  public final long maxSizeBytes;

  public LRUCachePolicy(long maxSizeBytes) {
    this.maxSizeBytes = maxSizeBytes;
  }

  @Override
  public boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries) {
    return currentSizeBytes > maxSizeBytes
        || countOfPrunableQueries > MAX_NUMBER_OF_PRUNABLE_QUERIES_TO_KEEP;
  }

  @Override
  public boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck) {
    return serverUpdatesSinceLastCheck > SERVER_UPDATES_BETWEEN_CACHE_SIZE_CHECKS;
  }

  @Override
  public float getPercentOfQueriesToPruneAtOnce() {
    return PERCENT_OF_QUERIES_TO_PRUNE_AT_ONCE;
  }

  @Override
  public long getMaxNumberOfQueriesToKeep() {
    return MAX_NUMBER_OF_PRUNABLE_QUERIES_TO_KEEP;
  }
}
