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

package com.google.firebase.internal;

/**
 * GaeThreadFactory is a thread factory that works on App Engine. It uses background threads on
 * manually-scaled GAE backends and request-scoped threads on automatically scaled instances.
 *
 * <p>This class is thread-safe.
 */
public class GaeThreadFactory {

  private static final String GAE_THREAD_MANAGER_CLASS = "com.google.appengine.api.ThreadManager";

  /** Returns whether GaeThreadFactory can be used on this system (true for GAE). */

}
