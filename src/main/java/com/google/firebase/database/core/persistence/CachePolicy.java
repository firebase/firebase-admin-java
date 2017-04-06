package com.google.firebase.database.core.persistence;

public interface CachePolicy {

  CachePolicy NONE =
      new CachePolicy() {
        @Override
        public boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries) {
          return false;
        }

        @Override
        public boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck) {
          return false;
        }

        @Override
        public float getPercentOfQueriesToPruneAtOnce() {
          return 0;
        }

        @Override
        public long getMaxNumberOfQueriesToKeep() {
          return Long.MAX_VALUE;
        }
      };

  boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries);

  boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck);

  float getPercentOfQueriesToPruneAtOnce();

  long getMaxNumberOfQueriesToKeep();
}
