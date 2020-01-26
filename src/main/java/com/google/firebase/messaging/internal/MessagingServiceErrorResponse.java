package com.google.firebase.messaging.internal;

import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.Nullable;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.List;
import java.util.Map;

/**
 * The DTO for parsing error responses from the FCM service.
 */
public final class MessagingServiceErrorResponse extends GenericJson {

  private static final Map<String, MessagingErrorCode> MESSAGING_ERROR_CODES =
      ImmutableMap.<String, MessagingErrorCode>builder()
          .put("APNS_AUTH_ERROR", MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)
          .put("INTERNAL", MessagingErrorCode.INTERNAL)
          .put("INVALID_ARGUMENT", MessagingErrorCode.INVALID_ARGUMENT)
          .put("QUOTA_EXCEEDED", MessagingErrorCode.QUOTA_EXCEEDED)
          .put("SENDER_ID_MISMATCH", MessagingErrorCode.SENDER_ID_MISMATCH)
          .put("THIRD_PARTY_AUTH_ERROR", MessagingErrorCode.THIRD_PARTY_AUTH_ERROR)
          .put("UNAVAILABLE", MessagingErrorCode.UNAVAILABLE)
          .put("UNREGISTERED", MessagingErrorCode.UNREGISTERED)
          .build();

  private static final String FCM_ERROR_TYPE =
      "type.googleapis.com/google.firebase.fcm.v1.FcmError";

  @Key("error")
  private Map<String, Object> error;

  public String getStatus() {
    if (error == null) {
      return null;
    }

    return (String) error.get("status");
  }


  @Nullable
  public MessagingErrorCode getMessagingErrorCode() {
    if (error == null) {
      return null;
    }

    Object details = error.get("details");
    if (details instanceof List) {
      for (Object detail : (List<?>) details) {
        if (detail instanceof Map) {
          Map<?,?> detailMap = (Map<?,?>) detail;
          if (FCM_ERROR_TYPE.equals(detailMap.get("@type"))) {
            String errorCode = (String) detailMap.get("errorCode");
            return MESSAGING_ERROR_CODES.get(errorCode);
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public String getErrorMessage() {
    if (error != null) {
      return (String) error.get("message");
    }

    return null;
  }
}
