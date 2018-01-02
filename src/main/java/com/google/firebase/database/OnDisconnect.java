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

package com.google.firebase.database;

import com.google.api.core.ApiFuture;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.Repo;
import com.google.firebase.database.core.ValidationPath;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.PriorityUtilities;
import com.google.firebase.database.utilities.Pair;
import com.google.firebase.database.utilities.Utilities;
import com.google.firebase.database.utilities.Validation;
import com.google.firebase.database.utilities.encoding.CustomClassMapper;
import com.google.firebase.internal.TaskToApiFuture;
import com.google.firebase.tasks.Task;

import java.util.Map;

/**
 * The OnDisconnect class is used to manage operations that will be run on the server when this
 * client disconnects. It can be used to add or remove data based on a client's connection status.
 * It is very useful in applications looking for 'presence' functionality. <br>
 * <br>
 * Instances of this class are obtained by calling {@link DatabaseReference#onDisconnect()
 * onDisconnect} on a Firebase Database ref.
 */
@SuppressWarnings("rawtypes")
public class OnDisconnect {

  private Repo repo;
  private Path path;

  OnDisconnect(Repo repo, Path path) {
    this.repo = repo;
    this.path = path;
  }

  /**
   * Similar to {@link #setValueAsync(Object)}, but returns a Task.
   *
   * @param value The value to be set when a disconnect occurs
   * @return The {@link Task} for this operation.
   * @deprecated Use {@link #setValueAsync(Object)}
   */
  public Task<Void> setValue(Object value) {
    return onDisconnectSetInternal(value, PriorityUtilities.NullPriority(), null);
  }

  /**
   * Similar to {@link #setValueAsync(Object, String)}, but returns a Task.
   *
   * @param value The value to be set when a disconnect occurs
   * @param priority The priority to be set when a disconnect occurs
   * @return The {@link Task} for this operation.
   * @deprecated Use {@link #setValueAsync(Object, String)}
   */
  public Task<Void> setValue(Object value, String priority) {
    return onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), null);
  }

  /**
   * Similar to {@link #setValueAsync(Object, double)}, but returns a Task.
   *
   * @param value The value to be set when a disconnect occurs
   * @param priority The priority to be set when a disconnect occurs
   * @return The {@link Task} for this operation.
   * @deprecated Use {@link #setValueAsync(Object, double)}
   */
  public Task<Void> setValue(Object value, double priority) {
    return onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), null);
  }

  /**
   * Ensure the data at this location is set to the specified value when the client is disconnected
   * (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(Object value, CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.NullPriority(), listener);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs
   * @param priority The priority to be set when a disconnect occurs
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(Object value, String priority, CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), listener);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs
   * @param priority The priority to be set when a disconnect occurs
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(Object value, double priority, CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), listener);
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs
   * @param priority The priority to be set when a disconnect occurs
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void setValue(Object value, Map priority, CompletionListener listener) {
    onDisconnectSetInternal(value, PriorityUtilities.parsePriority(path, priority), listener);
  }

  /**
   * Ensure the data at this location is set to the specified value when the client is disconnected
   * (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs
   * @return The ApiFuture for this operation.
   */
  public ApiFuture<Void> setValueAsync(Object value) {
    return new TaskToApiFuture<>(setValue(value));
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs
   * @param priority The priority to be set when a disconnect occurs
   * @return The ApiFuture for this operation.
   */
  public ApiFuture<Void> setValueAsync(Object value, String priority) {
    return new TaskToApiFuture<>(setValue(value, priority));
  }

  /**
   * Ensure the data at this location is set to the specified value and priority when the client is
   * disconnected (due to closing the browser, navigating to a new page, or network issues). <br>
   * <br>
   * This method is especially useful for implementing "presence" systems, where a value should be
   * changed or cleared when a user disconnects so that they appear "offline" to other users.
   *
   * @param value The value to be set when a disconnect occurs
   * @param priority The priority to be set when a disconnect occurs
   * @return The ApiFuture for this operation.
   */
  public ApiFuture<Void> setValueAsync(Object value, double priority) {
    return new TaskToApiFuture<>(setValue(value, priority));
  }

  private Task<Void> onDisconnectSetInternal(
      Object value, Node priority, final CompletionListener optListener) {
    Validation.validateWritablePath(path);
    ValidationPath.validateWithObject(path, value);
    Object bouncedValue = CustomClassMapper.convertToPlainJavaTypes(value);
    Validation.validateWritableObject(bouncedValue);
    final Node node = NodeUtilities.NodeFromJSON(bouncedValue, priority);
    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.onDisconnectSetValue(path, node, wrapped.getSecond());
          }
        });
    return wrapped.getFirst();
  }

  // Update

  /**
   * Similar to {@link #updateChildrenAsync(Map)}, but returns a Task.
   *
   * @param update The paths to update, along with their desired values
   * @return The {@link Task} for this operation.
   * @deprecated Use {@link #updateChildrenAsync(Map)}
   */
  public Task<Void> updateChildren(Map<String, Object> update) {
    return updateChildrenInternal(update, null);
  }

  /**
   * Ensure the data has the specified child values updated when the client is disconnected
   *
   * @param update The paths to update, along with their desired values
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void updateChildren(final Map<String, Object> update, final CompletionListener listener) {
    updateChildrenInternal(update, listener);
  }

  /**
   * Ensure the data has the specified child values updated when the client is disconnected
   *
   * @param update The paths to update, along with their desired values
   * @return The ApiFuture for this operation.
   */
  public ApiFuture<Void> updateChildrenAsync(Map<String, Object> update) {
    return new TaskToApiFuture<>(updateChildren(update));
  }

  private Task<Void> updateChildrenInternal(
      final Map<String, Object> update, final CompletionListener optListener) {
    final Map<Path, Node> parsedUpdate = Validation.parseAndValidateUpdate(path, update);
    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.onDisconnectUpdate(path, parsedUpdate, wrapped.getSecond(), update);
          }
        });
    return wrapped.getFirst();
  }

  // Remove

  /**
   * Similar to {@link #removeValueAsync()}, but returns a Task.
   *
   * @return The {@link Task} for this operation.
   * @deprecated Use {@link #removeValueAsync()}
   */
  public Task<Void> removeValue() {
    return setValue(null);
  }

  /**
   * Remove the value at this location when the client disconnects
   *
   * @param listener A listener that will be triggered once the server has queued up the operation
   */
  public void removeValue(CompletionListener listener) {
    setValue(null, listener);
  }

  /**
   * Remove the value at this location when the client disconnects
   *
   * @return The ApiFuture for this operation.
   */
  public ApiFuture<Void> removeValueAsync() {
    return new TaskToApiFuture<>(removeValue());
  }

  // Cancel the operation

  /**
   * Similar to {@link #cancelAsync()} ()}, but returns a Task.
   *
   * @return The {@link Task} for this operation.
   * @deprecated Use {@link #cancelAsync()}.
   */
  public Task<Void> cancel() {
    return cancelInternal(null);
  }

  /**
   * Cancel any disconnect operations that are queued up at this location
   *
   * @param listener A listener that will be triggered once the server has cancelled the operations
   */
  public void cancel(final CompletionListener listener) {
    cancelInternal(listener);
  }

  /**
   * Cancel any disconnect operations that are queued up at this location
   *
   * @return The ApiFuture for this operation.
   */
  public ApiFuture<Void> cancelAsync() {
    return new TaskToApiFuture<>(cancel());
  }

  private Task<Void> cancelInternal(final CompletionListener optListener) {
    final Pair<Task<Void>, CompletionListener> wrapped = Utilities.wrapOnComplete(optListener);
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            repo.onDisconnectCancel(path, wrapped.getSecond());
          }
        });
    return wrapped.getFirst();
  }
}
