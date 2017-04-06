package com.google.firebase.database;

import org.junit.Assert;

public class TestChildEventListener implements ChildEventListener {

  @Override
  public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
    Assert.fail("onChildAdded called, but was not expected in Test!");
  }

  @Override
  public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
    Assert.fail("onChildChanged called, but was not expected in Test!");
  }

  @Override
  public void onChildRemoved(DataSnapshot snapshot) {
    Assert.fail("onChildRemoved called, but was not expected in Test!");
  }

  @Override
  public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
    Assert.fail("onChildMoved called, but was not expected in Test!");
  }

  @Override
  public void onCancelled(DatabaseError error) {
    Assert.fail("onCancelled called, but was not expected in Test!");
  }
}
