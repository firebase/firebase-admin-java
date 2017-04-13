package com.google.firebase.database.utilities;

public class Pair<T, U> {

  private final T first;
  private final U second;

  public Pair(T first, U second) {
    this.first = first;
    this.second = second;
  }

  public T getFirst() {
    return first;
  }

  public U getSecond() {
    return second;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Pair pair = (Pair) o;

    if (first != null ? !first.equals(pair.first) : pair.first != null) {
      return false;
    }
    if (second != null ? !second.equals(pair.second) : pair.second != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = first != null ? first.hashCode() : 0;
    result = 31 * result + (second != null ? second.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Pair(" + first + "," + second + ")";
  }
}
