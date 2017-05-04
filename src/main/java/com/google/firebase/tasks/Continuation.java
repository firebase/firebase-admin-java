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

package com.google.firebase.tasks;

import com.google.firebase.internal.NonNull;

/**
 * A function that is called to continue execution after completion of a {@link Task}.
 *
 * @param <T> the Task's result type
 * @param <R> the Continuation's result type
 * @see Task#continueWith(Continuation)
 * @see Task#continueWithTask(Continuation)
 */
public interface Continuation<T, R> {

  /**
   * Returns the result of applying this Continuation to {@code task}.
   *
   * <p>To propagate failure from the completed Task call {@link Task#getResult()} and allow the
   * {@link RuntimeExecutionException} to propagate. The RuntimeExecutionException will be unwrapped
   * such that the Task returned by {@link Task#continueWith(Continuation)} or {@link
   * Task#continueWithTask(Continuation)} fails with the original exception.
   *
   * <p>To suppress specific failures call {@link Task#getResult(Class)} and catch the exception
   * types of interest:
   *
   * <pre class="prettyprint">
   * task.continueWith(new Continuation&lt;String, String&gt;() {
   * {@literal @}Override
   * public String then(Task&lt;String&gt; task) {
   * try {
   * return task.getResult(IOException.class);
   * } catch (FileNotFoundException e) {
   * return "Not found";
   * } catch (IOException e) {
   * return "Read failed";
   * }
   * }
   * }
   * </pre>
   *
   * <p>To suppress all failures guard any calls to {@link Task#getResult()} with {@link
   * Task#isSuccessful()}:
   *
   * <pre class="prettyprint">
   * task.continueWith(new Continuation&lt;String, String&gt;() {
   * {@literal @}Override
   * public String then(Task&lt;String&gt; task) {
   * if (task.isSuccessful()) {
   * return task.getResult();
   * } else {
   * return DEFAULT_VALUE;
   * }
   * }
   * }
   * </pre>
   *
   * @param task the completed Task. Never null
   * @throws Exception if the result couldn't be produced
   */
  R then(@NonNull Task<T> task) throws Exception;
}
