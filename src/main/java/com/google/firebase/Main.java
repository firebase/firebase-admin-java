package com.google.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Logger;
import com.google.firebase.database.ValueEventListener;

import java.io.FileInputStream;

public class Main {

  public static void main(String[] args) throws Exception {
    runFirebase();
  }

  private static void runFirebase() throws Exception {
    GoogleCredentials credentials = GoogleCredentials.fromStream(
        new FileInputStream("integration_cert.json"));
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(credentials)
        .setDatabaseUrl("https://admin-java-integration.firebaseio.com")
        .build();
    FirebaseApp.initializeApp(options);
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    database.setLogLevel(Logger.Level.DEBUG);

    DatabaseReference foo = database.getReference().child("foo");
    foo.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        System.out.println(snapshot.getValue());
      }

      @Override
      public void onCancelled(DatabaseError error) {

      }
    });
    foo.child("bar").setValueAsync(System.currentTimeMillis()).get();

    database.getReference().child("parent").addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
        System.out.println("[EVENT] ADD " + snapshot.getValue());
      }

      @Override
      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
        System.out.println("[EVENT] CHANGE " + snapshot.getValue());
      }

      @Override
      public void onChildRemoved(DataSnapshot snapshot) {
        System.out.println("[EVENT] DELETE " + snapshot.getValue());
      }

      @Override
      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
        System.out.println("[EVENT] MOVE " + snapshot.getValue());
      }

      @Override
      public void onCancelled(DatabaseError error) {
        System.out.println("ERROR: " + error.getMessage());
      }
    });
    Thread.sleep(3000);
    FirebaseApp.getInstance().delete();
    System.in.read();
  }

}