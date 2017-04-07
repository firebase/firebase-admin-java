package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;

import com.google.firebase.database.TestConstants;
import java.net.URI;
import org.junit.Test;

public class RepoInfoTest {

  @Test
  public void getConnectionURLTestOverloadWorks() {
    RepoInfo info = new RepoInfo();
    info.host = TestConstants.TEST_REPO + "." + TestConstants.TEST_SERVER;
    info.internalHost = info.host;
    info.secure = false;
    info.namespace = TestConstants.TEST_REPO;
    URI url = info.getConnectionURL(null);
    assertEquals("ws://tests.fblocal.com:9000/.ws?ns=tests&v=5", url.toString());
    url = info.getConnectionURL("test");
    assertEquals("ws://tests.fblocal.com:9000/.ws?ns=tests&v=5&ls=test", url.toString());
  }
}
