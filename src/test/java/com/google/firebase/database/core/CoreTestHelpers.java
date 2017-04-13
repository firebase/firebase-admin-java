package com.google.firebase.database.core;

import com.google.firebase.database.connection.PersistentConnection;

public class CoreTestHelpers {

  public static void freezeContext(Context context) {
    context.freeze();
  }

  public static PersistentConnection getRepoConnection(Repo repo) {
    return repo.getConnection();
  }
}
