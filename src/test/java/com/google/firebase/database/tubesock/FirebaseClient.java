package com.google.firebase.database.tubesock;

import java.net.URI;
import java.util.concurrent.Semaphore;

/**
 * User: greg Date: 7/25/13 Time: 6:48 PM
 *
 * <p>This test is just a quick smoke test to make sure we can connect to a wss endpoint.
 */
public class FirebaseClient {

  private WebSocket client;
  private Semaphore semaphore;

  public static void main(String[] args) {
    try {
      new FirebaseClient().start();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public void start() throws WebSocketException, InterruptedException {
    semaphore = new Semaphore(0);
    URI uri = URI.create("wss://gsoltis.firebaseio-demo.com/.ws?v=5");
    client = new WebSocket(uri);
    client.setEventHandler(new Handler());
    client.connect();
    semaphore.acquire(1);
    client.blockClose();
  }

  private class Handler implements WebSocketEventHandler {

    @Override
    public void onOpen() {
      System.out.println("Opened socket");
      client.close();
    }

    @Override
    public void onMessage(WebSocketMessage message) {
    }

    @Override
    public void onClose() {
      System.out.println("Closed socket");
      semaphore.release(1);
    }

    @Override
    public void onError(WebSocketException e) {
      e.printStackTrace();
    }

    @Override
    public void onLogMessage(String msg) {
      System.err.println(msg);
    }
  }
}
