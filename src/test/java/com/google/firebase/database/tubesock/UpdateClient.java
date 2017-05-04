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

/** User: greg Date: 7/24/13 Time: 1:42 PM */
public class UpdateClient {

  private Semaphore completionLatch;
  private AtomicBoolean completed;

  public void update() throws WebSocketException, InterruptedException {
    Semaphore semaphore = new Semaphore(0);
    URI uri = URI.create("ws://localhost:9001/updateReports?agent=tubesock");
    completed = new AtomicBoolean(false);
    completionLatch = semaphore;
    WebSocket client = new WebSocket(uri);
    client.setEventHandler(new Handler());
    client.connect();
    semaphore.acquire(1);
    client.blockClose();
  }

  private void finish() {
    if (completed.compareAndSet(false, true)) {
      completionLatch.release(1);
    } else {
      System.err.println("Tried to end a test that was already over");
    }
  }

  private class Handler implements WebSocketEventHandler {

    @Override
    public void onOpen() {}

    @Override
    public void onMessage(WebSocketMessage message) {}

    @Override
    public void onClose() {
      finish();
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
