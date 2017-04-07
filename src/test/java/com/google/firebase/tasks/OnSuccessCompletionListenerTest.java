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
