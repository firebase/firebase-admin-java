/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.DateTime;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.internal.GetAccountInfoResponse.User;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains metadata associated with a Firebase user account. Instances of this class are immutable
 * and thread safe.
 */
public class UserRecord implements UserInfo {

  private static final String PROVIDER_ID = "firebase";
  private static final Map<String, String> REMOVABLE_FIELDS = ImmutableMap.of(
      "displayName", "DISPLAY_NAME",
      "photoUrl", "PHOTO_URL");
  static final String CUSTOM_ATTRIBUTES = "customAttributes";
  private static final int MAX_CLAIMS_PAYLOAD_SIZE = 1000;

  private final String uid;
  private final String tenantId;
  private final String email;
  private final String phoneNumber;
  private final boolean emailVerified;
  private final String displayName;
  private final String photoUrl;
  private final boolean disabled;
  private final ProviderUserInfo[] providers;
  private final long tokensValidAfterTimestamp;
  private final UserMetadata userMetadata;
  private final Map<String, Object> customClaims;

  UserRecord(User response, JsonFactory jsonFactory) {
    checkNotNull(response, "response must not be null");
    checkNotNull(jsonFactory, "jsonFactory must not be null");
    checkArgument(!Strings.isNullOrEmpty(response.getUid()), "uid must not be null or empty");
    this.uid = response.getUid();
    this.tenantId = response.getTenantId();
    this.email = response.getEmail();
    this.phoneNumber = response.getPhoneNumber();
    this.emailVerified = response.isEmailVerified();
    this.displayName = response.getDisplayName();
    this.photoUrl = response.getPhotoUrl();
    this.disabled = response.isDisabled();
    if (response.getProviders() == null || response.getProviders().length == 0) {
      this.providers = new ProviderUserInfo[0];
    } else {
      this.providers = new ProviderUserInfo[response.getProviders().length];
      for (int i = 0; i < this.providers.length; i++) {
        this.providers[i] = new ProviderUserInfo(response.getProviders()[i]);
      }
    }
    this.tokensValidAfterTimestamp = response.getValidSince() * 1000;

    String lastRefreshAtRfc3339 = response.getLastRefreshAt();
    long lastRefreshAtMillis = 0;
    if (!Strings.isNullOrEmpty(lastRefreshAtRfc3339)) {
      lastRefreshAtMillis = DateTime.parseRfc3339(lastRefreshAtRfc3339).getValue();
    }

    this.userMetadata = new UserMetadata(
        response.getCreatedAt(), response.getLastLoginAt(), lastRefreshAtMillis);
    this.customClaims = parseCustomClaims(response.getCustomClaims(), jsonFactory);
  }

  private Map<String, Object> parseCustomClaims(String customClaims, JsonFactory jsonFactory) {
    if (Strings.isNullOrEmpty(customClaims)) {
      return ImmutableMap.of();
    }
    try {
      Map<String, Object> parsed = new HashMap<>();
      jsonFactory.createJsonParser(customClaims).parseAndClose(parsed);
      return ImmutableMap.copyOf(parsed);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to parse custom claims json", e);
    }
  }

  /**
   * Returns the user ID of this user.
   *
   * @return a non-null, non-empty user ID string.
   */
  @Override
  public String getUid() {
    return uid;
  }

  /**
   * Returns the tenant ID associated with this user, if one exists.
   *
   * @return a tenant ID string or null.
   */
  @Nullable
  public String getTenantId() {
    return this.tenantId;
  }

  /**
   * Returns the provider ID of this user.
   *
   * @return a constant provider ID value.
   */
  @Override
  public String getProviderId() {
    return PROVIDER_ID;
  }

  /**
   * Returns the email address associated with this user.
   *
   * @return an email address string or null.
   */
  @Nullable
  @Override
  public String getEmail() {
    return email;
  }

  /**
   * Returns the phone number associated with this user.
   *
   * @return a phone number string or null.
   */
  @Nullable
  @Override
  public String getPhoneNumber() {
    return phoneNumber;
  }

  /**
   * Returns whether the email address of this user has been verified.
   *
   * @return true if the email has been verified, and false otherwise.
   */
  public boolean isEmailVerified() {
    return emailVerified;
  }

  /**
   * Returns the display name of this user.
   *
   * @return a display name string or null.
   */
  @Nullable
  @Override
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Returns the photo URL of this user.
   *
   * @return a URL string or null.
   */
  @Nullable
  @Override
  public String getPhotoUrl() {
    return photoUrl;
  }

  /**
   * Returns whether this user account is disabled.
   *
   * @return true if the user account is disabled, and false otherwise.
   */
  public boolean isDisabled() {
    return disabled;
  }

  /**
   * Returns an array of {@code UserInfo} objects that represents the identities from different
   * identity providers that are linked to this user.
   *
   * @return an array of {@link UserInfo} instances, which may be empty.
   */
  public UserInfo[] getProviderData() {
    return providers;
  }

  /**
   * Returns a timestamp in milliseconds since epoch, truncated down to the closest second.
   * Tokens minted before this timestamp are considered invalid.
   *
   * @return Timestamp in milliseconds since the epoch. Tokens minted before this timestamp are
   *     considered invalid.
   */
  public long getTokensValidAfterTimestamp() {
    return tokensValidAfterTimestamp;
  }

  /**
   * Returns additional metadata associated with this user.
   *
   * @return a non-null UserMetadata instance.
   */
  public UserMetadata getUserMetadata() {
    return this.userMetadata;
  }

  /**
   * Returns custom claims set on this user.
   *
   * @return a non-null, immutable Map of custom claims, possibly empty.
   */
  @NonNull
  public Map<String,Object> getCustomClaims() {
    return customClaims;
  }

  /**
   * Returns a new {@link UpdateRequest}, which can be used to update the attributes
   * of this user.
   *
   * @return a non-null UserRecord.UpdateRequest instance.
   */
  public UpdateRequest updateRequest() {
    return new UpdateRequest(uid);
  }

  static void checkUid(String uid) {
    checkArgument(!Strings.isNullOrEmpty(uid), "uid cannot be null or empty");
    checkArgument(uid.length() <= 128, "UID cannot be longer than 128 characters");
  }

  static void checkEmail(String email) {
    checkArgument(!Strings.isNullOrEmpty(email), "email cannot be null or empty");
    checkArgument(email.matches("^[^@]+@[^@]+$"));
  }

  static void checkPhoneNumber(String phoneNumber) {
    // Phone number verification is very lax here. Backend will enforce E.164 spec compliance, and
    // normalize accordingly.
    checkArgument(!Strings.isNullOrEmpty(phoneNumber), "phone number cannot be null or empty");
    checkArgument(phoneNumber.startsWith("+"),
        "phone number must be a valid, E.164 compliant identifier starting with a '+' sign");
  }

  static void checkProvider(String providerId, String providerUid) {
    checkArgument(!Strings.isNullOrEmpty(providerId), "providerId must be a non-empty string");
    checkArgument(!Strings.isNullOrEmpty(providerUid), "providerUid must be a non-empty string");
  }

  static void checkUrl(String photoUrl) {
    checkArgument(!Strings.isNullOrEmpty(photoUrl), "url cannot be null or empty");
    try {
      new URL(photoUrl);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("malformed url string", e);
    }
  }

  private static void checkPassword(String password) {
    checkArgument(!Strings.isNullOrEmpty(password), "password cannot be null or empty");
    checkArgument(password.length() >= 6, "password must be at least 6 characters long");
  }

  static void checkCustomClaims(Map<String,Object> customClaims) {
    if (customClaims == null) {
      return;
    }
    for (String key : customClaims.keySet()) {
      checkArgument(!Strings.isNullOrEmpty(key), "Claim names must not be null or empty");
      checkArgument(!FirebaseUserManager.RESERVED_CLAIMS.contains(key),
          "Claim '" + key + "' is reserved and cannot be set");
    }
  }

  private static void checkValidSince(long epochSeconds) {
    checkArgument(epochSeconds > 0, "validSince (seconds since epoch) must be greater than 0: "
        + Long.toString(epochSeconds));
  }

  static String serializeCustomClaims(Map customClaims, JsonFactory jsonFactory) {
    checkNotNull(jsonFactory, "JsonFactory must not be null");
    if (customClaims == null || customClaims.isEmpty()) {
      return "{}";
    }

    try {
      String claimsPayload = jsonFactory.toString(customClaims);
      checkArgument(claimsPayload.length() <= MAX_CLAIMS_PAYLOAD_SIZE,
          "customClaims payload cannot be larger than " + MAX_CLAIMS_PAYLOAD_SIZE + " characters");
      return claimsPayload;
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to serialize custom claims into JSON", e);
    }
  }

  /**
   * A specification class for creating new user accounts. Set the initial attributes of the new
   * user account by calling various setter methods available in this class. None of the attributes
   * are required.
   */
  public static class CreateRequest {

    private final Map<String,Object> properties = new HashMap<>();

    /**
     * Creates a new {@link CreateRequest}, which can be used to create a new user. The returned
     * object should be passed to {@link FirebaseAuth#createUser(CreateRequest)} to register
     * the user information persistently.
     */
    public CreateRequest() {
    }

    /**
     * Sets a user ID for the new user.
     *
     * @param uid a non-null, non-empty user ID that uniquely identifies the new user. The user ID
     *     must not be longer than 128 characters.
     */
    public CreateRequest setUid(String uid) {
      checkUid(uid);
      properties.put("localId", uid);
      return this;
    }

    /**
     * Sets an email address for the new user.
     *
     * @param email a non-null, non-empty email address string.
     */
    public CreateRequest setEmail(String email) {
      checkEmail(email);
      properties.put("email", email);
      return this;
    }

    /**
     * Sets a phone number for the new user.
     *
     * @param phone a non-null, non-empty phone number string.
     */
    public CreateRequest setPhoneNumber(String phone) {
      checkPhoneNumber(phone);
      properties.put("phoneNumber", phone);
      return this;
    }

    /**
     * Sets whether the user email address has been verified or not.
     *
     * @param emailVerified a boolean indicating the email verification status.
     */
    public CreateRequest setEmailVerified(boolean emailVerified) {
      properties.put("emailVerified", emailVerified);
      return this;
    }

    /**
     * Sets the display name for the new user.
     *
     * @param displayName a non-null display name string.
     */
    public CreateRequest setDisplayName(String displayName) {
      checkNotNull(displayName, "displayName cannot be null");
      properties.put("displayName", displayName);
      return this;
    }

    /**
     * Sets the photo URL for the new user.
     *
     * @param photoUrl a non-null, non-empty URL string.
     */
    public CreateRequest setPhotoUrl(String photoUrl) {
      checkUrl(photoUrl);
      properties.put("photoUrl", photoUrl);
      return this;
    }

    /**
     * Sets whether the new user account should be disabled by default or not.
     *
     * @param disabled a boolean indicating whether the new account should be disabled.
     */
    public CreateRequest setDisabled(boolean disabled) {
      properties.put("disabled", disabled);
      return this;
    }

    /**
     * Sets the password for the new user.
     *
     * @param password a password string that is at least 6 characters long.
     */
    public CreateRequest setPassword(String password) {
      checkPassword(password);
      properties.put("password", password);
      return this;
    }

    Map<String, Object> getProperties() {
      return ImmutableMap.copyOf(properties);
    }
  }

  /**
   * A class for updating the attributes of an existing user. An instance of this class can be
   * obtained via a {@link UserRecord} object, or from a user ID string. Specify the changes to be
   * made in the user account by calling the various setter methods available in this class.
   */
  public static class UpdateRequest {

    private final Map<String,Object> properties = new HashMap<>();

    /**
     * Creates a new {@link UpdateRequest}, which can be used to update the attributes
     * of the user identified by the specified user ID. This method allows updating attributes of
     * a user account, without first having to call {@link FirebaseAuth#getUser(String)}.
     *
     * @param uid a non-null, non-empty user ID string.
     * @throws IllegalArgumentException If the user ID is null or empty.
     */
    public UpdateRequest(String uid) {
      checkArgument(!Strings.isNullOrEmpty(uid), "uid must not be null or empty");
      properties.put("localId", uid);
    }

    String getUid() {
      return (String) properties.get("localId");
    }

    /**
     * Updates the email address associated with this user.
     *
     * @param email a non-null, non-empty email address to be associated with the user.
     */
    public UpdateRequest setEmail(String email) {
      checkEmail(email);
      properties.put("email", email);
      return this;
    }

    /**
     * Updates the phone number associated with this user. Calling this method with a null argument
     * removes the phone number from the user account.
     *
     * @param phone a valid phone number string or null.
     */
    public UpdateRequest setPhoneNumber(@Nullable String phone) {
      if (phone != null) {
        checkPhoneNumber(phone);
      }

      if (phone == null && properties.containsKey("deleteProvider")) {
        Object deleteProvider = properties.get("deleteProvider");
        if (deleteProvider != null) {
          // Due to java's type erasure, we can't fully check the type. :(
          @SuppressWarnings("unchecked")
          Iterable<String> deleteProviderIterable = (Iterable<String>)deleteProvider;

          // If we've been told to unlink the phone provider both via setting phoneNumber to null
          // *and* by setting providersToUnlink to include 'phone', then we'll reject that. Though
          // it might also be reasonable to relax this restriction and just unlink it.
          for (String dp : deleteProviderIterable) {
            if (dp == "phone") {
              throw new IllegalArgumentException(
                  "Both UpdateRequest.setPhoneNumber(null) and "
                  + "UpdateRequest.setProvidersToUnlink(['phone']) were set. To unlink from a "
                  + "phone provider, only specify UpdateRequest.setPhoneNumber(null).");

            }
          }
        }
      }

      properties.put("phoneNumber", phone);
      return this;
    }

    /**
     * Updates the email verification status of this account.
     *
     * @param emailVerified a boolean indicating whether the email address has been verified.
     */
    public UpdateRequest setEmailVerified(boolean emailVerified) {
      properties.put("emailVerified", emailVerified);
      return this;
    }

    /**
     * Updates the display name of this user. Calling this method with a null argument removes the
     * display name attribute from the user account.
     *
     * @param displayName a display name string or null
     */
    public UpdateRequest setDisplayName(@Nullable String displayName) {
      properties.put("displayName", displayName);
      return this;
    }

    /**
     * Updates the Photo URL of this user. Calling this method with a null argument removes
     * the photo URL attribute from the user account.
     *
     * @param photoUrl a valid URL string or null
     */
    public UpdateRequest setPhotoUrl(@Nullable String photoUrl) {
      // This is allowed to be null
      if (photoUrl != null) {
        checkUrl(photoUrl);
      }
      properties.put("photoUrl", photoUrl);
      return this;
    }

    /**
     * Enables or disables this user account.
     *
     * @param disabled a boolean indicating whether this account should be disabled.
     */
    public UpdateRequest setDisabled(boolean disabled) {
      properties.put("disableUser", disabled);
      return this;
    }

    /**
     * Updates the password of this user.
     *
     * @param password a new password string that is at least 6 characters long.
     */
    public UpdateRequest setPassword(String password) {
      checkPassword(password);
      properties.put("password", password);
      return this;
    }

    /**
     * Updates the custom claims associated with this user. Calling this method with a null
     * argument removes any custom claims from the user account.
     *
     * @param customClaims a Map of custom claims or null
     */
    public UpdateRequest setCustomClaims(Map<String,Object> customClaims) {
      checkCustomClaims(customClaims);
      properties.put(CUSTOM_ATTRIBUTES, customClaims);
      return this;
    }

    /**
     * Links this user to the specified provider.
     *
     * <p>Linking a provider to an existing user account does not invalidate the
     * refresh token of that account. In other words, the existing account
     * continues to be able to access resources, despite not having used
     * the newly linked provider to sign in. If you wish to force the user to
     * authenticate with this new provider, you need to (a) revoke their
     * refresh token (see
     * https://firebase.google.com/docs/auth/admin/manage-sessions#revoke_refresh_tokens),
     * and (b) ensure no other authentication methods are present on this
     * account.
     *
     * @param providerToLink provider info to be linked to this user\'s account.
     */
    public UpdateRequest setProviderToLink(@NonNull UserProvider providerToLink) {
      properties.put("linkProviderUserInfo", checkNotNull(providerToLink));
      return this;
    }

    /**
     * Unlinks this user from the specified providers.
     *
     * @param providerIds list of identifiers for the identity providers.
     */
    public UpdateRequest setProvidersToUnlink(Iterable<String> providerIds) {
      checkNotNull(providerIds);
      for (String id : providerIds) {
        checkArgument(!Strings.isNullOrEmpty(id), "providerIds must not be null or empty");

        if (id == "phone" && properties.containsKey("phoneNumber")
            && properties.get("phoneNumber") == null) {
          // If we've been told to unlink the phone provider both via setting phoneNumber to null
          // *and* by setting providersToUnlink to include 'phone', then we'll reject that. Though
          // it might also be reasonable to relax this restriction and just unlink it.
          throw new IllegalArgumentException(
              "Both UpdateRequest.setPhoneNumber(null) and "
              + "UpdateRequest.setProvidersToUnlink(['phone']) were set. To unlink from a phone "
              + "provider, only specify UpdateRequest.setPhoneNumber(null).");
        }
      }

      properties.put("deleteProvider", providerIds);
      return this;
    }

    UpdateRequest setValidSince(long epochSeconds) {
      checkValidSince(epochSeconds);
      properties.put("validSince", epochSeconds);
      return this;
    }

    Map<String, Object> getProperties(JsonFactory jsonFactory) {
      Map<String, Object> copy = new HashMap<>(properties);
      List<String> remove = new ArrayList<>();
      for (Map.Entry<String, String> entry : REMOVABLE_FIELDS.entrySet()) {
        if (copy.containsKey(entry.getKey()) && copy.get(entry.getKey()) == null) {
          remove.add(entry.getValue());
          copy.remove(entry.getKey());
        }
      }

      if (!remove.isEmpty()) {
        copy.put("deleteAttribute", ImmutableList.copyOf(remove));
      }

      if (copy.containsKey("phoneNumber") && copy.get("phoneNumber") == null) {
        Object deleteProvider = copy.get("deleteProvider");
        if (deleteProvider != null) {
          // Due to java's type erasure, we can't fully check the type. :(
          @SuppressWarnings("unchecked")
          Iterable<String> deleteProviderIterable = (Iterable<String>)deleteProvider;

          copy.put("deleteProvider", new ImmutableList.Builder<String>()
              .addAll(deleteProviderIterable)
              .add("phone")
              .build());
        } else {
          copy.put("deleteProvider", ImmutableList.of("phone"));
        }

        copy.remove("phoneNumber");
      }

      if (copy.containsKey(CUSTOM_ATTRIBUTES)) {
        Map customClaims = (Map) copy.remove(CUSTOM_ATTRIBUTES);
        copy.put(CUSTOM_ATTRIBUTES, serializeCustomClaims(customClaims, jsonFactory));
      }
      return ImmutableMap.copyOf(copy);
    }
  }

}
