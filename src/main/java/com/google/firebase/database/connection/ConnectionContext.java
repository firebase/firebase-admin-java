package com.google.firebase.database.connection;

import com.google.firebase.database.logging.Logger;

import java.util.concurrent.ScheduledExecutorService;

public class ConnectionContext {

  private final ScheduledExecutorService executorService;
  private final ConnectionAuthTokenProvider authTokenProvider;
  private final Logger logger;
  private final boolean persistenceEnabled;
  private final String clientSdkVersion;
  private final String userAgent;

  public ConnectionContext(Logger logger,
                           ConnectionAuthTokenProvider authTokenProvider,
                           ScheduledExecutorService executorService,
                           boolean persistenceEnabled,
                           String clientSdkVersion,
                           String userAgent) {
    this.logger = logger;
    this.authTokenProvider = authTokenProvider;
    this.executorService = executorService;
    this.persistenceEnabled = persistenceEnabled;
    this.clientSdkVersion = clientSdkVersion;
    this.userAgent = userAgent;
  }

  public Logger getLogger() {
    return this.logger;
  }

  public ConnectionAuthTokenProvider getAuthTokenProvider() {
    return this.authTokenProvider;
  }

  public ScheduledExecutorService getExecutorService() {
    return this.executorService;
  }

  public boolean isPersistenceEnabled() {
    return this.persistenceEnabled;
  }

  public String getClientSdkVersion() {
    return this.clientSdkVersion;
  }

  public String getUserAgent() {
    return this.userAgent;
  }
}
