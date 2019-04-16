package com.google.firebase;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

public class Rpb {
  public static void main(String[] args) {
    ExecutorService executor = Executors.newCachedThreadPool();
    for (int i = 0; i < 128; i++) {
      final String id = "rpb-" + i;
      executor.execute(() -> loop(id));
    }
    while (true) {
      LockSupport.park();
      System.out.println("unparked");
    }
  }

  private static void loop(String id) {
    FirebaseApp app = FirebaseApp.initializeApp(
            FirebaseOptions.builder()
                    .setDatabaseUrl("https://rpb-staging-1.firebaseio-staging.com")
                    .setCredentials(GoogleCredentials.newBuilder().setAccessToken(
                            new AccessToken("IGNOREME", Date.from(Instant.now().plus(24, ChronoUnit.HOURS)))).build())
                    .build(),
            id
            );
    FirebaseDatabase db = FirebaseDatabase.getInstance(app);
    DatabaseReference parent = db.getReference("users").child(id);

    while (true) {
      db.goOnline();
      List<ApiFuture<Void>> futures = new ArrayList<>();
      for (int i=0; i < 100; i++) {
        futures.add(parent.child("c-" + i).onDisconnect().removeValueAsync());
        futures.add(parent.child("c-" + i).setValueAsync(i));
      }
      try {
        ApiFutures.allAsList(futures).get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      db.goOffline();
    }
  }
}
