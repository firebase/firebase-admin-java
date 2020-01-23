/*
 * Copyright 2020 Google Inc.
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

import com.google.api.client.http.HttpResponseException;
import com.google.firebase.FirebaseException;
import com.google.firebase.IncomingHttpResponse;
import java.io.IOException;

/**
 * An interface for handling all sorts of exceptions that may occur while making an HTTP call and
 * converting them into some instance of FirebaseException.
 */
public interface HttpErrorHandler<T extends FirebaseException> {

  /**
   * Handle any low-level transport and initialization errors.
   */
  T handleIOException(IOException e);

  /**
   * Handle HTTP response exceptions (caused by HTTP error responses).
   */
  T handleHttpResponseException(HttpResponseException e, IncomingHttpResponse response);

  /**
   * Handle any errors that may occur while parsing the response payload.
   */
  T handleParseException(IOException e, IncomingHttpResponse response);
}
