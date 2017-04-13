package com.google.firebase.tasks;

import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.firebase.tasks.testing.TestOnCompleteListener;
import com.google.firebase.testing.MockitoTestRule;
import java.util.concurrent.Executor;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public class OnCompleteCompletionListenerTest {

  @Rule public MockitoTestRule mockitoTestRule = new MockitoTestRule();

  @Mock private Executor mockExecutor;

  @Test
  public void testOnComplete_nothingExecutedAfterCancel() {
    OnCompleteCompletionListener<Void> listener =
        new OnCompleteCompletionListener<>(mockExecutor, new TestOnCompleteListener<Void>());
    listener.cancel();

    TaskImpl<Void> task = new TaskImpl<>();
    task.setResult(null);
    listener.onComplete(task);

    verifyZeroInteractions(mockExecutor);
  }
}
