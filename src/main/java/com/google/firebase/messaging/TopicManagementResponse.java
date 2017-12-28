package com.google.firebase.messaging;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;

public class TopicManagementResponse {

  private final int successCount;
  private final int failureCount;
  private final List<Error> errors;

  TopicManagementResponse(List<Map> results) {
    int successCount = 0;
    int failureCount = 0;
    ImmutableList.Builder<Error> errors = ImmutableList.builder();
    for (int i = 0; i < results.size(); i++) {
      Map result = results.get(i);
      if (result.isEmpty()) {
        successCount++;
      } else {
        failureCount++;
        errors.add(new Error(i, (String) result.get("error")));
      }
    }
    this.successCount = successCount;
    this.failureCount = failureCount;
    this.errors = errors.build();
  }

  public int getSuccessCount() {
    return successCount;
  }

  public int getFailureCount() {
    return failureCount;
  }

  public List<Error> getErrors() {
    return errors;
  }

  public static class Error {
    private final int index;
    private final String reason;

    private Error(int index, String reason) {
      this.index = index;
      this.reason = reason;
    }

    public int getIndex() {
      return index;
    }

    public String getReason() {
      return reason;
    }
  }
}
