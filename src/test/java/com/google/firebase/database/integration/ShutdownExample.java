package com.google.firebase.database.integration;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger.Level;
import com.google.firebase.database.ValueEventListener;
import java.util.concurrent.Semaphore;

public class ShutdownExample {

  public static void main(String[] args) {
    final Semaphore shutdownLatch = new Semaphore(0);

    FirebaseApp app =
        FirebaseApp.initializeApp(
            new FirebaseOptions.Builder()
                .setDatabaseUrl("http://gsoltis.fblocal.com:9000")
                .build());

    FirebaseDatabase db = FirebaseDatabase.getInstance(app);
    db.setLogLevel(Level.DEBUG);
    DatabaseReference ref = db.getReference();

    ValueEventListener listener =
        ref.child("shutdown")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    Boolean shouldShutdown = snapshot.getValue(Boolean.class);
                    if (shouldShutdown != null && shouldShutdown) {
                      System.out.println("Should shut down");
                      shutdownLatch.release(1);
                    } else {
                      System.out.println("Not shutting down: " + shouldShutdown);
                    }
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    System.err.println("Shouldn't happen");
                  }
                });

    try {
      // Keeps us running until we receive the notification to shut down
      shutdownLatch.acquire(1);
      ref.child("shutdown").removeEventListener(listener);
      db.goOffline();
      System.out.println("Done, should exit");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
