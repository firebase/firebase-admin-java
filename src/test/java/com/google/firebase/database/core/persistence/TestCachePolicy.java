package com.google.firebase.database.core.persistence;

public class TestCachePolicy implements CachePolicy {

  private boolean timeToPrune = false;
  private final float percentToPruneAtOnce;
  private final long maxNumberToKeep;

  public TestCachePolicy(float percentToPruneAtOnce, long maxNumberToKeep) {
    this.percentToPruneAtOnce = percentToPruneAtOnce;
    this.maxNumberToKeep = maxNumberToKeep;
  }

  public void pruneOnNextServerUpdate() {
    timeToPrune = true;
  }

  @Override
  public boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries) {
    if (timeToPrune) {
      timeToPrune = false;
      return true;
    } else {
      return false;
    }
  }

  @Override
  public boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck) {
    return true;
  }

  @Override
  public float getPercentOfQueriesToPruneAtOnce() {
    return percentToPruneAtOnce;
  }

  @Override
  public long getMaxNumberOfQueriesToKeep() {
    return maxNumberToKeep;
  }
}
