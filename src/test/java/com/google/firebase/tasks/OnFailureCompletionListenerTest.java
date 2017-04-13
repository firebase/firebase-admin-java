package com.google.firebase.tasks;

import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.firebase.tasks.testing.TestOnFailureListener;
import com.google.firebase.testing.MockitoTestRule;
import java.rmi.RemoteException;
import java.util.concurrent.Executor;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public class OnFailureCompletionListenerTest {

  @Rule public MockitoTestRule mockitoTestRule = new MockitoTestRule();

  @Mock private Executor mockExecutor;

  @Test
  public void testOnComplete_nothingExecutedAfterCancel() {
    OnFailureCompletionListener<Void> listener =
        new OnFailureCompletionListener<>(mockExecutor, new TestOnFailureListener());
    listener.cancel();

    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(new RemoteException());
    listener.onComplete(task);

    verifyZeroInteractions(mockExecutor);
  }

  @Test
  public void testOnComplete_nothingExecutedOnSuccess() {
    OnFailureCompletionListener<Void> listener =
        new OnFailureCompletionListener<>(mockExecutor, new TestOnFailureListener());

    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    listener.onComplete(task);

    verifyZeroInteractions(mockExecutor);
  }
}
