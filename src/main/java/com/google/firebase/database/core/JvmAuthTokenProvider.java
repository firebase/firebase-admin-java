package com.google.firebase.database.core;

import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.database.util.GAuthToken;
import com.google.firebase.internal.AuthStateListener;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.NonNull;
import com.google.firebase.tasks.OnCompleteListener;
import com.google.firebase.tasks.Task;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.firebase.internal.Preconditions.checkNotNull;

public class JvmAuthTokenProvider implements AuthTokenProvider {

  private final ScheduledExecutorService executorService;
  private final FirebaseApp firebaseApp;

  public JvmAuthTokenProvider(FirebaseApp firebaseApp, ScheduledExecutorService executorService) {
    this.executorService = executorService;
    this.firebaseApp = firebaseApp;
  }

  private static String wrapOAuthToken(FirebaseApp firebaseApp, GetTokenResult result) {
    String oauthToken = result.getToken();
    if (oauthToken == null) {
      // This shouldn't happen in the actual production SDK, but can happen in tests.
      return null;
    } else {
      Map<String, Object> authVariable = firebaseApp.getOptions()
          .getDatabaseAuthVariableOverride();
      GAuthToken gAuthToken = new GAuthToken(oauthToken, authVariable);
      return gAuthToken.serializeToString();
    }
  }

  @Override
  public void getToken(boolean forceRefresh, final GetTokenCompletionListener listener) {
    ImplFirebaseTrampolines.getToken(firebaseApp, forceRefresh)
        .addOnCompleteListener(
            this.executorService,
            new OnCompleteListener<GetTokenResult>() {
              @Override
              public void onComplete(@NonNull Task<GetTokenResult> task) {
                if (task.isSuccessful()) {
                  listener.onSuccess(wrapOAuthToken(firebaseApp, task.getResult()));
                } else {
                  listener.onError(task.getException().toString());
                }
              }
            });
  }

  @Override
  public void addTokenChangeListener(TokenChangeListener listener) {
    ImplFirebaseTrampolines.addAuthStateChangeListener(firebaseApp, wrap(listener));
  }

  @Override
  public void removeTokenChangeListener(TokenChangeListener listener) {
    ImplFirebaseTrampolines.removeAuthStateChangeListener(firebaseApp, wrap(listener));
  }

  private AuthStateListener wrap(TokenChangeListener listener) {
    return new TokenChangeListenerWrapper(listener, firebaseApp, executorService);
  }

  /**
   * Wraps a TokenChangeListener instance inside a FirebaseApp.AuthStateListener. Equality
   * comparisons are delegated to the TokenChangeListener so that listener addition and removal
   * will work as expected in FirebaseApp.
   */
  private static class TokenChangeListenerWrapper implements AuthStateListener {

    private final TokenChangeListener listener;
    private final FirebaseApp firebaseApp;
    private final ScheduledExecutorService executorService;

    TokenChangeListenerWrapper(
        TokenChangeListener listener,
        FirebaseApp firebaseApp,
        ScheduledExecutorService executorService) {
      this.listener = checkNotNull(listener, "Listener must not be null");
      this.firebaseApp = checkNotNull(firebaseApp, "FirebaseApp must not be null");
      this.executorService = checkNotNull(executorService, "ExecutorService must not be null");
    }

    @Override
    public void onAuthStateChanged(final GetTokenResult tokenResult) {
      // Notify the TokenChangeListener on database's thread pool to make sure that
      // all database work happens on database worker threads.
      executorService.execute(
          new Runnable() {
            @Override
            public void run() {
              listener.onTokenChange(wrapOAuthToken(firebaseApp, tokenResult));
            }
          });
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj != null
          && obj instanceof TokenChangeListenerWrapper
          && ((TokenChangeListenerWrapper) obj).listener.equals(listener);
    }
  }
}

