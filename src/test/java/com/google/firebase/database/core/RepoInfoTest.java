package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import org.junit.Test;

public class RepoInfoTest {

  @Test
  public void getConnectionURLTestOverloadWorks() {
    final String repo = "tests";
    final String server = "admin-java-sdk.firebaseio.com:9000";

    RepoInfo info = new RepoInfo();
    info.host = repo + "." + server;
    info.internalHost = info.host;
    info.secure = false;
    info.namespace = repo;
    URI url = info.getConnectionURL(null);
    assertEquals("ws://tests.admin-java-sdk.firebaseio.com:9000/.ws?ns=tests&v=5",
        url.toString());
    url = info.getConnectionURL("test");
    assertEquals("ws://tests.admin-java-sdk.firebaseio.com:9000/.ws?ns=tests&v=5&ls=test",
        url.toString());
  }
}
