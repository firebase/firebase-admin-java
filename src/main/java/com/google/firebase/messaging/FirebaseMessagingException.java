package com.google.firebase.messaging;

import com.google.firebase.FirebaseException;

public class FirebaseMessagingException extends FirebaseException {

  FirebaseMessagingException(String detailMessage, Throwable cause) {
    super(detailMessage, cause);
  }
}
