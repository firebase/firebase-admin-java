package com.google.firebase.database.core;

public class Tag {

  private final long tagNumber;

  public Tag(long tagNumber) {
    this.tagNumber = tagNumber;
  }

  public long getTagNumber() {
    return this.tagNumber;
  }

  @Override
  public String toString() {
    return "Tag{" + "tagNumber=" + tagNumber + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Tag tag = (Tag) o;

    if (tagNumber != tag.tagNumber) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return (int) (tagNumber ^ (tagNumber >>> 32));
  }
}
