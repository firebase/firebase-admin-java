/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.database.core;

import static org.junit.Assert.assertEquals;

import java.net.URI;
import org.junit.Test;

public class RepoInfoTest {

  @Test
  public void getConnectionURLTestOverloadWorks() {
    final String repo = "tests";
    final String server = "admin-java-sdk.firebaseio.com";

    RepoInfo info = new RepoInfo();
    info.host = repo + "." + server;
    info.internalHost = info.host;
    info.secure = false;
    info.namespace = repo;
    URI url = info.getConnectionURL(null);
    assertEquals("ws://tests.admin-java-sdk.firebaseio.com/.ws?ns=tests&v=5",
        url.toString());
    url = info.getConnectionURL("test");
    assertEquals("ws://tests.admin-java-sdk.firebaseio.com/.ws?ns=tests&v=5&ls=test",
        url.toString());
  }
}
