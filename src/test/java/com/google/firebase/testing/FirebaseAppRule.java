package com.google.firebase.testing;

import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseAppStore;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Makes sure that all FirebaseApp state is cleared before and after tests.
 */
public class FirebaseAppRule implements TestRule {

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        resetState();
        try {
          base.evaluate();
        } finally {
          resetState();
        }
      }
    };
  }

  private void resetState() {
    TestOnlyImplFirebaseTrampolines.clearInstancesForTest();
    FirebaseAppStore.clearInstanceForTest();
  }
}
