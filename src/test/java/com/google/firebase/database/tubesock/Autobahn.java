package com.google.firebase.database.tubesock;

import java.io.IOException;

/**
 * User: greg Date: 7/24/13 Time: 11:36 AM
 *
 * <p>This test is used to run the autobahn websocket protocol compliance test suite. {@see
 * http://autobahn.ws/testsuite}
 */
public class Autobahn {

  private static final Boolean BLOCK_ON_START = false;

  private static void runTest(int i) {
    System.out.println("Running test #" + i);
    TestClient client = new TestClient();
    try {
      client.startTest("" + i);
    } catch (WebSocketException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void updateResults() {
    UpdateClient updateClient = new UpdateClient();
    try {
      updateClient.update();
    } catch (WebSocketException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void runSuite() {
    runSuite(1, 301);
  }

  private static void runSuite(int from, int to) {
    for (int i = from; i <= to; ++i) {
      runTest(i);
    }
    updateResults();
  }

  public static void main(String[] args) {
    if (BLOCK_ON_START) {
      // Block to allow time to attach a profiler or debugger or whatever
      try {
        System.in.read(new byte[1]);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    runSuite();
  }
}
