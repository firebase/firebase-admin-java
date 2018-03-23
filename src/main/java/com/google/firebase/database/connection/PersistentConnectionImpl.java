/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.database.connection;

import static com.google.firebase.database.connection.ConnectionUtils.hardAssert;

import com.google.firebase.database.connection.util.RetryHelper;
import com.google.firebase.database.util.GAuthToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistentConnectionImpl implements Connection.Delegate, PersistentConnection {

  private static final String REQUEST_ERROR = "error";
  private static final String REQUEST_QUERIES = "q";
  private static final String REQUEST_TAG = "t";
  private static final String REQUEST_STATUS = "s";
  private static final String REQUEST_PATH = "p";
  private static final String REQUEST_NUMBER = "r";
  private static final String REQUEST_PAYLOAD = "b";
  private static final String REQUEST_COUNTERS = "c";
  private static final String REQUEST_DATA_PAYLOAD = "d";
  private static final String REQUEST_DATA_HASH = "h";
  private static final String REQUEST_COMPOUND_HASH = "ch";
  private static final String REQUEST_COMPOUND_HASH_PATHS = "ps";
  private static final String REQUEST_COMPOUND_HASH_HASHES = "hs";
  private static final String REQUEST_CREDENTIAL = "cred";
  private static final String REQUEST_AUTHVAR = "authvar";
  private static final String REQUEST_ACTION = "a";
  private static final String REQUEST_ACTION_STATS = "s";
  private static final String REQUEST_ACTION_QUERY = "q";
  private static final String REQUEST_ACTION_PUT = "p";
  private static final String REQUEST_ACTION_MERGE = "m";
  private static final String REQUEST_ACTION_QUERY_UNLISTEN = "n";
  private static final String REQUEST_ACTION_ONDISCONNECT_PUT = "o";
  private static final String REQUEST_ACTION_ONDISCONNECT_MERGE = "om";
  private static final String REQUEST_ACTION_ONDISCONNECT_CANCEL = "oc";
  private static final String REQUEST_ACTION_AUTH = "auth";
  private static final String REQUEST_ACTION_GAUTH = "gauth";
  private static final String REQUEST_ACTION_UNAUTH = "unauth";
  private static final String REQUEST_NOAUTH = "noauth";
  private static final String RESPONSE_FOR_REQUEST = "b";
  private static final String SERVER_ASYNC_ACTION = "a";
  private static final String SERVER_ASYNC_PAYLOAD = "b";
  private static final String SERVER_ASYNC_DATA_UPDATE = "d";
  private static final String SERVER_ASYNC_DATA_MERGE = "m";
  private static final String SERVER_ASYNC_DATA_RANGE_MERGE = "rm";
  private static final String SERVER_ASYNC_AUTH_REVOKED = "ac";
  private static final String SERVER_ASYNC_LISTEN_CANCELLED = "c";
  private static final String SERVER_ASYNC_SECURITY_DEBUG = "sd";
  private static final String SERVER_DATA_UPDATE_PATH = "p";
  private static final String SERVER_DATA_UPDATE_BODY = "d";
  private static final String SERVER_DATA_START_PATH = "s";
  private static final String SERVER_DATA_END_PATH = "e";
  private static final String SERVER_DATA_RANGE_MERGE = "m";
  private static final String SERVER_DATA_TAG = "t";
  private static final String SERVER_DATA_WARNINGS = "w";
  private static final String SERVER_RESPONSE_DATA = "d";
  /** Delay after which a established connection is considered successful. */
  private static final long SUCCESSFUL_CONNECTION_ESTABLISHED_DELAY = 30 * 1000;

  private static final long IDLE_TIMEOUT = 60 * 1000;
  /** If auth fails repeatedly, we'll assume something is wrong and log a warning / back off. */
  private static final long INVALID_AUTH_TOKEN_THRESHOLD = 3;

  private static final String SERVER_KILL_INTERRUPT_REASON = "server_kill";
  private static final String IDLE_INTERRUPT_REASON = "connection_idle";
  private static final String TOKEN_REFRESH_INTERRUPT_REASON = "token_refresh";

  private static final Logger logger = LoggerFactory.getLogger(PersistentConnection.class);

  private static long connectionIds = 0;

  private final Delegate delegate;
  private final HostInfo hostInfo;
  private final ConnectionContext context;
  private final ConnectionFactory connFactory;
  private final ConnectionAuthTokenProvider authTokenProvider;
  private final ScheduledExecutorService executorService;
  private final RetryHelper retryHelper;
  private final String label;

  private String cachedHost;
  private HashSet<String> interruptReasons = new HashSet<>();
  private boolean firstConnection = true;
  private long lastConnectionEstablishedTime;
  private Connection realtime;
  private ConnectionState connectionState = ConnectionState.Disconnected;
  private long writeCounter = 0;
  private long requestCounter = 0;
  private Map<Long, ConnectionRequestCallback> requestCBHash;
  private List<OutstandingDisconnect> onDisconnectRequestQueue;
  private Map<Long, OutstandingPut> outstandingPuts;
  private Map<ListenQuerySpec, OutstandingListen> listens;
  private String authToken;
  private boolean forceAuthTokenRefresh;
  private String lastSessionId;
  /** Counter to check whether the callback is for the last getToken call. */
  private long currentGetTokenAttempt = 0;

  private int invalidAuthTokenCount = 0;
  private ScheduledFuture<?> inactivityTimer = null;
  private long lastWriteTimestamp;
  private boolean hasOnDisconnects;

  public PersistentConnectionImpl(ConnectionContext context, HostInfo info, Delegate delegate) {
    this(context, info, delegate, new DefaultConnectionFactory());
  }

  PersistentConnectionImpl(
      ConnectionContext context, HostInfo info, Delegate delegate, ConnectionFactory connFactory) {

    this.context = context;
    this.hostInfo = info;
    this.delegate = delegate;
    this.connFactory = connFactory;
    this.executorService = context.getExecutorService();
    this.authTokenProvider = context.getAuthTokenProvider();
    this.listens = new HashMap<>();
    this.requestCBHash = new HashMap<>();
    this.outstandingPuts = new HashMap<>();
    this.onDisconnectRequestQueue = new ArrayList<>();
    this.retryHelper =
        new RetryHelper.Builder(this.executorService, RetryHelper.class)
            .withMinDelayAfterFailure(1000)
            .withRetryExponent(1.3)
            .withMaxDelay(30 * 1000)
            .withJitterFactor(0.7)
            .build();

    long connId = connectionIds++;
    this.label = "[pc_" + connId + "]";
    this.lastSessionId = null;
    doIdleCheck();
  }

  // Connection.Delegate methods
  @Override
  public void onReady(long timestamp, String sessionId) {
    logger.debug("{} onReady", label);
    lastConnectionEstablishedTime = System.currentTimeMillis();
    handleTimestamp(timestamp);

    if (this.firstConnection) {
      sendConnectStats();
    }

    restoreAuth();
    this.firstConnection = false;
    this.lastSessionId = sessionId;
    delegate.onConnect();
  }

  @Override
  public void onCacheHost(String host) {
    this.cachedHost = host;
  }

  @Override
  public void listen(
      List<String> path,
      Map<String, Object> queryParams,
      ListenHashProvider currentHashFn,
      Long tag,
      RequestResultCallback listener) {
    ListenQuerySpec query = new ListenQuerySpec(path, queryParams);
    logger.debug("{} Listening on {}", label, query);
    // TODO: Fix this somehow?
    //hardAssert(query.isDefault() || !query.loadsAllData(), "listen() called for non-default but "
    //          + "complete query");
    hardAssert(!listens.containsKey(query), "listen() called twice for same QuerySpec.");
    logger.debug("{} Adding listen query: {}", label, query);
    OutstandingListen outstandingListen =
        new OutstandingListen(listener, query, tag, currentHashFn);
    listens.put(query, outstandingListen);
    if (connected()) {
      sendListen(outstandingListen);
    }
    doIdleCheck();
  }

  @Override
  public void initialize() {
    this.tryScheduleReconnect();
  }

  @Override
  public void shutdown() {
    this.interrupt("shutdown");
  }

  @Override
  public void put(List<String> path, Object data, RequestResultCallback onComplete) {
    putInternal(REQUEST_ACTION_PUT, path, data, /*hash=*/ null, onComplete);
  }

  @Override
  public void compareAndPut(
      List<String> path, Object data, String hash, RequestResultCallback onComplete) {
    putInternal(REQUEST_ACTION_PUT, path, data, hash, onComplete);
  }

  @Override
  public void merge(List<String> path, Map<String, Object> data, RequestResultCallback onComplete) {
    putInternal(REQUEST_ACTION_MERGE, path, data, /*hash=*/ null, onComplete);
  }

  @Override
  public void purgeOutstandingWrites() {
    for (OutstandingPut put : this.outstandingPuts.values()) {
      if (put.onComplete != null) {
        put.onComplete.onRequestResult("write_canceled", null);
      }
    }
    for (OutstandingDisconnect onDisconnect : this.onDisconnectRequestQueue) {
      if (onDisconnect.onComplete != null) {
        onDisconnect.onComplete.onRequestResult("write_canceled", null);
      }
    }
    this.outstandingPuts.clear();
    this.onDisconnectRequestQueue.clear();
    // Only if we are not connected can we reliably determine that we don't have onDisconnects
    // (outstanding) anymore. Otherwise we leave the flag untouched.
    if (!connected()) {
      this.hasOnDisconnects = false;
    }
    doIdleCheck();
  }

  @Override
  public void onDataMessage(Map<String, Object> message) {
    if (message.containsKey(REQUEST_NUMBER)) {
      // this is a response to a request we sent
      // TODO: this is a hack. Make the json parser give us a Long
      long rn = (Integer) message.get(REQUEST_NUMBER);
      ConnectionRequestCallback responseListener = requestCBHash.remove(rn);
      if (responseListener != null) {
        // jackson gives up Map<String, Object> for json objects
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) message.get(RESPONSE_FOR_REQUEST);
        responseListener.onResponse(response);
      }
    } else if (message.containsKey(REQUEST_ERROR)) {
      // TODO: log the error? probably shouldn't throw here...
    } else if (message.containsKey(SERVER_ASYNC_ACTION)) {
      String action = (String) message.get(SERVER_ASYNC_ACTION);
      // jackson gives up Map<String, Object> for json objects
      @SuppressWarnings("unchecked")
      Map<String, Object> body = (Map<String, Object>) message.get(SERVER_ASYNC_PAYLOAD);
      onDataPush(action, body);
    } else {
      logger.debug("{} Ignoring unknown message: {}", label, message);
    }
  }

  @Override
  public void onDisconnect(Connection.DisconnectReason reason) {
    logger.debug("{} Got on disconnect due to {}", label, reason.name());
    this.connectionState = ConnectionState.Disconnected;
    this.realtime = null;
    this.hasOnDisconnects = false;
    requestCBHash.clear();
    if (inactivityTimer != null) {
      logger.debug("{} Cancelling idle time checker", label);
      inactivityTimer.cancel(false);
      inactivityTimer = null;
    }
    cancelSentTransactions();
    if (shouldReconnect()) {
      long timeSinceLastConnectSucceeded =
          System.currentTimeMillis() - lastConnectionEstablishedTime;
      boolean lastConnectionWasSuccessful;
      if (lastConnectionEstablishedTime > 0) {
        lastConnectionWasSuccessful =
            timeSinceLastConnectSucceeded > SUCCESSFUL_CONNECTION_ESTABLISHED_DELAY;
      } else {
        lastConnectionWasSuccessful = false;
      }
      if (reason == Connection.DisconnectReason.SERVER_RESET || lastConnectionWasSuccessful) {
        retryHelper.signalSuccess();
      }
      tryScheduleReconnect();
    }
    lastConnectionEstablishedTime = 0;
    delegate.onDisconnect();
  }

  @Override
  public void onKill(String reason) {
    logger.debug(
        "{} Firebase Database connection was forcefully killed by the server. Will not attempt "
            + "reconnect. Reason: {}", label, reason);
    interrupt(SERVER_KILL_INTERRUPT_REASON);
  }

  @Override
  public void unlisten(List<String> path, Map<String, Object> queryParams) {
    ListenQuerySpec query = new ListenQuerySpec(path, queryParams);
    logger.debug("{} Unlistening on {}", label, query);

    // TODO: fix this by understanding query params?
    //Utilities.hardAssert(query.isDefault() || !query.loadsAllData(),
    //    "unlisten() called for non-default but complete query");
    OutstandingListen listen = removeListen(query);
    if (listen != null && connected()) {
      sendUnlisten(listen);
    }
    doIdleCheck();
  }

  private boolean connected() {
    return connectionState == ConnectionState.Authenticating
        || connectionState == ConnectionState.Connected;
  }

  @Override
  public void onDisconnectPut(List<String> path, Object data, RequestResultCallback onComplete) {
    this.hasOnDisconnects = true;
    if (canSendWrites()) {
      sendOnDisconnect(REQUEST_ACTION_ONDISCONNECT_PUT, path, data, onComplete);
    } else {
      onDisconnectRequestQueue.add(
          new OutstandingDisconnect(REQUEST_ACTION_ONDISCONNECT_PUT, path, data, onComplete));
    }
    doIdleCheck();
  }

  private boolean canSendWrites() {
    return connectionState == ConnectionState.Connected;
  }

  @Override
  public void onDisconnectMerge(
      List<String> path, Map<String, Object> updates, final RequestResultCallback onComplete) {
    this.hasOnDisconnects = true;
    if (canSendWrites()) {
      sendOnDisconnect(REQUEST_ACTION_ONDISCONNECT_MERGE, path, updates, onComplete);
    } else {
      onDisconnectRequestQueue.add(
          new OutstandingDisconnect(REQUEST_ACTION_ONDISCONNECT_MERGE, path, updates, onComplete));
    }
    doIdleCheck();
  }

  @Override
  public void onDisconnectCancel(List<String> path, RequestResultCallback onComplete) {
    // We do not mark hasOnDisconnects true here, because we only are removing disconnects.
    // However, we can also not reliably determine whether we had onDisconnects, so we can't
    // and do not reset the flag.
    if (canSendWrites()) {
      sendOnDisconnect(REQUEST_ACTION_ONDISCONNECT_CANCEL, path, null, onComplete);
    } else {
      onDisconnectRequestQueue.add(
          new OutstandingDisconnect(REQUEST_ACTION_ONDISCONNECT_CANCEL, path, null, onComplete));
    }
    doIdleCheck();
  }

  @Override
  public void interrupt(String reason) {
    logger.debug("{} Connection interrupted for: {}", label, reason);
    interruptReasons.add(reason);

    if (realtime != null) {
      // Will call onDisconnect and set the connection state to Disconnected
      realtime.close();
      realtime = null;
    } else {
      retryHelper.cancel();
      this.connectionState = ConnectionState.Disconnected;
    }
    // Reset timeouts
    retryHelper.signalSuccess();
  }

  @Override
  public void resume(String reason) {
    logger.debug("{} Connection no longer interrupted for: {}", label, reason);
    interruptReasons.remove(reason);

    if (shouldReconnect() && connectionState == ConnectionState.Disconnected) {
      tryScheduleReconnect();
    }
  }

  @Override
  public boolean isInterrupted(String reason) {
    return interruptReasons.contains(reason);
  }

  private boolean shouldReconnect() {
    return interruptReasons.size() == 0;
  }

  @Override
  public void refreshAuthToken() {
    // Old versions of the database client library didn't have synchronous access to the
    // new token and call this instead of the overload that includes the new token.

    // After a refresh token any subsequent operations are expected to have the authentication
    // status at the point of this call. To avoid race conditions with delays after getToken,
    // we close the connection to make sure any writes/listens are queued until the connection
    // is reauthed with the current token after reconnecting. Note that this will trigger
    // onDisconnects which isn't ideal.
    logger.debug("{} Auth token refresh requested", label);

    // By using interrupt instead of closing the connection we make sure there are no race
    // conditions with other fetch token attempts (interrupt/resume is expected to handle those
    // correctly)
    interrupt(TOKEN_REFRESH_INTERRUPT_REASON);
    resume(TOKEN_REFRESH_INTERRUPT_REASON);
  }

  @Override
  public void refreshAuthToken(String token) {
    logger.debug("{} Auth token refreshed.", label);
    this.authToken = token;
    if (connected()) {
      if (token != null) {
        upgradeAuth();
      } else {
        sendUnauth();
      }
    }
  }

  private void tryScheduleReconnect() {
    if (shouldReconnect()) {
      hardAssert(
          this.connectionState == ConnectionState.Disconnected,
          "Not in disconnected state: %s",
          this.connectionState);
      final boolean forceRefresh = this.forceAuthTokenRefresh;
      logger.debug("{} Scheduling connection attempt", label);
      this.forceAuthTokenRefresh = false;
      retryHelper.retry(
          new Runnable() {
            @Override
            public void run() {
              logger.debug("{} Trying to fetch auth token", label);
              hardAssert(
                  connectionState == ConnectionState.Disconnected,
                  "Not in disconnected state: %s",
                  connectionState);
              connectionState = ConnectionState.GettingToken;
              currentGetTokenAttempt++;
              final long thisGetTokenAttempt = currentGetTokenAttempt;
              authTokenProvider.getToken(
                  forceRefresh,
                  new ConnectionAuthTokenProvider.GetTokenCallback() {
                    @Override
                    public void onSuccess(String token) {
                      if (thisGetTokenAttempt == currentGetTokenAttempt) {
                        // Someone could have interrupted us while fetching the token,
                        // marking the connection as Disconnected
                        if (connectionState == ConnectionState.GettingToken) {
                          logger.debug("{} Successfully fetched token, opening connection", label);
                          openNetworkConnection(token);
                        } else {
                          hardAssert(
                              connectionState == ConnectionState.Disconnected,
                              "Expected connection state disconnected, but was %s",
                              connectionState);
                          logger.debug(
                              "{} Not opening connection after token refresh, because connection "
                                  + "was set to disconnected", label);
                        }
                      } else {
                        logger.debug(
                            "{} Ignoring getToken result, because this was not the "
                                + "latest attempt.", label);
                      }
                    }

                    @Override
                    public void onError(String error) {
                      if (thisGetTokenAttempt == currentGetTokenAttempt) {
                        connectionState = ConnectionState.Disconnected;
                        logger.debug("{} Error fetching token: {}", label, error);
                        tryScheduleReconnect();
                      } else {
                        logger.debug(
                            "{} Ignoring getToken error, because this was not the "
                                + "latest attempt.", label);
                      }
                    }
                  });
            }
          });
    }
  }

  private void openNetworkConnection(String token) {
    hardAssert(
        connectionState == ConnectionState.GettingToken,
        "Trying to open network connection while in the wrong state: %s",
        connectionState);
    // User might have logged out. Positive auth status is handled after authenticating with
    // the server
    if (token == null) {
      delegate.onAuthStatus(false);
    }
    authToken = token;
    connectionState = ConnectionState.Connecting;
    realtime = connFactory.newConnection(this);
    realtime.open();
  }

  private void sendOnDisconnect(
      String action, List<String> path, Object data, final RequestResultCallback onComplete) {
    Map<String, Object> request = new HashMap<>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(path));
    request.put(REQUEST_DATA_PAYLOAD, data);
    //
    //if (logger.logsDebug()) logger.debug("onDisconnect " + action + " " + request);
    sendAction(
        action,
        request,
        new ConnectionRequestCallback() {
          @Override
          public void onResponse(Map<String, Object> response) {
            String status = (String) response.get(REQUEST_STATUS);
            String errorMessage = null;
            String errorCode = null;
            if (!status.equals("ok")) {
              errorCode = status;
              errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
            }
            if (onComplete != null) {
              onComplete.onRequestResult(errorCode, errorMessage);
            }
          }
        });
  }

  private void cancelSentTransactions() {
    List<OutstandingPut> cancelledTransactionWrites = new ArrayList<>();

    Iterator<Map.Entry<Long, OutstandingPut>> iter = outstandingPuts.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<Long, OutstandingPut> entry = iter.next();
      OutstandingPut put = entry.getValue();
      if (put.getRequest().containsKey(REQUEST_DATA_HASH) && put.wasSent()) {
        cancelledTransactionWrites.add(put);
        iter.remove();
      }
    }

    for (OutstandingPut put : cancelledTransactionWrites) {
      // onRequestResult() may invoke rerunTransactions() and enqueue new writes. We defer calling
      // it until we've finished enumerating all existing writes.
      put.getOnComplete().onRequestResult("disconnected", null);
    }
  }

  private void sendUnlisten(OutstandingListen listen) {
    Map<String, Object> request = new HashMap<>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(listen.query.path));

    Long tag = listen.getTag();
    if (tag != null) {
      request.put(REQUEST_QUERIES, listen.getQuery().queryParams);
      request.put(REQUEST_TAG, tag);
    }

    sendAction(REQUEST_ACTION_QUERY_UNLISTEN, request, null);
  }

  private OutstandingListen removeListen(ListenQuerySpec query) {
    logger.debug("{} removing query {}", label, query);
    if (!listens.containsKey(query)) {
      logger.debug(
          "{} Trying to remove listener for QuerySpec {} but no listener exists.", label, query);
      return null;
    } else {
      OutstandingListen oldListen = listens.get(query);
      listens.remove(query);
      doIdleCheck();
      return oldListen;
    }
  }

  private Collection<OutstandingListen> removeListens(List<String> path) {
    logger.debug("{} Removing all listens at path {}", label, path);
    List<OutstandingListen> removedListens = new ArrayList<>();
    for (Map.Entry<ListenQuerySpec, OutstandingListen> entry : listens.entrySet()) {
      ListenQuerySpec query = entry.getKey();
      OutstandingListen listen = entry.getValue();
      if (query.path.equals(path)) {
        removedListens.add(listen);
      }
    }

    for (OutstandingListen toRemove : removedListens) {
      listens.remove(toRemove.getQuery());
    }

    doIdleCheck();

    return removedListens;
  }

  private void onDataPush(String action, Map<String, Object> body) {
    logger.debug("{} handleServerMessage: {} {}", label, action, body);
    if (action.equals(SERVER_ASYNC_DATA_UPDATE) || action.equals(SERVER_ASYNC_DATA_MERGE)) {
      boolean isMerge = action.equals(SERVER_ASYNC_DATA_MERGE);

      String pathString = (String) body.get(SERVER_DATA_UPDATE_PATH);
      Object payloadData = body.get(SERVER_DATA_UPDATE_BODY);
      Long tagNumber = ConnectionUtils.longFromObject(body.get(SERVER_DATA_TAG));
      // ignore empty merges
      if (isMerge && (payloadData instanceof Map) && ((Map) payloadData).size() == 0) {
        logger.debug("{} Ignoring empty merge for path {}", label, pathString);
      } else {
        List<String> path = ConnectionUtils.stringToPath(pathString);
        delegate.onDataUpdate(path, payloadData, isMerge, tagNumber);
      }
    } else if (action.equals(SERVER_ASYNC_DATA_RANGE_MERGE)) {
      String pathString = (String) body.get(SERVER_DATA_UPDATE_PATH);
      List<String> path = ConnectionUtils.stringToPath(pathString);
      Object payloadData = body.get(SERVER_DATA_UPDATE_BODY);
      Long tag = ConnectionUtils.longFromObject(body.get(SERVER_DATA_TAG));
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> ranges = (List<Map<String, Object>>) payloadData;
      List<RangeMerge> rangeMerges = new ArrayList<>();
      for (Map<String, Object> range : ranges) {
        String startString = (String) range.get(SERVER_DATA_START_PATH);
        String endString = (String) range.get(SERVER_DATA_END_PATH);
        List<String> start = startString != null ? ConnectionUtils.stringToPath(startString) : null;
        List<String> end = endString != null ? ConnectionUtils.stringToPath(endString) : null;
        Object update = range.get(SERVER_DATA_RANGE_MERGE);
        rangeMerges.add(new RangeMerge(start, end, update));
      }
      if (rangeMerges.isEmpty()) {
        logger.debug("{} Ignoring empty range merge for path {}", label, pathString);
      } else {
        this.delegate.onRangeMergeUpdate(path, rangeMerges, tag);
      }
    } else if (action.equals(SERVER_ASYNC_LISTEN_CANCELLED)) {
      String pathString = (String) body.get(SERVER_DATA_UPDATE_PATH);
      List<String> path = ConnectionUtils.stringToPath(pathString);
      onListenRevoked(path);
    } else if (action.equals(SERVER_ASYNC_AUTH_REVOKED)) {
      String status = (String) body.get(REQUEST_STATUS);
      String reason = (String) body.get(SERVER_DATA_UPDATE_BODY);
      onAuthRevoked(status, reason);
    } else if (action.equals(SERVER_ASYNC_SECURITY_DEBUG)) {
      onSecurityDebugPacket(body);
    } else {
      logger.debug("{} Unrecognized action from server: {}", label, action);
    }
  }

  private void onListenRevoked(List<String> path) {
    // Remove the listen and manufacture a "permission denied" error for the failed listen

    Collection<OutstandingListen> listens = removeListens(path);
    // The listen may have already been removed locally. If so, skip it
    if (listens != null) {
      for (OutstandingListen listen : listens) {
        listen.resultCallback.onRequestResult("permission_denied", null);
      }
    }
  }

  private void onAuthRevoked(String errorCode, String errorMessage) {
    // This might be for an earlier token than we just recently sent. But since we need to close
    // the connection anyways, we can set it to null here and we will refresh the token later
    // on reconnect.
    logger.debug("{} Auth token revoked: {} ({})", label, errorCode, errorMessage);
    this.authToken = null;
    this.forceAuthTokenRefresh = true;
    this.delegate.onAuthStatus(false);
    // Close connection and reconnect
    this.realtime.close();
  }

  private void onSecurityDebugPacket(Map<String, Object> message) {
    // TODO: implement on iOS too
    logger.info("{} {}", label, message.get("msg"));
  }

  private void upgradeAuth() {
    sendAuthHelper(/*restoreStateAfterComplete=*/ false);
  }

  private void sendAuthAndRestoreState() {
    sendAuthHelper(/*restoreStateAfterComplete=*/ true);
  }

  private void sendAuthHelper(final boolean restoreStateAfterComplete) {
    hardAssert(connected(), "Must be connected to send auth, but was: %s", this.connectionState);
    hardAssert(this.authToken != null, "Auth token must be set to authenticate!");

    ConnectionRequestCallback onComplete =
        new ConnectionRequestCallback() {
          @Override
          public void onResponse(Map<String, Object> response) {
            connectionState = ConnectionState.Connected;

            String status = (String) response.get(REQUEST_STATUS);
            if (status.equals("ok")) {
              invalidAuthTokenCount = 0;
              delegate.onAuthStatus(true);
              if (restoreStateAfterComplete) {
                restoreState();
              }
            } else {
              authToken = null;
              forceAuthTokenRefresh = true;
              delegate.onAuthStatus(false);
              String reason = (String) response.get(SERVER_RESPONSE_DATA);
              logger.debug("{} Authentication failed: {} ({})", label, status, reason);
              realtime.close();

              if (status.equals("invalid_token") || status.equals("permission_denied")) {
                // We'll wait a couple times before logging the warning / increasing the
                // retry period since oauth tokens will report as "invalid" if they're
                // just expired. Plus there may be transient issues that resolve themselves.
                invalidAuthTokenCount++;
                if (invalidAuthTokenCount >= INVALID_AUTH_TOKEN_THRESHOLD) {
                  // Set a long reconnect delay because recovery is unlikely.
                  retryHelper.setMaxDelay();
                  logger.warn(
                      "{} Provided authentication credentials are invalid. This "
                          + "usually indicates your FirebaseApp instance was not initialized "
                          + "correctly. Make sure your database URL is correct and that your "
                          + "service account is for the correct project and is authorized to "
                          + "access it.", label);
                }
              }
            }
          }
        };

    Map<String, Object> request = new HashMap<>();
    GAuthToken googleAuthToken = GAuthToken.tryParseFromString(this.authToken);
    if (googleAuthToken != null) {
      request.put(REQUEST_CREDENTIAL, googleAuthToken.getToken());
      if (googleAuthToken.getAuth() != null) {
        if (!googleAuthToken.getAuth().isEmpty()) {
          request.put(REQUEST_AUTHVAR, googleAuthToken.getAuth());
        }
      } else {
        request.put(REQUEST_NOAUTH, true);
      }
      sendSensitive(REQUEST_ACTION_GAUTH, /*isSensitive=*/ true, request, onComplete);
    } else {
      request.put(REQUEST_CREDENTIAL, authToken);
      sendSensitive(REQUEST_ACTION_AUTH, /*isSensitive=*/ true, request, onComplete);
    }
  }

  private void sendUnauth() {
    hardAssert(connected(), "Must be connected to send unauth.");
    hardAssert(authToken == null, "Auth token must not be set.");
    sendAction(REQUEST_ACTION_UNAUTH, Collections.<String, Object>emptyMap(), null);
  }

  private void restoreAuth() {
    logger.debug("{} Calling restore state", label);

    hardAssert(
        this.connectionState == ConnectionState.Connecting,
        "Wanted to restore auth, but was in wrong state: %s",
        this.connectionState);

    if (authToken == null) {
      logger.debug("{} Not restoring auth because token is null.", label);
      this.connectionState = ConnectionState.Connected;
      restoreState();
    } else {
      logger.debug("{} Restoring auth.", label);
      this.connectionState = ConnectionState.Authenticating;
      sendAuthAndRestoreState();
    }
  }

  private void restoreState() {
    hardAssert(
        this.connectionState == ConnectionState.Connected,
        "Should be connected if we're restoring state, but we are: %s",
        this.connectionState);

    // Restore listens
    logger.debug("{} Restoring outstanding listens", label);
    for (OutstandingListen listen : listens.values()) {
      logger.debug("{} Restoring listen {}", label, listen.getQuery());
      sendListen(listen);
    }

    logger.debug("{} Restoring writes.", label);
    // Restore puts
    ArrayList<Long> outstanding = new ArrayList<>(outstandingPuts.keySet());
    // Make sure puts are restored in order
    Collections.sort(outstanding);
    for (Long put : outstanding) {
      sendPut(put);
    }

    // Restore disconnect operations
    for (OutstandingDisconnect disconnect : onDisconnectRequestQueue) {
      sendOnDisconnect(
          disconnect.getAction(),
          disconnect.getPath(),
          disconnect.getData(),
          disconnect.getOnComplete());
    }
    onDisconnectRequestQueue.clear();
  }

  private void handleTimestamp(long timestamp) {
    logger.debug("{} Handling timestamp", label);
    long timestampDelta = timestamp - System.currentTimeMillis();
    Map<String, Object> updates = new HashMap<>();
    updates.put(Constants.DOT_INFO_SERVERTIME_OFFSET, timestampDelta);
    delegate.onServerInfoUpdate(updates);
  }

  private Map<String, Object> getPutObject(List<String> path, Object data, String hash) {
    Map<String, Object> request = new HashMap<>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(path));
    request.put(REQUEST_DATA_PAYLOAD, data);
    if (hash != null) {
      request.put(REQUEST_DATA_HASH, hash);
    }
    return request;
  }

  private void putInternal(
      String action,
      List<String> path,
      Object data,
      String hash,
      RequestResultCallback onComplete) {
    Map<String, Object> request = getPutObject(path, data, hash);

    // local to PersistentConnection
    long writeId = this.writeCounter++;

    outstandingPuts.put(writeId, new OutstandingPut(action, request, onComplete));
    if (canSendWrites()) {
      sendPut(writeId);
    }
    this.lastWriteTimestamp = System.currentTimeMillis();
    doIdleCheck();
  }

  private void sendPut(final long putId) {
    assert canSendWrites()
        : "sendPut called when we can't send writes (we're disconnected or writes are paused).";
    final OutstandingPut put = outstandingPuts.get(putId);
    final RequestResultCallback onComplete = put.getOnComplete();
    final String action = put.getAction();

    put.markSent();
    sendAction(
        action,
        put.getRequest(),
        new ConnectionRequestCallback() {
          @Override
          public void onResponse(Map<String, Object> response) {
            logger.debug("{} {} response: {}", label, action, response);

            OutstandingPut currentPut = outstandingPuts.get(putId);
            if (currentPut == put) {
              outstandingPuts.remove(putId);

              if (onComplete != null) {
                String status = (String) response.get(REQUEST_STATUS);
                if (status.equals("ok")) {
                  onComplete.onRequestResult(null, null);
                } else {
                  String errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
                  onComplete.onRequestResult(status, errorMessage);
                }
              }
            } else {
              logger.debug("{} Ignoring on complete for put {} because it was removed already.",
                  label, putId);
            }
            doIdleCheck();
          }
        });
  }

  private void sendListen(final OutstandingListen listen) {
    Map<String, Object> request = new HashMap<>();
    request.put(REQUEST_PATH, ConnectionUtils.pathToString(listen.getQuery().path));
    Long tag = listen.getTag();
    // Only bother to send query if it's non-default
    if (tag != null) {
      request.put(REQUEST_QUERIES, listen.query.queryParams);
      request.put(REQUEST_TAG, tag);
    }

    ListenHashProvider hashFunction = listen.getHashFunction();
    request.put(REQUEST_DATA_HASH, hashFunction.getSimpleHash());

    if (hashFunction.shouldIncludeCompoundHash()) {
      CompoundHash compoundHash = hashFunction.getCompoundHash();

      List<String> posts = new ArrayList<>();
      for (List<String> path : compoundHash.getPosts()) {
        posts.add(ConnectionUtils.pathToString(path));
      }
      Map<String, Object> hash = new HashMap<>();
      hash.put(REQUEST_COMPOUND_HASH_HASHES, compoundHash.getHashes());
      hash.put(REQUEST_COMPOUND_HASH_PATHS, posts);
      request.put(REQUEST_COMPOUND_HASH, hash);
    }

    sendAction(
        REQUEST_ACTION_QUERY,
        request,
        new ConnectionRequestCallback() {

          @Override
          public void onResponse(Map<String, Object> response) {
            String status = (String) response.get(REQUEST_STATUS);
            // log warnings in any case, even if listener was already removed
            if (status.equals("ok")) {
              @SuppressWarnings("unchecked")
              Map<String, Object> serverBody =
                  (Map<String, Object>) response.get(SERVER_DATA_UPDATE_BODY);
              if (serverBody.containsKey(SERVER_DATA_WARNINGS)) {
                @SuppressWarnings("unchecked")
                List<String> warnings = (List<String>) serverBody.get(SERVER_DATA_WARNINGS);
                warnOnListenerWarnings(warnings, listen.query);
              }
            }

            OutstandingListen currentListen = listens.get(listen.getQuery());
            // only trigger actions if the listen hasn't been removed (and maybe readded)
            if (currentListen == listen) {
              if (!status.equals("ok")) {
                removeListen(listen.getQuery());
                String errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
                listen.resultCallback.onRequestResult(status, errorMessage);
              } else {
                listen.resultCallback.onRequestResult(null, null);
              }
            }
          }
        });
  }

  private void sendStats(final Map<String, Integer> stats) {
    if (!stats.isEmpty()) {
      Map<String, Object> request = new HashMap<>();
      request.put(REQUEST_COUNTERS, stats);
      sendAction(
          REQUEST_ACTION_STATS,
          request,
          new ConnectionRequestCallback() {
            @Override
            public void onResponse(Map<String, Object> response) {
              String status = (String) response.get(REQUEST_STATUS);
              if (!status.equals("ok")) {
                String errorMessage = (String) response.get(SERVER_DATA_UPDATE_BODY);
                logger.debug(
                    "{} Failed to send stats: {} (message: {})", label, stats, errorMessage);
              }
            }
          });
    } else {
      logger.debug("{} Not sending stats because stats are empty", label);
    }
  }

  @SuppressWarnings("unchecked")
  private void warnOnListenerWarnings(List<String> warnings, ListenQuerySpec query) {
    if (warnings.contains("no_index")) {
      String indexSpec = "\".indexOn\": \"" + query.queryParams.get("i") + '\"';
      logger.warn(
          "{} Using an unspecified index. Consider adding '{}' at {} to your security and "
              + "Firebase Database rules for better performance",
          label, indexSpec, ConnectionUtils.pathToString(query.path));
    }
  }

  private void sendConnectStats() {
    Map<String, Integer> stats = new HashMap<>();
    assert !this.context.isPersistenceEnabled()
        : "Stats for persistence on JVM missing (persistence not yet supported)";
    stats.put("sdk.admin_java." + context.getClientSdkVersion().replace('.', '-'), 1);
    logger.debug("{} Sending first connection stats", label);
    sendStats(stats);
  }

  private void sendAction(
      String action, Map<String, Object> message, ConnectionRequestCallback onResponse) {
    sendSensitive(action, /*isSensitive=*/ false, message, onResponse);
  }

  private void sendSensitive(
      String action,
      boolean isSensitive,
      Map<String, Object> message,
      ConnectionRequestCallback onResponse) {
    long rn = nextRequestNumber();
    Map<String, Object> request = new HashMap<>();
    request.put(REQUEST_NUMBER, rn);
    request.put(REQUEST_ACTION, action);
    request.put(REQUEST_PAYLOAD, message);
    realtime.sendRequest(request, isSensitive);
    requestCBHash.put(rn, onResponse);
  }

  private long nextRequestNumber() {
    return requestCounter++;
  }

  private void doIdleCheck() {
    if (isIdle()) {
      if (this.inactivityTimer != null) {
        this.inactivityTimer.cancel(false);
      }

      this.inactivityTimer =
          this.executorService.schedule(
              new Runnable() {
                @Override
                public void run() {
                  inactivityTimer = null;
                  if (idleHasTimedOut()) {
                    interrupt(IDLE_INTERRUPT_REASON);
                  } else {
                    doIdleCheck();
                  }
                }
              },
              IDLE_TIMEOUT,
              TimeUnit.MILLISECONDS);
    } else if (isInterrupted(IDLE_INTERRUPT_REASON)) {
      hardAssert(!isIdle());
      this.resume(IDLE_INTERRUPT_REASON);
    }
  }

  /**
   * @return Returns true if the connection is currently not being used (for listen, outstanding
   *     operations).
   */
  private boolean isIdle() {
    return this.listens.isEmpty()
        && this.requestCBHash.isEmpty()
        && !this.hasOnDisconnects
        && this.outstandingPuts.isEmpty();
  }

  private boolean idleHasTimedOut() {
    long now = System.currentTimeMillis();
    return isIdle() && now > (this.lastWriteTimestamp + IDLE_TIMEOUT);
  }

  private enum ConnectionState {
    Disconnected,
    GettingToken,
    Connecting,
    Authenticating,
    Connected
  }

  private interface ConnectionRequestCallback {

    void onResponse(Map<String, Object> response);
  }

  private static class ListenQuerySpec {

    private final List<String> path;
    private final Map<String, Object> queryParams;

    public ListenQuerySpec(List<String> path, Map<String, Object> queryParams) {
      this.path = path;
      this.queryParams = queryParams;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ListenQuerySpec)) {
        return false;
      }

      ListenQuerySpec that = (ListenQuerySpec) o;

      if (!path.equals(that.path)) {
        return false;
      }
      return queryParams.equals(that.queryParams);
    }

    @Override
    public int hashCode() {
      int result = path.hashCode();
      result = 31 * result + queryParams.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return ConnectionUtils.pathToString(this.path) + " (params: " + queryParams + ")";
    }
  }

  private static class OutstandingListen {

    private final RequestResultCallback resultCallback;
    private final ListenQuerySpec query;
    private final ListenHashProvider hashFunction;
    private final Long tag;

    private OutstandingListen(
        RequestResultCallback callback,
        ListenQuerySpec query,
        Long tag,
        ListenHashProvider hashFunction) {
      this.resultCallback = callback;
      this.query = query;
      this.hashFunction = hashFunction;
      this.tag = tag;
    }

    ListenQuerySpec getQuery() {
      return query;
    }

    Long getTag() {
      return this.tag;
    }

    ListenHashProvider getHashFunction() {
      return this.hashFunction;
    }

    @Override
    public String toString() {
      return query.toString() + " (Tag: " + this.tag + ")";
    }
  }

  private static class OutstandingPut {

    private String action;
    private Map<String, Object> request;
    private RequestResultCallback onComplete;
    private boolean sent;

    private OutstandingPut(
        String action, Map<String, Object> request, RequestResultCallback onComplete) {
      this.action = action;
      this.request = request;
      this.onComplete = onComplete;
    }

    String getAction() {
      return action;
    }

    Map<String, Object> getRequest() {
      return request;
    }

    RequestResultCallback getOnComplete() {
      return onComplete;
    }

    void markSent() {
      this.sent = true;
    }

    boolean wasSent() {
      return this.sent;
    }
  }

  private static class OutstandingDisconnect {

    private final String action;
    private final List<String> path;
    private final Object data;
    private final RequestResultCallback onComplete;

    private OutstandingDisconnect(
        String action, List<String> path, Object data, RequestResultCallback onComplete) {
      this.action = action;
      this.path = path;
      this.data = data;
      this.onComplete = onComplete;
    }

    public String getAction() {
      return action;
    }

    public List<String> getPath() {
      return path;
    }

    public Object getData() {
      return data;
    }

    public RequestResultCallback getOnComplete() {
      return onComplete;
    }
  }

  interface ConnectionFactory {
    Connection newConnection(PersistentConnectionImpl delegate);
  }

  private static class DefaultConnectionFactory implements ConnectionFactory {
    @Override
    public Connection newConnection(PersistentConnectionImpl delegate) {
      return new Connection(delegate.context, delegate.hostInfo, delegate.cachedHost,
          delegate, delegate.lastSessionId);
    }
  }


}
