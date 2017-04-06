package com.google.firebase.testing;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Test rule that calls {@link MockitoAnnotations#initMocks} before the test and
 * {@link Mockito#validateMockitoUsage()} after.
 */
public class MockitoTestRule implements MethodRule {

  @Override
  public Statement apply(final Statement base, FrameworkMethod method, final Object target) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        MockitoAnnotations.initMocks(target);
        try {
          base.evaluate();
        } finally {
          Mockito.validateMockitoUsage();
        }
      }
    };
  }
}
