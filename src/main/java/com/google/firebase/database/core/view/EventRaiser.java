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

package com.google.firebase.database.core.view;

import com.google.firebase.database.core.Context;
import com.google.firebase.database.core.EventTarget;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Each view owns an instance of this class, and it is used to send events to the event target
 * thread.
 *
 * <p>Note that it is safe to post events directly to that thread, since a shutdown will not occur
 * unless there are no listeners. If there are no listeners, all instances of this class will be
 * cleaned up.
 */
public class EventRaiser {

  private static final Logger logger = LoggerFactory.getLogger(EventRaiser.class);

  private final EventTarget eventTarget;

  public EventRaiser(Context ctx) {
    eventTarget = ctx.getEventTarget();
  }

  public void raiseEvents(final List<? extends Event> events) {
    logger.debug("Raising {} event(s)", events.size());
    // TODO: Use an immutable data structure for events so we don't have to clone to be safe.
    final ArrayList<Event> eventsClone = new ArrayList<>(events);
    eventTarget.postEvent(
        new Runnable() {
          @Override
          public void run() {
            for (Event event : eventsClone) {
              logger.debug("Raising {}", event);
              event.fire();
            }
          }
        });
  }
}
