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

package com.google.firebase.tasks;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;

public class TaskExecutorsTest {

  private static final long TIMEOUT_MS = 500;

  @Test
  public void testDefaultThreadPool() throws InterruptedException {
    final ArrayBlockingQueue<Thread> sync = new ArrayBlockingQueue<>(1);
    TaskExecutors.DEFAULT_THREAD_POOL.execute(
        new Runnable() {
          @Override
          public void run() {
            sync.add(Thread.currentThread());
          }
        });
    Thread actual = sync.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    Assert.assertNotEquals(Thread.currentThread(), actual);
  }

  @Test
  public void testDirect() throws InterruptedException {
    final ArrayBlockingQueue<Thread> sync = new ArrayBlockingQueue<>(1);
    TaskExecutors.DIRECT.execute(
        new Runnable() {
          @Override
          public void run() {
            sync.add(Thread.currentThread());
          }
        });
    Thread actual = sync.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    Assert.assertEquals(Thread.currentThread(), actual);
  }
}
