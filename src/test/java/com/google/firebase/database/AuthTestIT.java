package com.google.firebase.database;

import com.firebase.security.token.TokenGenerator;
import com.firebase.security.token.TokenOptions;
import com.google.firebase.database.core.DatabaseConfig;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AuthTestIT {

  @After
  public void tearDown() {
    TestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void authWithApplicationSecret() throws DatabaseException, InterruptedException {
    DatabaseConfig cfg = TestHelpers.newTestConfig();
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(cfg));
    cfg.setAuthTokenProvider(provider);
    DatabaseReference ref = TestHelpers.rootWithConfig(cfg);
    TestHelpers.assertSuccessfulAuth(ref, provider, TestHelpers.getTestSecret());
  }

  @Test
  public void authAsAdminWithToken() throws DatabaseException, InterruptedException {
    TokenGenerator tokenGenerator = new TokenGenerator(TestHelpers.getTestSecret());
    TokenOptions options = new TokenOptions();
    options.setAdmin(true);
    Map<String, Object> authObj = new HashMap<>();
    authObj.put("uid", "uid");
    String token = tokenGenerator.createToken(authObj, options);

    DatabaseConfig cfg = TestHelpers.newTestConfig();
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(cfg));
    cfg.setAuthTokenProvider(provider);
    DatabaseReference ref = TestHelpers.rootWithConfig(cfg);
    TestHelpers.assertSuccessfulAuth(ref, provider, token);
  }

  @Test
  public void authAsUserWithValidTokens() throws DatabaseException, InterruptedException {
    Map<String, Object> authObj = new HashMap<>();
    authObj.put("test", "data");
    authObj.put("uid", "uid");
    TokenGenerator generator = new TokenGenerator(TestHelpers.getTestSecret());
    String token = generator.createToken(authObj);

    DatabaseConfig cfg = TestHelpers.newTestConfig();
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(cfg));
    cfg.setAuthTokenProvider(provider);
    DatabaseReference ref = TestHelpers.rootWithConfig(cfg);
    TestHelpers.assertSuccessfulAuth(ref, provider, token);
  }

  @Test
  public void expiresIsProperlyEnforced() throws DatabaseException, InterruptedException {
    DatabaseConfig cfg = TestHelpers.newTestConfig();
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(cfg));
    cfg.setAuthTokenProvider(provider);
    DatabaseReference ref = TestHelpers.rootWithConfig(cfg);

    long oneSecondFromNow = System.currentTimeMillis() + 1000;
    TokenOptions options = new TokenOptions();
    options.setExpires(new Date(oneSecondFromNow));
    TokenGenerator generator = new TokenGenerator(TestHelpers.getTestSecret());
    Map<String, Object> authObj = new HashMap<>();
    authObj.put("uid", "test-uid");
    String token = generator.createToken(authObj, options);

    final List<Boolean> authenticatedStates = new ArrayList<>();

    final Semaphore semaphore = new Semaphore(0);

    DatabaseReference authRef = ref.getRoot().child(".info/authenticated");

    ValueEventListener listener =
        authRef.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                authenticatedStates.add(snapshot.getValue(Boolean.class));
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                throw new RuntimeException("Should not happen");
              }
            });

    provider.setToken(token);
    provider.setNextToken(TestHelpers.getTestSecret());

    TestHelpers.waitFor(semaphore, 4);
    assertEquals(Arrays.asList(false, true, false, true), authenticatedStates);

    authRef.removeEventListener(listener);
  }

  @Test
  public void dotInfoAuthenticatedWorks() throws DatabaseException, InterruptedException {
    DatabaseConfig cfg = TestHelpers.newTestConfig();
    TestTokenProvider provider = new TestTokenProvider(TestHelpers.getExecutorService(cfg));
    cfg.setAuthTokenProvider(provider);
    DatabaseReference ref = TestHelpers.rootWithConfig(cfg);

    final Semaphore semaphore = new Semaphore(0);
    final List<Boolean> results = new ArrayList<>();
    ref.getRoot()
        .child(".info/authenticated")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                results.add(snapshot.getValue(Boolean.class));
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not be cancelled");
              }
            });

    TokenGenerator generator = new TokenGenerator(TestHelpers.getTestSecret());
    Map<String, Object> authObj = new HashMap<>();
    authObj.put("uid", "test-uid");
    String token = generator.createToken(authObj);
    provider.setToken(token);

    // Wait for successful authentication, including initial unauthenticated event
    TestHelpers.waitFor(semaphore, 2);

    provider.setToken(null);

    // Wait for unauthentication
    TestHelpers.waitFor(semaphore, 1);
    List<Boolean> expected = new ArrayList<>();
    expected.addAll(Arrays.asList(false, true, false));
    DeepEquals.assertEquals(expected, results);
  }
}
