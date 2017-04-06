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

  @Rule
  public MockitoTestRule mMockitoTestRule = new MockitoTestRule();

  @Mock
  private Executor mMockExecutor;

  @Test
  public void testOnComplete_nothingExecutedAfterCancel() {
    OnFailureCompletionListener<Void> listener =
        new OnFailureCompletionListener<>(mMockExecutor, new TestOnFailureListener());
    listener.cancel();

    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(new RemoteException());
    listener.onComplete(task);

    verifyZeroInteractions(mMockExecutor);
  }

  @Test
  public void testOnComplete_nothingExecutedOnSuccess() {
    OnFailureCompletionListener<Void> listener =
        new OnFailureCompletionListener<>(mMockExecutor, new TestOnFailureListener());

    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    listener.onComplete(task);

    verifyZeroInteractions(mMockExecutor);
  }
}
