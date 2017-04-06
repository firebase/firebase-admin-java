package com.google.firebase.tasks;

import com.google.firebase.tasks.testing.TestOnCompleteListener;
import com.google.firebase.testing.MockitoTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Executor;

import static org.mockito.Mockito.verifyZeroInteractions;

public class OnCompleteCompletionListenerTest {

  @Rule
  public MockitoTestRule mMockitoTestRule = new MockitoTestRule();

  @Mock
  private Executor mMockExecutor;

  @Test
  public void testOnComplete_nothingExecutedAfterCancel() {
    OnCompleteCompletionListener<Void> listener =
        new OnCompleteCompletionListener<>(mMockExecutor, new TestOnCompleteListener<Void>());
    listener.cancel();

    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    listener.onComplete(task);

    verifyZeroInteractions(mMockExecutor);
  }
}
