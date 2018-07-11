/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.snippets;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Realtime Database snippets for documentation.
 */
public class FirebaseDatabaseSnippets {

  // CSOFF: MemberName
  // [START user_class]
  public static class User {

    public String date_of_birth;
    public String full_name;
    public String nickname;

    public User(String dateOfBirth, String fullName) {
      // [START_EXCLUDE]
      this(dateOfBirth, fullName, null);
      // [END_EXCLUDE]
    }

    public User(String dateOfBirth, String fullName, String nickname) {
      // [START_EXCLUDE]
      this.date_of_birth = dateOfBirth;
      this.full_name = fullName;
      this.nickname = nickname;
      // [END_EXCLUDE]
    }

  }
  // [END user_class]

  // [START post_class]
  public static class Post {

    public String author;
    public String title;

    public Post(String author, String title) {
      // [START_EXCLUDE]
      this.author = author;
      this.title = title;
      // [END_EXCLUDE]
    }

  }
  // [END post_class]

  // [START dinosaur_class]
  public static class Dinosaur {

    public int height;
    public int weight;

    public Dinosaur(int height, int weight) {
      // [START_EXCLUDE]
      this.height = height;
      this.weight = weight;
      // [END_EXCLUDE]
    }

  }
  // [END dinosaur_class]

  // CSOFF: LineLength
  public void savingData() {
    // [START get_database_and_reference]
    final FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference ref = database.getReference("server/saving-data/fireblog");
    // [END get_database_and_reference]

    // TODO(writer): Show user class with this
    // [START set_user_data_all]
    DatabaseReference usersRef = ref.child("users");

    Map<String, User> users = new HashMap<>();
    users.put("alanisawesome", new User("June 23, 1912", "Alan Turing"));
    users.put("gracehop", new User("December 9, 1906", "Grace Hopper"));

    usersRef.setValueAsync(users);
    // [END set_user_data_all]

    // [START set_user_data_child]
    usersRef.child("alanisawesome").setValueAsync(new User("June 23, 1912", "Alan Turing"));
    usersRef.child("gracehop").setValueAsync(new User("December 9, 1906", "Grace Hopper"));
    // [END set_user_data_child]

    // [START single_user_update_children]
    DatabaseReference hopperRef = usersRef.child("gracehop");
    Map<String, Object> hopperUpdates = new HashMap<>();
    hopperUpdates.put("nickname", "Amazing Grace");

    hopperRef.updateChildrenAsync(hopperUpdates);
    // [END single_user_update_children]

    // [START multi_user_update_children]
    Map<String, Object> userUpdates = new HashMap<>();
    userUpdates.put("alanisawesome/nickname", "Alan The Machine");
    userUpdates.put("gracehop/nickname", "Amazing Grace");

    usersRef.updateChildrenAsync(userUpdates);
    // [END multi_user_update_children]

    // [START multi_user_object_updates]
    Map<String, Object> userNicknameUpdates = new HashMap<>();
    userNicknameUpdates.put("alanisawesome", new User(null, null, "Alan The Machine"));
    userNicknameUpdates.put("gracehop", new User(null, null, "Amazing Grace"));

    usersRef.updateChildrenAsync(userNicknameUpdates);
    // [END multi_user_object_updates]

    // [START adding_completion_callback]
    DatabaseReference dataRef = ref.child("data");
    dataRef.setValue("I'm writing data", new DatabaseReference.CompletionListener() {
      @Override
      public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
        if (databaseError != null) {
          System.out.println("Data could not be saved " + databaseError.getMessage());
        } else {
          System.out.println("Data saved successfully.");
        }
      }
    });
    // [END adding_completion_callback]

    // TODO(writer): Show post class with this
    // [START push_posts]
    DatabaseReference postsRef = ref.child("posts");

    DatabaseReference newPostRef = postsRef.push();
    newPostRef.setValueAsync(new Post("gracehop", "Announcing COBOL, a New Programming Language"));

    // We can also chain the two calls together
    postsRef.push().setValueAsync(new Post("alanisawesome", "The Turing Machine"));
    // [END push_posts]

    // [START getting_post_id]
    // Generate a reference to a new location and add some data using push()
    DatabaseReference pushedPostRef = postsRef.push();

    // Get the unique ID generated by a push()
    String postId = pushedPostRef.getKey();
    // [END getting_post_id]

    // [START save_transaction]
    DatabaseReference upvotesRef = ref.child("server/saving-data/fireblog/posts/-JRHTHaIs-jNPLXOQivY/upvotes");
    upvotesRef.runTransaction(new Transaction.Handler() {
      @Override
      public Transaction.Result doTransaction(MutableData mutableData) {
        Integer currentValue = mutableData.getValue(Integer.class);
        if (currentValue == null) {
          mutableData.setValue(1);
        } else {
          mutableData.setValue(currentValue + 1);
        }

        return Transaction.success(mutableData);
      }

      @Override
      public void onComplete(
          DatabaseError databaseError, boolean committed, DataSnapshot dataSnapshot) {
        System.out.println("Transaction completed");
      }
    });
    // [END save_transaction]
  }

  public void readingData() {
    // TODO(writer): Show Post class
    // [START get_ref_and_read]
    // Get a reference to our posts
    final FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference ref = database.getReference("server/saving-data/fireblog/posts");

    // Attach a listener to read the data at our posts reference
    ref.addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        Post post = dataSnapshot.getValue(Post.class);
        System.out.println(post);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        System.out.println("The read failed: " + databaseError.getCode());
      }
    });
    // [END get_ref_and_read]

    // [START child_added]
    ref.addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        Post newPost = dataSnapshot.getValue(Post.class);
        System.out.println("Author: " + newPost.author);
        System.out.println("Title: " + newPost.title);
        System.out.println("Previous Post ID: " + prevChildKey);
      }

      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
    // [END child_added]

    // [START child_changed]
    ref.addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
        Post changedPost = dataSnapshot.getValue(Post.class);
        System.out.println("The updated post title is: " + changedPost.title);
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
    // [END child_changed]

    // [START child_removed]
    ref.addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
        Post removedPost = dataSnapshot.getValue(Post.class);
        System.out.println("The blog post titled " + removedPost.title + " has been deleted");
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
    // [END child_removed]

    // [START event_guarantees]
    final AtomicInteger count = new AtomicInteger();

    ref.addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        // New child added, increment count
        int newCount = count.incrementAndGet();
        System.out.println("Added " + dataSnapshot.getKey() + ", count is " + newCount);
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
      // [END_EXCLUDE]
    });

    // The number of children will always be equal to 'count' since the value of
    // the dataSnapshot here will include every child_added event triggered before this point.
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        long numChildren = dataSnapshot.getChildrenCount();
        System.out.println(count.get() + " == " + numChildren);
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {}
    });
    // [END event_guarantees]

    // [START remove_listener]
    // Create and attach listener
    ValueEventListener listener = new ValueEventListener() {
      // [START_EXCLUDE]
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
      // [END_EXCLUDE]
    };
    ref.addValueEventListener(listener);

    // Remove listener
    ref.removeEventListener(listener);
    // [END remove_listener]

    // [START single_value_event]
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        // ...
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        // ...
      }
    });
    // [END single_value_event]

    // TODO(writer): Show dinosaur class
    // [START order_by_child]
    final DatabaseReference dinosaursRef = database.getReference("dinosaurs");
    dinosaursRef.orderByChild("height").addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        Dinosaur dinosaur = dataSnapshot.getValue(Dinosaur.class);
        System.out.println(dataSnapshot.getKey() + " was " + dinosaur.height + " meters tall.");
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
      // [END_EXCLUDE]
    });
    // [END order_by_child]

    // [START deeply_nested_query]
    dinosaursRef.orderByChild("dimensions/height").addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        // ...
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
      // [END_EXCLUDE]
    });
    // [END deeply_nested_query]

    // [START order_by_key]
    dinosaursRef.orderByKey().addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println(dataSnapshot.getKey());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
      // [END_EXCLUDE]
    });
    // [END order_by_key]

    // [START order_by_value]
    DatabaseReference scoresRef = database.getReference("scores");
    scoresRef.orderByValue().addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println("The " + dataSnapshot.getKey() + " score is " + dataSnapshot.getValue());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
      // [END_EXCLUDE]
    });
    // [END order_by_value]

    // [START limit_to_last]
    dinosaursRef.orderByChild("weight").limitToLast(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println(dataSnapshot.getKey());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
      // [END_EXCLUDE]
    });
    // [END limit_to_last]

    // [START limit_to_first]
    dinosaursRef.orderByChild("weight").limitToFirst(2).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println(dataSnapshot.getKey());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
      // [END_EXCLUDE]
    });
    // [END limit_to_first]

    // [START limit_order_value]
    scoresRef.orderByValue().limitToFirst(3).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println("The " + dataSnapshot.getKey() + " score is " + dataSnapshot.getValue());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
      // [END_EXCLUDE]
    });
    // [END limit_order_value]

    // [START start_at]
    dinosaursRef.orderByChild("height").startAt(3).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println(dataSnapshot.getKey());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
      // [END_EXCLUDE]
    });
    // [END start_at]

    // [START end_at]
    dinosaursRef.orderByKey().endAt("pterodactyl").addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println(dataSnapshot.getKey());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {
      }

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
      }
      // [END_EXCLUDE]
    });
    // [END end_at]

    // [START start_and_end_at]
    dinosaursRef.orderByKey().startAt("b").endAt("b\uf8ff").addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println(dataSnapshot.getKey());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
      // [END_EXCLUDE]
    });
    // [END start_and_end_at]

    // [START equal_to]
    dinosaursRef.orderByChild("height").equalTo(25).addChildEventListener(new ChildEventListener() {
      @Override
      public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
        System.out.println(dataSnapshot.getKey());
      }

      // [START_EXCLUDE]
      @Override
      public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onChildRemoved(DataSnapshot dataSnapshot) {}

      @Override
      public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

      @Override
      public void onCancelled(DatabaseError databaseError) {}
      // [END_EXCLUDE]
    });
    // [END equal_to]

    // [START complex_combined]
    dinosaursRef.child("stegosaurus").child("height").addValueEventListener(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot stegoHeightSnapshot) {
        Integer favoriteDinoHeight = stegoHeightSnapshot.getValue(Integer.class);
        Query query = dinosaursRef.orderByChild("height").endAt(favoriteDinoHeight).limitToLast(2);
        query.addValueEventListener(new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot dataSnapshot) {
            // Data is ordered by increasing height, so we want the first entry
            DataSnapshot firstChild = dataSnapshot.getChildren().iterator().next();
            System.out.println("The dinosaur just shorter than the stegosaurus is: " + firstChild.getKey());
          }

          @Override
          public void onCancelled(DatabaseError databaseError) {
            // ...
          }
        });
      }

      @Override
      public void onCancelled(DatabaseError databaseError) {
        // ...
      }
    });
    // [END complex_combined]
  }

  public void initializeApp() throws IOException {
    // [START init_admin_sdk_for_db]
    // Fetch the service account key JSON file contents
    FileInputStream serviceAccount = new FileInputStream("path/to/serviceAccount.json");

    // Initialize the app with a service account, granting admin privileges
    FirebaseOptions options = new FirebaseOptions.Builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setDatabaseUrl("https://<databaseName>.firebaseio.com")
        .build();
    FirebaseApp.initializeApp(options);

    // As an admin, the app has access to read and write all data, regardless of Security Rules
    DatabaseReference ref = FirebaseDatabase.getInstance()
        .getReference("restricted_access/secret_document");
    ref.addListenerForSingleValueEvent(new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot dataSnapshot) {
        Object document = dataSnapshot.getValue();
        System.out.println(document);
      }

      @Override
      public void onCancelled(DatabaseError error) {
      }
    });
    // [END init_admin_sdk_for_db]
  }
}