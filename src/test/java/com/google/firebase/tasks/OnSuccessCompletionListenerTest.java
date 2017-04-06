package com.google.firebase.tasks;

import com.google.firebase.tasks.testing.TestOnSuccessListener;
import com.google.firebase.testing.MockitoTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.rmi.RemoteException;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.verifyZeroInteractions;

public class OnSuccessCompletionListenerTest {

  @Rule public MockitoTestRule mMockitoTestRule = new MockitoTestRule();

  @Mock private Executor mMockExecutor;

  @Test
  public void testOnComplete_nothingExecutedAfterCancel() {
    OnSuccessCompletionListener<Void> listener =
        new OnSuccessCompletionListener<>(mMockExecutor, new TestOnSuccessListener<>());
    listener.cancel();

    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    listener.onComplete(task);

    verifyZeroInteractions(mMockExecutor);
  }

  @Test
  public void testOnComplete_nothingExecutedOnFailure() {
    OnSuccessCompletionListener<Void> listener =
        new OnSuccessCompletionListener<>(mMockExecutor, new TestOnSuccessListener<>());

    TaskImpl<Void> task = new TaskImpl<>();
    task.setException(new RemoteException());
    listener.onComplete(task);

    verifyZeroInteractions(mMockExecutor);
  }
}
