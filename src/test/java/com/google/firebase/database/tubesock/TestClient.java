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

package com.google.firebase.database.tubesock;

import java.net.URI;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** User: greg Date: 7/24/13 Time: 11:41 AM */
public class TestClient {

  private WebSocket client;
  private AtomicBoolean inTest;
  private Semaphore testLatch;

  public void startTest(String testNum) throws WebSocketException, InterruptedException {
    URI uri = URI.create("ws://localhost:9001/runCase?case=" + testNum + "&agent=tubesock");
    inTest = new AtomicBoolean(true);
    testLatch = new Semaphore(0);
    client = new WebSocket(uri);
    client.setEventHandler(new Handler());
    client.connect();
    testLatch.acquire(1);
    // Not required, but make sure the threads exit after the socket is closed
    client.blockClose();
  }

  private void finishTest() {
    if (inTest.compareAndSet(true, false)) {
      testLatch.release(1);
    } else {
      // Sanity check to make sure we don't double-close
      System.err.println("Tried to end a test that was already over");
    }
  }

  private class Handler implements WebSocketEventHandler {

    @Override
    public void onOpen() {}

    @Override
    public void onMessage(WebSocketMessage message) {
      // For autobahn tests, simply echo back any valid messages we get
      try {
        if (message.isText()) {
          client.send(message.getText());
        } else {
          client.send(message.getBytes());
        }
      } catch (WebSocketException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void onClose() {
      finishTest();
    }

    @Override
    public void onError(WebSocketException e) {
      // The autobahn tests will generate a number of errors from the client. Uncomment this if you
      // want to see them.
      //e.printStackTrace();
    }

    @Override
    public void onLogMessage(String msg) {
      System.err.println(msg);
    }
  }
}
