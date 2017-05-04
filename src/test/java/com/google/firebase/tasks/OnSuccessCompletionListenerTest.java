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

import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.firebase.tasks.testing.TestOnSuccessListener;
import com.google.firebase.testing.MockitoTestRule;
import java.rmi.RemoteException;
import java.util.concurrent.Executor;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public class OnSuccessCompletionListenerTest {

  @Rule public MockitoTestRule mockitoTestRule = new MockitoTestRule();

  @Mock private Executor mockExecutor;

  @Test
  public void testOnComplete_nothingExecutedAfterCancel() {
    OnSuccessCompletionListener<Void> listener =
        new OnSuccessCompletionListener<>(mockExecutor, new TestOnSuccessListener<>());
    listener.cancel();

    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    listener.onComplete(task);

    verifyZeroInteractions(mockExecutor);
  }

  @Test
  public void testOnComplete_nothingExecutedOnFailure() {
    OnSuccessCompletionListener<Void> listener =
        new OnSuccessCompletionListener<>(mockExecutor, new TestOnSuccessListener<>());

    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(new RemoteException());
    listener.onComplete(task);

    verifyZeroInteractions(mockExecutor);
  }
}
