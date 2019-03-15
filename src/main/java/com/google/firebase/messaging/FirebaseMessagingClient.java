package com.google.firebase.messaging;

import java.util.List;

interface FirebaseMessagingClient {

  String send(Message message, boolean dryRun) throws  FirebaseMessagingException;

  BatchResponse sendAll(List<Message> messages, boolean dryRun) throws FirebaseMessagingException;

}
