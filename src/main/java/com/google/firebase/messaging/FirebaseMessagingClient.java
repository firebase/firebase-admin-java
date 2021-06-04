package com.google.firebase.messaging;

import java.util.List;

/**
 * An interface for sending Firebase Cloud Messaging (FCM) messages.
 */
interface FirebaseMessagingClient {

  /**
   * Sends the given message with FCM.
   *
   * @param message A non-null {@link Message} to be sent.
   * @param dryRun A boolean indicating whether to perform a dry run (validation only) of the send.
   * @return A message ID string.
   * @throws FirebaseMessagingException If an error occurs while handing the message off to FCM for
   *     delivery.
   */
  String send(Message message, boolean dryRun) throws  FirebaseMessagingException;

  /**
   * Sends all the messages in the given list with FCM.
   *
   * @param messages A non-null, non-empty list of messages.
   * @param dryRun A boolean indicating whether to perform a dry run (validation only) of the send.
   * @return A {@link BatchResponse} indicating the result of the operation.
   * @throws FirebaseMessagingException If an error occurs while handing the messages off to FCM for
   *     delivery.
   */
  BatchResponse sendAll(List<Message> messages, boolean dryRun) throws FirebaseMessagingException;

}
