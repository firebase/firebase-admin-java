package com.google.firebase;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class Main {

  public static void main(String[] args) throws Exception {
    ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setNameFormat("hkj-%d")
        .build();
    ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(0, threadFactory);

    threadDump();
    Future<Void> f = exec.schedule(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        System.out.println("Task 1");
        return null;
      }
    }, 5, TimeUnit.SECONDS);
    threadDump();

    f.get();

    Thread.sleep(1000);
    threadDump();

    exec.shutdownNow();
  }

  private static void threadDump() {
    System.out.println("\nThread Dump");
    System.out.println("=============");
    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
    for (Thread t : threadSet) {
      System.out.println(t.getName());
    }
  }

}
