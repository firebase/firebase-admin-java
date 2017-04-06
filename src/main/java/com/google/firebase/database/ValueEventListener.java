package com.google.firebase.database;

/**
 * Classes implementing this interface can be used to receive events about data changes at a
 * location. Attach the listener to a location user {@link
 * DatabaseReference#addValueEventListener(ValueEventListener)}.
 */
public interface ValueEventListener {

  /**
   * This method will be called with a snapshot of the data at this location. It will also be called
   * each time that data changes.
   *
   * @param snapshot The current data at the location
   */
  void onDataChange(DataSnapshot snapshot);

  /**
   * This method will be triggered in the event that this listener either failed at the server, or
   * is removed as a result of the security and Firebase Database rules. For more information on
   * securing your data, see: <a
   * href="https://firebase.google.com/docs/database/security/quickstart" target="_blank"> Security
   * Quickstart</a>
   *
   * @param error A description of the error that occurred
   */
  void onCancelled(DatabaseError error);
}
