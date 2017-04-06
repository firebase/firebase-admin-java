package com.google.firebase.database.core.utilities;

public interface Predicate<T> {

  boolean evaluate(T object);

  Predicate<Object> TRUE =
      new Predicate<Object>() {
        @Override
        public boolean evaluate(Object object) {
          return true;
        }
      };
}
