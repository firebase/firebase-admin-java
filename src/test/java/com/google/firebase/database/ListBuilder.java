package com.google.firebase.database;

import java.util.ArrayList;
import java.util.List;

/** User: greg Date: 5/29/13 Time: 11:45 AM */
public class ListBuilder {

  private List<Object> list = new ArrayList<>();

  public ListBuilder put(Object o) {
    list.add(o);
    return this;
  }

  public List<Object> build() {
    return list;
  }
}
