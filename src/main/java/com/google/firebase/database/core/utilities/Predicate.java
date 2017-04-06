package com.google.firebase.database.core.utilities;

public interface Predicate<T> {

  Predicate<Object> TRUE =
      new Predicate<Object>() {
        @Override
        public boolean evaluate(Object object) {
          return true;
        }
      };

  boolean evaluate(T object);
}
