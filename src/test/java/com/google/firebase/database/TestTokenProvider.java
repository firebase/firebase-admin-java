package com.google.firebase.database;

import com.google.firebase.database.core.AuthTokenProvider;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestTokenProvider implements AuthTokenProvider {

  private String token;
  private String nextToken;
  private Set<AuthTokenProvider.TokenChangeListener> listeners = new HashSet<>();
  private ScheduledExecutorService executorService;

  public TestTokenProvider(ScheduledExecutorService executorService) {
    this.executorService = executorService;
  }

  public void setToken(String token) {
    this.token = token;
    for (final TokenChangeListener listener : this.listeners) {
      this.executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              listener.onTokenChange();
            }
          });
    }
  }

  public void setNextToken(String token) {
    this.nextToken = token;
  }

  @Override
  public void getToken(boolean forceRefresh, final GetTokenCompletionListener listener) {
    if (forceRefresh && this.nextToken != null) {
      this.token = nextToken;
    }
    final String currentToken = this.token;
    // Make sure to delay the callback by a short delay to test there are no race
    // conditions through reordering of other operations after getToken call.
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        this.executorService.schedule(
            new Runnable() {
              @Override
              public void run() {
                listener.onSuccess(currentToken);
              }
            },
            10,
            TimeUnit.MILLISECONDS);
  }

  @Override
  public void addTokenChangeListener(TokenChangeListener listener) {
    this.listeners.add(listener);
  }

  @Override
  public void removeTokenChangeListener(TokenChangeListener listener) {
    this.listeners.remove(listener);
  }
}
