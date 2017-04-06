package com.google.firebase.database;

import com.firebase.security.token.TokenGenerator;
import com.firebase.security.token.TokenOptions;
import com.google.firebase.database.connection.ConnectionAuthTokenProvider;
import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnectionImpl;
import com.google.firebase.database.connection.RangeMerge;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.utilities.DefaultRunLoop;
import com.google.firebase.database.utilities.ParsedUrl;
import com.google.firebase.database.utilities.Utilities;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.firebase.database.utilities.Utilities.hardAssert;
import static org.junit.Assert.fail;

/** A test that stressed the connection lifecycle injecting auth errors, auth expirations etc. */
public class ConnectionLoadTestIT implements PersistentConnectionImpl.Delegate {

  private static final long EXPIRATION_DELAY_MS = 2000;
  private static final long TIME_UNIT_MS = 50;
  private static final double AUTH_FAIL_PROBABILITY = 0.1;
  private Random random = new Random();
  private Semaphore stopSemaphore = new Semaphore(0);
  private boolean connected = false;
  private boolean interrupted = false;
  private ScheduledExecutorService executorService;
  private PersistentConnectionImpl connection;
  private TestAuthTokenProvider authTokenProvider;

  public ConnectionLoadTestIT() {
    final DefaultRunLoop runLoop =
        new DefaultRunLoop() {
          @Override
          public void handleException(Throwable e) {
            e.printStackTrace();
            stopSemaphore.release();
            fail("Exception encountered");
          }
        };
    this.executorService = runLoop.getExecutorService();
    authTokenProvider = new TestAuthTokenProvider(this.executorService);
    com.google.firebase.database.logging.Logger logger =
        new DefaultLogger(Logger.Level.DEBUG, null);
    // TODO(dimond): fix version and user agent?
    ConnectionContext context =
        new ConnectionContext(
            logger, authTokenProvider, this.executorService, false, "x.x.x", "user-agent");
    ParsedUrl url = Utilities.parseUrl(TestConstants.TEST_NAMESPACE);
    HostInfo hostInfo =
        new HostInfo(url.repoInfo.host, url.repoInfo.namespace, url.repoInfo.secure);
    this.connection = new PersistentConnectionImpl(context, hostInfo, this);

    // Make sure the connection goes from idle to unidle again
    // Based on PersistentConnectionImpl.IDLE_TIMEOUT + 5 seconds
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        this.executorService.scheduleAtFixedRate(
            new Runnable() {
              @Override
              public void run() {
                connection.put(Arrays.asList("foobar"), "value", null);
              }
            },
            65,
            65,
            TimeUnit.SECONDS);
  }

  public void run() {
    this.connection.initialize();
    scheduleNextOp();
  }

  private void scheduleNextOp() {
    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        this.executorService.schedule(
            new Runnable() {
              @Override
              public void run() {
                nextOp();
              }
            },
            random.nextInt((int) TIME_UNIT_MS * 5),
            TimeUnit.MILLISECONDS);
  }

  private void waitForCompletion() {
    try {
      stopSemaphore.acquire();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    this.connection.shutdown();
    this.executorService.shutdownNow();
  }

  private void nextOp() {
    final int numOps = 4;
    int nextOp = this.random.nextInt(numOps);
    if (nextOp == 0) {
      // interrupt
      if (this.interrupted) {
        this.connection.resume("interrupt");
        this.interrupted = false;
      } else {
        this.connection.interrupt("interrupt");
        this.interrupted = true;
      }
    } else if (nextOp == 1) {
      this.connection.refreshAuthToken();
    } else if (nextOp == 2) {
      authTokenProvider.generateToken();
      this.connection.refreshAuthToken(authTokenProvider.currentToken);
    } else if (nextOp == 3) {
      this.connection.injectConnectionFailure();
    } else {
      fail("Unknown op!");
    }
    scheduleNextOp();
  }

  @Override
  public void onDataUpdate(List<String> path, Object message, boolean isMerge, Long optTag) {
    // meh
  }

  @Override
  public void onRangeMergeUpdate(List<String> path, List<RangeMerge> merges, Long optTag) {
    // meh
  }

  @Override
  public void onConnect() {
    hardAssert(!this.connected);
    this.connected = true;
  }

  @Override
  public void onDisconnect() {
    // this.connected can be false if the connection is interrupted or the connection attempt
    // fails before the connection was established. This is probably fine...
    //hardAssert(this.connected);
    this.connected = false;
  }

  @Override
  public void onAuthStatus(boolean authOk) {
    // meh
  }

  @Override
  public void onServerInfoUpdate(Map<String, Object> updates) {
    // meh
  }

  @Test
  @Ignore
  public void runLoadTestForever() {
    ConnectionLoadTestIT loadTester = new ConnectionLoadTestIT();
    loadTester.run();
    loadTester.waitForCompletion();
  }

  private static class TestAuthTokenProvider implements ConnectionAuthTokenProvider {

    private String currentToken;
    private long currentExpirationTime;
    private Random random;
    private BlockingQueue<Runnable> queue;
    private ScheduledExecutorService scheduledExecutorService;

    public TestAuthTokenProvider(ScheduledExecutorService service) {
      this.scheduledExecutorService = service;
      this.random = new Random();
      this.queue = new LinkedBlockingQueue<>();

      new Thread(
              new Runnable() {
                @Override
                public void run() {
                  while (true) {
                    try {
                      Runnable runnable = queue.take();
                      runnable.run();
                    } catch (InterruptedException e) {
                      e.printStackTrace();
                      throw new RuntimeException(e);
                    }
                  }
                }
              })
          .start();
    }

    private void generateToken() {
      this.currentExpirationTime =
          System.currentTimeMillis() + random.nextInt((int) EXPIRATION_DELAY_MS);
      TokenOptions options = new TokenOptions();
      options.setExpires(new Date(this.currentExpirationTime));
      TokenGenerator generator = new TokenGenerator(TestHelpers.getTestSecret());
      Map<String, Object> authObj = new HashMap<>();
      authObj.put("uid", "test-uid");
      this.currentToken = generator.createToken(authObj, options);
    }

    @Override
    public void getToken(final boolean forceRefresh, final GetTokenCallback callback) {
      queue.add(
          new Runnable() {
            @Override
            public void run() {
              try {
                Thread.sleep(random.nextInt((int) TIME_UNIT_MS));
              } catch (InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
              }
              if (System.currentTimeMillis() > currentExpirationTime || forceRefresh) {
                generateToken();
              }
              scheduledExecutorService.execute(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (random.nextDouble() < AUTH_FAIL_PROBABILITY) {
                        callback.onError("fail");
                      } else {
                        callback.onSuccess(currentToken);
                      }
                    }
                  });
            }
          });
    }
  }
}
