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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TemplateTest {

  @Test
  public void testConstructor() {
    Template template = new Template();

    assertNotNull(template.getParameters());
    assertNotNull(template.getConditions());
    assertNotNull(template.getParameterGroups());
    assertTrue(template.getParameters().isEmpty());
    assertTrue(template.getConditions().isEmpty());
    assertTrue(template.getParameterGroups().isEmpty());
    assertNull(template.getETag());
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorWithNullTemplateResponse() {
    new Template(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameters() {
    Template template = new Template();
    template.setParameters(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullConditions() {
    Template template = new Template();
    template.setConditions(null);
  }

  @Test(expected = NullPointerException.class)
  public void testSetNullParameterGroups() {
    Template template = new Template();
    template.setParameterGroups(null);
  }
}
