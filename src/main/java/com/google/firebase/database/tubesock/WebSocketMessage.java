package com.google.firebase.database.tubesock;

public class WebSocketMessage {

  private byte[] byteMessage;
  private String stringMessage;
  private byte opcode;

  public WebSocketMessage(byte[] message) {
    this.byteMessage = message;
    this.opcode = WebSocket.OPCODE_BINARY;
  }

  public WebSocketMessage(String message) {
    this.stringMessage = message;
    this.opcode = WebSocket.OPCODE_TEXT;
  }

  public boolean isText() {
    return opcode == WebSocket.OPCODE_TEXT;
  }

  public boolean isBinary() {
    return opcode == WebSocket.OPCODE_BINARY;
  }

  public byte[] getBytes() {
    return byteMessage;
  }

  public String getText() {
    return stringMessage;
  }
}
