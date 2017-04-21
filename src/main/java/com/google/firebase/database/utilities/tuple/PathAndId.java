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

package com.google.firebase.database.utilities.tuple;

import com.google.firebase.database.core.Path;

/** User: greg Date: 5/22/13 Time: 12:21 PM */
public class PathAndId {

  private Path path;
  private long id;

  public PathAndId(Path path, long id) {
    this.path = path;
    this.id = id;
  }

  public Path getPath() {
    return path;
  }

  public long getId() {
    return id;
  }
}
