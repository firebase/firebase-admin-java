package com.google.firebase.database.core.view;

import com.google.firebase.database.core.EventRegistration;
import java.util.List;

public class ViewAccess {

  // Provides test access to event registrations on a view.
  public static List<EventRegistration> getEventRegistrations(View view) {
    return view.getEventRegistrations();
  }
}
