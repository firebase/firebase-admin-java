/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.firebase.remoteconfig.internal.TemplateResponse;
import org.junit.Test;

public class UserTest {

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullVersionResponse() {
    new User(null);
  }

  @Test
  public void testEquality() {
    final User userOne = new User(new TemplateResponse.UserResponse());
    final User userTwo = new User(new TemplateResponse.UserResponse());

    assertEquals(userOne, userTwo);

    final User userThree = new User(new TemplateResponse.UserResponse()
            .setName("admin-user")
            .setEmail("admin@email.com")
            .setImageUrl("http://admin.jpg"));
    final User userFour = new User(new TemplateResponse.UserResponse()
            .setName("admin-user")
            .setEmail("admin@email.com")
            .setImageUrl("http://admin.jpg"));

    assertEquals(userThree, userFour);

    final User userFive = new User(new TemplateResponse.UserResponse()
            .setName("admin-user")
            .setEmail("admin@email.com"));
    final User userSix = new User(new TemplateResponse.UserResponse()
            .setName("admin-user")
            .setEmail("admin@email.com"));

    assertEquals(userFive, userSix);
    assertNotEquals(userOne, userThree);
    assertNotEquals(userOne, userFive);
    assertNotEquals(userThree, userFive);
  }
}
