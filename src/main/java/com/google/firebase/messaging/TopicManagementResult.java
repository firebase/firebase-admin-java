package com.google.firebase.messaging;

import java.util.Map;

public class TopicManagementResult {

  private final boolean success;
  private final String reason;

  TopicManagementResult(Map<String, Object> response) {
    this.success = response.isEmpty();
    this.reason = (String) response.get("error");
  }

  public boolean isSuccess() {
    return success;
  }

  public String getReason() {
    return reason;
  }
}
