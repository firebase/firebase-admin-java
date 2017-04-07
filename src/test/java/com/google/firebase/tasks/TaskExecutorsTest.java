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
