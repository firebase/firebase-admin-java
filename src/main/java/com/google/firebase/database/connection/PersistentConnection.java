package com.google.firebase.database.connection;

import java.util.List;
import java.util.Map;

public interface PersistentConnection {

  void initialize();

  // Lifecycle

  void shutdown();

  void refreshAuthToken();

  // Auth

  void refreshAuthToken(String token);

  void listen(List<String> path, Map<String, Object> queryParams,
              ListenHashProvider currentHashFn, Long tag,
              RequestResultCallback onComplete);

  // Listens

  void unlisten(List<String> path, Map<String, Object> queryParams);

  void purgeOutstandingWrites();

  // Writes

  void put(List<String> path, Object data, RequestResultCallback onComplete);

  void compareAndPut(List<String> path, Object data, String hash,
                     RequestResultCallback onComplete);

  void merge(List<String> path, Map<String, Object> data, RequestResultCallback onComplete);

  void onDisconnectPut(List<String> path, Object data, RequestResultCallback onComplete);

  // Disconnects

  void onDisconnectMerge(List<String> path, Map<String, Object> updates,
                         RequestResultCallback onComplete);

  void onDisconnectCancel(List<String> path, RequestResultCallback onComplete);

  void interrupt(String reason);

  // Connection management

  void resume(String reason);

  boolean isInterrupted(String reason);

  interface Delegate {

    void onDataUpdate(List<String> path, Object message, boolean isMerge, Long optTag);

    void onRangeMergeUpdate(List<String> path, List<RangeMerge> merges, Long optTag);

    void onConnect();

    void onDisconnect();

    void onAuthStatus(boolean authOk);

    void onServerInfoUpdate(Map<String, Object> updates);
  }

}
