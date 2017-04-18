package com.google.firebase.database;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.RepoInfo;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;

/**
 * Internal helpers in com.google.firebase.database package (for use by core, tests, etc.)
 *
 * @hide
 */
public class InternalHelpers {

  /** So Repo, etc. can create DatabaseReference instances. */
  public static DatabaseReference createReference(Repo repo, Path path) {
    return new DatabaseReference(repo, path);
  }

  /** So Repo, etc. can create DataSnapshots. */
  public static DataSnapshot createDataSnapshot(DatabaseReference ref, IndexedNode node) {
    return new DataSnapshot(ref, node);
  }

  /** So Repo can create FirebaseDatabase objects to keep legacy tests working. */
  public static FirebaseDatabase createDatabaseForTests(
      FirebaseApp app, RepoInfo repoInfo, DatabaseConfig config) {
    return FirebaseDatabase.createForTests(app, repoInfo, config);
  }

  /** For Repo to create MutableData objects. */
  public static MutableData createMutableData(Node node) {
    return new MutableData(node);
  }

  /**
   * For Repo to check if the database has been destroyed.
   */
  public static void checkNotDestroyed(Repo repo) {
    repo.getDatabase().checkNotDestroyed();
  }
}
