package com.google.firebase.database.connection;

import java.util.List;
import java.util.Map;

public interface PersistentConnection {

  interface Delegate {

    void onDataUpdate(List<String> path, Object message, boolean isMerge, Long optTag);

    void onRangeMergeUpdate(List<String> path, List<RangeMerge> merges, Long optTag);

    void onConnect();

    void onDisconnect();

    void onAuthStatus(boolean authOk);

    void onServerInfoUpdate(Map<String, Object> updates);
  }

  // Lifecycle

  void initialize();

  void shutdown();

  // Auth

  void refreshAuthToken();

  void refreshAuthToken(String token);

  // Listens

  void listen(List<String> path, Map<String, Object> queryParams,
      ListenHashProvider currentHashFn, Long tag,
      RequestResultCallback onComplete);

  void unlisten(List<String> path, Map<String, Object> queryParams);

  // Writes

  void purgeOutstandingWrites();

  void put(List<String> path, Object data, RequestResultCallback onComplete);

  void compareAndPut(List<String> path, Object data, String hash,
      RequestResultCallback onComplete);

  void merge(List<String> path, Map<String, Object> data, RequestResultCallback onComplete);

  // Disconnects

  void onDisconnectPut(List<String> path, Object data, RequestResultCallback onComplete);

  void onDisconnectMerge(List<String> path, Map<String, Object> updates,
      RequestResultCallback onComplete);

  void onDisconnectCancel(List<String> path, RequestResultCallback onComplete);

  // Connection management

  void interrupt(String reason);

  void resume(String reason);

  boolean isInterrupted(String reason);

}
