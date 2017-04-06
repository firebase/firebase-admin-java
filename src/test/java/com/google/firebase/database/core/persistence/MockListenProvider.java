package com.google.firebase.database.core.persistence;

import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.core.SyncTree;
import com.google.firebase.database.core.Tag;
import com.google.firebase.database.core.view.QuerySpec;

import java.util.HashSet;
import java.util.Set;

public class MockListenProvider implements SyncTree.ListenProvider {

  private Set<QuerySpec> listens = new HashSet<>();

  @Override
  public void startListening(
      QuerySpec query,
      Tag tag,
      ListenHashProvider hash,
      SyncTree.CompletionListener onListenComplete) {
    listens.add(query);
  }

  @Override
  public void stopListening(QuerySpec query, Tag tag) {
    listens.remove(query);
  }

  public boolean hasListen(QuerySpec query) {
    return listens.contains(query);
  }
}
