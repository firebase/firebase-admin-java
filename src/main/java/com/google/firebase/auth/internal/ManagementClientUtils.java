package com.google.firebase.auth.internal;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class ManagementClientUtils {
    
  public static final String CONFIGURATION_NOT_FOUND_ERROR = "configuration-not-found";
  public static final String TENANT_ID_MISMATCH_ERROR = "tenant-id-mismatch";
  public static final String TENANT_NOT_FOUND_ERROR = "tenant-not-found";
  public static final String USER_NOT_FOUND_ERROR = "user-not-found";
  public static final String INTERNAL_ERROR = "internal-error";

  // Map of server-side error codes to SDK error codes.
  // SDK error codes defined at: https://firebase.google.com/docs/auth/admin/errors
  public static final Map<String, String> ERROR_CODES = ImmutableMap.<String, String>builder()
      .put("CLAIMS_TOO_LARGE", "claims-too-large")
      .put("CONFIGURATION_NOT_FOUND", CONFIGURATION_NOT_FOUND_ERROR)
      .put("INSUFFICIENT_PERMISSION", "insufficient-permission")
      .put("DUPLICATE_EMAIL", "email-already-exists")
      .put("DUPLICATE_LOCAL_ID", "uid-already-exists")
      .put("EMAIL_EXISTS", "email-already-exists")
      .put("INVALID_CLAIMS", "invalid-claims")
      .put("INVALID_EMAIL", "invalid-email")
      .put("INVALID_PAGE_SELECTION", "invalid-page-token")
      .put("INVALID_PHONE_NUMBER", "invalid-phone-number")
      .put("PHONE_NUMBER_EXISTS", "phone-number-already-exists")
      .put("PROJECT_NOT_FOUND", "project-not-found")
      .put("TENANT_ID_MISMATCH", TENANT_ID_MISMATCH_ERROR)
      .put("TENANT_NOT_FOUND", TENANT_NOT_FOUND_ERROR)
      .put("USER_NOT_FOUND", USER_NOT_FOUND_ERROR)
      .put("WEAK_PASSWORD", "invalid-password")
      .put("UNAUTHORIZED_DOMAIN", "unauthorized-continue-uri")
      .put("INVALID_DYNAMIC_LINK_DOMAIN", "invalid-dynamic-link-domain")
      .build();
}