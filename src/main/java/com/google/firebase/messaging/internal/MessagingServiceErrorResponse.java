package com.google.firebase.messaging.internal;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.firebase.internal.Nullable;
import java.util.List;
import java.util.Map;

/**
 * The DTO for parsing error responses from the FCM service.
 */
public class MessagingServiceErrorResponse extends GenericJson {

  private static final String FCM_ERROR_TYPE =
      "type.googleapis.com/google.firebase.fcm.v1.FcmError";

  @Key("error")
  private Map<String, Object> error;

  @Nullable
  public String getErrorCode() {
    if (error == null) {
      return null;
    }
    Object details = error.get("details");
    if (details != null && details instanceof List) {
      for (Object detail : (List<?>) details) {
        if (detail instanceof Map) {
          Map<?,?> detailMap = (Map<?,?>) detail;
          if (FCM_ERROR_TYPE.equals(detailMap.get("@type"))) {
            return (String) detailMap.get("errorCode");
          }
        }
      }
    }
    return (String) error.get("status");
  }

  @Nullable
  public String getErrorMessage() {
    if (error != null) {
      return (String) error.get("message");
    }
    return null;
  }
}
