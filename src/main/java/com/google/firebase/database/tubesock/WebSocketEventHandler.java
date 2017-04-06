package com.google.firebase.database.tubesock;


public interface WebSocketEventHandler {

  void onOpen();

  void onMessage(WebSocketMessage message);

  void onClose();

  void onError(WebSocketException e);

  void onLogMessage(String msg);
}
