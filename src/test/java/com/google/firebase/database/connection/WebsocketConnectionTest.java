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

import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.TestHelpers;
import com.google.firebase.database.connection.WebsocketConnection.Delegate;
import com.google.firebase.database.connection.WebsocketConnection.WSClient;
import com.google.firebase.database.connection.WebsocketConnection.WSClientEventHandler;
import com.google.firebase.database.connection.WebsocketConnection.WSClientFactory;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class WebsocketConnectionTest {

  private static final ScheduledExecutorService executor =
      Mockito.spy(ScheduledExecutorService.class);

  @BeforeClass
  public static void setUpClass() {
    // Rig the execute() method to execute the Runnable on the calling thread.
    Mockito.doAnswer(new Answer() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        Runnable runnable = invocation.getArgument(0);
        runnable.run();
        return null;
      }
    }).when(executor).execute(Mockito.any(Runnable.class));
  }

  @AfterClass
  public static void tearDownClass() {
    executor.shutdownNow();
  }

  @Test
  public void testOpen() {
    MockClientFactory clientFactory = new MockClientFactory();
    clientFactory.conn.open();
    Mockito.verify(clientFactory.client, Mockito.times(1)).connect();
  }

  @Test
  public void testClose() {
    MockClientFactory clientFactory = new MockClientFactory();
    clientFactory.conn.close();
    Mockito.verify(clientFactory.client, Mockito.times(1)).close();
  }

  @Test
  public void testSendSingleFrame() {
    MockClientFactory clientFactory = new MockClientFactory();
    clientFactory.conn.send(ImmutableMap.<String, Object>of("key", "value"));
    Mockito.verify(clientFactory.client, Mockito.times(1)).send("{\"key\":\"value\"}");
  }

  @Test
  public void testSendMultipleFrames() {
    MockClientFactory clientFactory = new MockClientFactory();
    StringBuilder longString = new StringBuilder();
    for (int i = 0; i < 20000; i++) {
      longString.append("a");
    }
    clientFactory.conn.send(ImmutableMap.<String, Object>of("key", longString.toString()));

    String serialized = String.format("{\"key\":\"%s\"}", longString.toString());
    Mockito.verify(clientFactory.client, Mockito.times(1)).send("2");
    Mockito.verify(clientFactory.client, Mockito.times(1)).send(serialized.substring(0, 16384));
    Mockito.verify(clientFactory.client, Mockito.times(1)).send(serialized.substring(16384));
  }

  @Test
  public void testReceiveSingleFrame() {
    MockClientFactory clientFactory = new MockClientFactory();
    clientFactory.eventHandler.onMessage("{\"key\":\"value\"}");
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onMessage(
        ImmutableMap.<String, Object>of("key", "value"));
  }

  @Test
  public void testReceiveMultipleFrames() {
    MockClientFactory clientFactory = new MockClientFactory();
    clientFactory.eventHandler.onMessage("2");
    clientFactory.eventHandler.onMessage("{\"key1\":\"value1\",");
    clientFactory.eventHandler.onMessage("\"key2\":\"value2\"}");
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onMessage(
        ImmutableMap.<String, Object>of("key1", "value1", "key2", "value2"));
  }

  @Test
  public void testIncomingMessageIOError() {
    MockClientFactory clientFactory = new MockClientFactory();
    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of("key", "value");
    Mockito.doThrow(IOException.class).when(clientFactory.delegate).onMessage(data);
    clientFactory.eventHandler.onMessage("{\"key\":\"value\"}");
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onMessage(data);
    Mockito.verify(clientFactory.client, Mockito.times(1)).close();
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onDisconnect(false);
  }

  @Test
  public void testIncomingMessageClassCastError() {
    MockClientFactory clientFactory = new MockClientFactory();
    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of("key", "value");
    Mockito.doThrow(ClassCastException.class).when(clientFactory.delegate).onMessage(data);
    clientFactory.eventHandler.onMessage("{\"key\":\"value\"}");
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onMessage(data);
    Mockito.verify(clientFactory.client, Mockito.times(1)).close();
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onDisconnect(false);
  }

  @Test
  public void testOnClose() {
    MockClientFactory clientFactory = new MockClientFactory();
    clientFactory.eventHandler.onClose();
    Mockito.verify(clientFactory.client, Mockito.times(1)).close();
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onDisconnect(false);
  }

  @Test
  public void testOnError() {
    MockClientFactory clientFactory = new MockClientFactory();
    clientFactory.eventHandler.onError(new Exception("test"));
    Mockito.verify(clientFactory.client, Mockito.times(1)).close();
    Mockito.verify(clientFactory.delegate, Mockito.times(1)).onDisconnect(false);
  }

  private static class MockClientFactory implements WSClientFactory {

    private final WSClient client = Mockito.mock(WSClient.class);
    private final Delegate delegate = Mockito.mock(Delegate.class);
    private final WebsocketConnection conn;
    private WSClientEventHandler eventHandler;

    MockClientFactory() {
      ConnectionContext context = TestHelpers.newConnectionContext(executor);
      this.conn = new WebsocketConnection(context, delegate, this);
    }

    @Override
    public WSClient newClient(WSClientEventHandler delegate) {
      this.eventHandler = delegate;
      return client;
    }
  }
}
