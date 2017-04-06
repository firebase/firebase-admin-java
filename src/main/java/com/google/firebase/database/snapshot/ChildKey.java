package com.google.firebase.database.snapshot;

import com.google.firebase.database.utilities.Utilities;

public class ChildKey implements Comparable<ChildKey> {

  private static final ChildKey MIN_KEY = new ChildKey("[MIN_KEY]");
  private static final ChildKey MAX_KEY = new ChildKey("[MAX_KEY]");
  // Singleton for priority child keys
  private static final ChildKey PRIORITY_CHILD_KEY = new ChildKey(".priority");
  private static final ChildKey INFO_CHILD_KEY = new ChildKey(".info");
  private final String key;

  private ChildKey(String key) {
    this.key = key;
  }

  public static ChildKey getMinName() {
    return MIN_KEY;
  }

  public static ChildKey getMaxName() {
    return MAX_KEY;
  }

  public static ChildKey getPriorityKey() {
    return PRIORITY_CHILD_KEY;
  }

  public static ChildKey getInfoKey() {
    return INFO_CHILD_KEY;
  }

  public static ChildKey fromString(String key) {
    Integer intValue = Utilities.tryParseInt(key);
    if (intValue != null) {
      return new IntegerChildKey(key, intValue);
    } else if (key.equals(".priority")) {
      return PRIORITY_CHILD_KEY;
    } else {
      assert !key.contains("/");
      return new ChildKey(key);
    }
  }

  public String asString() {
    return this.key;
  }

  public boolean isPriorityChildName() {
    return this == PRIORITY_CHILD_KEY;
  }

  protected boolean isInt() {
    return false;
  }

  protected int intValue() {
    return 0;
  }

  @Override
  public int compareTo(ChildKey other) {
    if (this == other) {
      return 0;
    } else if (this == MIN_KEY || other == MAX_KEY) {
      return -1;
    } else if (other == MIN_KEY || this == MAX_KEY) {
      return 1;
    } else if (this.isInt()) {
      if (other.isInt()) {
        int cmp = Utilities.compareInts(this.intValue(), other.intValue());
        return cmp == 0 ? Utilities.compareInts(this.key.length(), other.key.length()) : cmp;
      } else {
        return -1;
      }
    } else if (other.isInt()) {
      return 1;
    } else {
      return this.key.compareTo(other.key);
    }
  }

  @Override
  public String toString() {
    return "ChildKey(\"" + this.key + "\")";
  }

  @Override
  public int hashCode() {
    return this.key.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ChildKey)) {
      return false;
    }
    if (this == obj) {
      return true;
    }
    ChildKey other = (ChildKey) obj;
    return this.key.equals(other.key);
  }

  private static class IntegerChildKey extends ChildKey {

    private final int intValue;

    IntegerChildKey(String name, int intValue) {
      super(name);
      this.intValue = intValue;
    }

    @Override
    protected boolean isInt() {
      return true;
    }

    @Override
    protected int intValue() {
      return this.intValue;
    }

    @Override
    public String toString() {
      return "IntegerChildName(\"" + super.key + "\")";
    }
  }
}
