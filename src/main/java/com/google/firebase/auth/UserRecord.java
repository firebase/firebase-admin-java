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
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.auth.internal.GetAccountInfoResponse;
import com.google.firebase.internal.Nullable;

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

  private final String uid;
  private final String email;
  private final String phoneNumber;
  private final boolean emailVerified;
  private final String displayName;
  private final String photoUrl;
  private final boolean disabled;
  private final ProviderUserInfo[] providers;
  private final UserMetadata userMetadata;

  UserRecord(GetAccountInfoResponse.User response) {
    checkNotNull(response, "Response must not be null");
    checkArgument(!Strings.isNullOrEmpty(response.getUid()), "uid must not be null or empty");
    this.uid = response.getUid();
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
    this.userMetadata = new UserMetadata(response.getCreatedAt(), response.getLastLoginAt());
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
   * Returns an array of UserInfo objects that represents the identities from different identity
   * providers that are linked to this user.
   *
   * @return an array of {@link UserInfo} instances, which may be empty.
   */
  public UserInfo[] getProviderData() {
    return providers;
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
   * Returns a new {@link UpdateRequest}, which can be used to update the attributes
   * of this user.
   *
   * @return a non-null UserRecord.UpdateRequest instance.
   */
  public UpdateRequest updateRequest() {
    return new UpdateRequest(uid);
  }

  private static void checkEmail(String email) {
    checkArgument(!Strings.isNullOrEmpty(email), "email cannot be null or empty");
    checkArgument(email.matches("^[^@]+@[^@]+$"));
  }

  private static void checkPhoneNumber(String phoneNumber) {
    // Phone number verification is very lax here. Backend will enforce E.164 spec compliance, and
    // normalize accordingly.
    checkArgument(!Strings.isNullOrEmpty(phoneNumber), "phone number cannot be null or empty");
    checkState(phoneNumber.startsWith("+"),
        "phone number must be a valid, E.164 compliant identifier starting with a '+' sign");
  }

  private static void checkPassword(String password) {
    checkArgument(!Strings.isNullOrEmpty(password), "password cannot be null or empty");
    checkArgument(password.length() >= 6, "password must be at least 6 characters long");
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
      checkArgument(!Strings.isNullOrEmpty(uid), "uid cannot be null or empty");
      checkArgument(uid.length() <= 128, "UID cannot be longer than 128 characters");
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
     * @param displayName a non-null, non-empty display name string.
     */
    public CreateRequest setDisplayName(String displayName) {
      checkNotNull(displayName, "displayName cannot be null or empty");
      properties.put("displayName", displayName);
      return this;
    }

    /**
     * Sets the photo URL for the new user.
     *
     * @param photoUrl a non-null, non-empty URL string.
     */
    public CreateRequest setPhotoUrl(String photoUrl) {
      checkArgument(!Strings.isNullOrEmpty(photoUrl), "photoUrl cannot be null or empty");
      try {
        new URL(photoUrl);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("malformed photoUrl string", e);
      }
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
      if (photoUrl != null) {
        try {
          new URL(photoUrl);
        } catch (MalformedURLException e) {
          throw new IllegalArgumentException("malformed photoUrl string", e);
        }
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

    Map<String, Object> getProperties() {
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
        copy.put("deleteProvider", ImmutableList.of("phone"));
        copy.remove("phoneNumber");
      }
      return ImmutableMap.copyOf(copy);
    }
  }

}
