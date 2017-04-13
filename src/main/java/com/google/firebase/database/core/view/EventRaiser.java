package com.google.firebase.database.core.view;

import com.google.firebase.database.core.Context;
import com.google.firebase.database.core.EventTarget;
import com.google.firebase.database.logging.LogWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Each view owns an instance of this class, and it is used to send events to the event target
 * thread.
 *
 * <p>Note that it is safe to post events directly to that thread, since a shutdown will not occur
 * unless there are no listeners. If there are no listeners, all instances of this class will be
 * cleaned up.
 */
public class EventRaiser {

  private final EventTarget eventTarget;
  private final LogWrapper logger;

  public EventRaiser(Context ctx) {
    eventTarget = ctx.getEventTarget();
    logger = ctx.getLogger("EventRaiser");
  }

  public void raiseEvents(final List<? extends Event> events) {
    if (logger.logsDebug()) {
      logger.debug("Raising " + events.size() + " event(s)");
    }
    // TODO: Use an immutable data structure for events so we don't have to clone to be safe.
    final ArrayList<Event> eventsClone = new ArrayList<>(events);
    eventTarget.postEvent(
        new Runnable() {
          @Override
          public void run() {
            for (Event event : eventsClone) {
              if (logger.logsDebug()) {
                logger.debug("Raising " + event.toString());
              }
              event.fire();
            }
          }
        });
  }
}
