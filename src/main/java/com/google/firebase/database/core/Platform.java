package com.google.firebase.database.core;

import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.logging.Logger;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public interface Platform {

  String DEVICE = "AdminJava";

  Logger newLogger(Context ctx, Logger.Level level, List<String> components);

  EventTarget newEventTarget(Context ctx);

  RunLoop newRunLoop(Context ctx);

  AuthTokenProvider newAuthTokenProvider(ScheduledExecutorService executorService);

  PersistentConnection newPersistentConnection(
      Context context,
      ConnectionContext connectionContext,
      HostInfo info,
      PersistentConnection.Delegate delegate);

  String getUserAgent(Context ctx);

  String getPlatformVersion();

  PersistenceManager createPersistenceManager(Context ctx, String firebaseId);
}
