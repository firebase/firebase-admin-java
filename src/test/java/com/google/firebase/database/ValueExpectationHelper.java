package com.google.firebase.database;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * User: greg Date: 6/4/13 Time: 2:45 PM
 */
public class ValueExpectationHelper {

  private static class QueryAndListener {

    public Query query;
    public ValueEventListener listener;

    public QueryAndListener(Query query, ValueEventListener listener) {
      this.query = query;
      this.listener = listener;
    }
  }

  private Semaphore semaphore = new Semaphore(0);
  private int count = 0;
  private List<QueryAndListener> expectations = new ArrayList<>();

  public void add(final Query query, final Object expected) {
    count++;
    ValueEventListener listener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Object result = snapshot.getValue();
                // Hack to handle race condition in initial data
                if (DeepEquals.deepEquals(expected, result)) {
                  // We may pass through intermediate states, but we should end up with the correct
                  // state
                  semaphore.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Listen cancelled");
              }
            });
    expectations.add(new QueryAndListener(query, listener));
  }

  public void waitForEvents() throws InterruptedException {
    TestHelpers.waitFor(semaphore, count);
    Iterator<QueryAndListener> iter = expectations.iterator();
    while (iter.hasNext()) {
      QueryAndListener pair = iter.next();
      pair.query.removeEventListener(pair.listener);
    }
    expectations.clear();
  }
}
