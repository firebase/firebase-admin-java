/*
 * Copyright 2018 Google Inc.
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

package com.google.firebase.snippets;

import com.google.common.io.BaseEncoding;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.ErrorInfo;
import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.ImportUserRecord;
import com.google.firebase.auth.ListProviderConfigsPage;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.OidcProviderConfig;
import com.google.firebase.auth.SamlProviderConfig;
import com.google.firebase.auth.SessionCookieOptions;
import com.google.firebase.auth.UserImportHash;
import com.google.firebase.auth.UserImportOptions;
import com.google.firebase.auth.UserImportResult;
import com.google.firebase.auth.UserProvider;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.auth.hash.Bcrypt;
import com.google.firebase.auth.hash.HmacSha256;
import com.google.firebase.auth.hash.Pbkdf2Sha256;
import com.google.firebase.auth.hash.Scrypt;
import com.google.firebase.auth.hash.StandardScrypt;
import com.google.firebase.auth.multitenancy.ListTenantsPage;
import com.google.firebase.auth.multitenancy.Tenant;
import com.google.firebase.auth.multitenancy.TenantAwareFirebaseAuth;
import com.google.firebase.auth.multitenancy.TenantManager;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Auth snippets for documentation.
 */
public class FirebaseAuthSnippets {

  public static void getUserById(String uid) throws FirebaseAuthException {
    // [START get_user_by_id]
    UserRecord userRecord = FirebaseAuth.getInstance().getUser(uid);
    // See the UserRecord reference doc for the contents of userRecord.
    System.out.println("Successfully fetched user data: " + userRecord.getUid());
    // [END get_user_by_id]
  }

  public static void getUserByEmail(String email) throws FirebaseAuthException {
    // [START get_user_by_email]
    UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);
    // See the UserRecord reference doc for the contents of userRecord.
    System.out.println("Successfully fetched user data: " + userRecord.getEmail());
    // [END get_user_by_email]
  }

  public static void getUserByPhoneNumber(
      String phoneNumber) throws FirebaseAuthException {
    // [START get_user_by_phone]
    UserRecord userRecord = FirebaseAuth.getInstance().getUserByPhoneNumber(phoneNumber);
    // See the UserRecord reference doc for the contents of userRecord.
    System.out.println("Successfully fetched user data: " + userRecord.getPhoneNumber());
    // [END get_user_by_phone]
  }

  public static void createUser() throws FirebaseAuthException {
    // [START create_user]
    CreateRequest request = new CreateRequest()
        .setEmail("user@example.com")
        .setEmailVerified(false)
        .setPassword("secretPassword")
        .setPhoneNumber("+11234567890")
        .setDisplayName("John Doe")
        .setPhotoUrl("http://www.example.com/12345678/photo.png")
        .setDisabled(false);

    UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
    System.out.println("Successfully created new user: " + userRecord.getUid());
    // [END create_user]
  }

  public static void createUserWithUid() throws FirebaseAuthException {
    // [START create_user_with_uid]
    CreateRequest request = new CreateRequest()
        .setUid("some-uid")
        .setEmail("user@example.com")
        .setPhoneNumber("+11234567890");

    UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
    System.out.println("Successfully created new user: " + userRecord.getUid());
    // [END create_user_with_uid]
  }

  public static void updateUser(String uid) throws FirebaseAuthException {
    // [START update_user]
    UpdateRequest request = new UpdateRequest(uid)
        .setEmail("user@example.com")
        .setPhoneNumber("+11234567890")
        .setEmailVerified(true)
        .setPassword("newPassword")
        .setDisplayName("Jane Doe")
        .setPhotoUrl("http://www.example.com/12345678/photo.png")
        .setDisabled(true);

    UserRecord userRecord = FirebaseAuth.getInstance().updateUser(request);
    System.out.println("Successfully updated user: " + userRecord.getUid());
    // [END update_user]
  }

  public static void setCustomUserClaims(
      String uid) throws FirebaseAuthException {
    // [START set_custom_user_claims]
    // Set admin privilege on the user corresponding to uid.
    Map<String, Object> claims = new HashMap<>();
    claims.put("admin", true);
    FirebaseAuth.getInstance().setCustomUserClaims(uid, claims);
    // The new custom claims will propagate to the user's ID token the
    // next time a new one is issued.
    // [END set_custom_user_claims]

    String idToken = "id_token";
    // [START verify_custom_claims]
    // Verify the ID token first.
    FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(idToken);
    if (Boolean.TRUE.equals(decoded.getClaims().get("admin"))) {
      // Allow access to requested admin resource.
    }
    // [END verify_custom_claims]

    // [START read_custom_user_claims]
    // Lookup the user associated with the specified uid.
    UserRecord user = FirebaseAuth.getInstance().getUser(uid);
    System.out.println(user.getCustomClaims().get("admin"));
    // [END read_custom_user_claims]
  }

  public static void setCustomUserClaimsScript() throws FirebaseAuthException {
    // [START set_custom_user_claims_script]
    UserRecord user = FirebaseAuth.getInstance()
        .getUserByEmail("user@admin.example.com");
    // Confirm user is verified.
    if (user.isEmailVerified()) {
      Map<String, Object> claims = new HashMap<>();
      claims.put("admin", true);
      FirebaseAuth.getInstance().setCustomUserClaims(user.getUid(), claims);
    }
    // [END set_custom_user_claims_script]
  }

  public static void setCustomUserClaimsInc() throws FirebaseAuthException {
    // [START set_custom_user_claims_incremental]
    UserRecord user = FirebaseAuth.getInstance()
        .getUserByEmail("user@admin.example.com");
    // Add incremental custom claim without overwriting the existing claims.
    Map<String, Object> currentClaims = user.getCustomClaims();
    if (Boolean.TRUE.equals(currentClaims.get("admin"))) {
      // Add level.
      currentClaims.put("level", 10);
      // Add custom claims for additional privileges.
      FirebaseAuth.getInstance().setCustomUserClaims(user.getUid(), currentClaims);
    }
    // [END set_custom_user_claims_incremental]
  }

  public static void listAllUsers() throws FirebaseAuthException {
    // [START list_all_users]
    // Start listing users from the beginning, 1000 at a time.
    ListUsersPage page = FirebaseAuth.getInstance().listUsers(null);
    while (page != null) {
      for (ExportedUserRecord user : page.getValues()) {
        System.out.println("User: " + user.getUid());
      }
      page = page.getNextPage();
    }

    // Iterate through all users. This will still retrieve users in batches,
    // buffering no more than 1000 users in memory at a time.
    page = FirebaseAuth.getInstance().listUsers(null);
    for (ExportedUserRecord user : page.iterateAll()) {
      System.out.println("User: " + user.getUid());
    }
    // [END list_all_users]
  }

  public static void deleteUser(String uid) throws FirebaseAuthException {
    // [START delete_user]
    FirebaseAuth.getInstance().deleteUser(uid);
    System.out.println("Successfully deleted user.");
    // [END delete_user]
  }

  public static void createCustomToken() throws FirebaseAuthException {
    // [START custom_token]
    String uid = "some-uid";

    String customToken = FirebaseAuth.getInstance().createCustomToken(uid);
    // Send token back to client
    // [END custom_token]
    System.out.println("Created custom token: " + customToken);
  }

  public static void createCustomTokenWithClaims() throws FirebaseAuthException {
    // [START custom_token_with_claims]
    String uid = "some-uid";
    Map<String, Object> additionalClaims = new HashMap<String, Object>();
    additionalClaims.put("premiumAccount", true);

    String customToken = FirebaseAuth.getInstance()
        .createCustomToken(uid, additionalClaims);
    // Send token back to client
    // [END custom_token_with_claims]
    System.out.println("Created custom token: " + customToken);
  }

  public static void verifyIdToken(String idToken) throws FirebaseAuthException {
    // [START verify_id_token]
    // idToken comes from the client app (shown above)
    FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
    String uid = decodedToken.getUid();
    // [END verify_id_token]
    System.out.println("Decoded ID token from user: " + uid);
  }

  public static void verifyIdTokenCheckRevoked(String idToken) {
    // [START verify_id_token_check_revoked]
    try {
      // Verify the ID token while checking if the token is revoked by passing checkRevoked
      // as true.
      boolean checkRevoked = true;
      FirebaseToken decodedToken = FirebaseAuth.getInstance()
          .verifyIdToken(idToken, checkRevoked);
      // Token is valid and not revoked.
      String uid = decodedToken.getUid();
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() == AuthErrorCode.REVOKED_ID_TOKEN) {
        // Token has been revoked. Inform the user to re-authenticate or signOut() the user.
      } else {
        // Token is invalid.
      }
    }
    // [END verify_id_token_check_revoked]
  }

  public static void revokeIdTokens(
      String idToken) throws FirebaseAuthException {
    String uid = "someUid";
    // [START revoke_tokens]
    FirebaseAuth.getInstance().revokeRefreshTokens(uid);
    UserRecord user = FirebaseAuth.getInstance().getUser(uid);
    // Convert to seconds as the auth_time in the token claims is in seconds too.
    long revocationSecond = user.getTokensValidAfterTimestamp() / 1000;
    System.out.println("Tokens revoked at: " + revocationSecond);
    // [END revoke_tokens]

    // [START save_revocation_in_db]
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("metadata/" + uid);
    Map<String, Object> userData = new HashMap<>();
    userData.put("revokeTime", revocationSecond);
    ref.setValueAsync(userData);
    // [END save_revocation_in_db]
  }

  static class LoginRequest {
    String idToken;

    String getIdToken() {
      return idToken;
    }
  }

  // [START session_login]
  @POST
  @Path("/sessionLogin")
  @Consumes("application/json")
  public Response createSessionCookie(LoginRequest request) {
    // Get the ID token sent by the client
    String idToken = request.getIdToken();
    // Set session expiration to 5 days.
    long expiresIn = TimeUnit.DAYS.toMillis(5);
    SessionCookieOptions options = SessionCookieOptions.builder()
        .setExpiresIn(expiresIn)
        .build();
    try {
      // Create the session cookie. This will also verify the ID token in the process.
      // The session cookie will have the same claims as the ID token.
      String sessionCookie = FirebaseAuth.getInstance().createSessionCookie(idToken, options);
      // Set cookie policy parameters as required.
      NewCookie cookie = new NewCookie("session", sessionCookie /* ... other parameters */);
      return Response.ok().cookie(cookie).build();
    } catch (FirebaseAuthException e) {
      return Response.status(Status.UNAUTHORIZED).entity("Failed to create a session cookie")
          .build();
    }
  }
  // [END session_login]

  public Response checkAuthTime(String idToken) throws FirebaseAuthException {
    // [START check_auth_time]
    // To ensure that cookies are set only on recently signed in users, check auth_time in
    // ID token before creating a cookie.
    FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
    long authTimeMillis = TimeUnit.SECONDS.toMillis(
        (long) decodedToken.getClaims().get("auth_time"));

    // Only process if the user signed in within the last 5 minutes.
    if (System.currentTimeMillis() - authTimeMillis < TimeUnit.MINUTES.toMillis(5)) {
      long expiresIn = TimeUnit.DAYS.toMillis(5);
      SessionCookieOptions options = SessionCookieOptions.builder()
          .setExpiresIn(expiresIn)
          .build();
      String sessionCookie = FirebaseAuth.getInstance().createSessionCookie(idToken, options);
      // Set cookie policy parameters as required.
      NewCookie cookie = new NewCookie("session", sessionCookie);
      return Response.ok().cookie(cookie).build();
    }
    // User did not sign in recently. To guard against ID token theft, require
    // re-authentication.
    return Response.status(Status.UNAUTHORIZED).entity("Recent sign in required").build();
    // [END check_auth_time]
  }

  private Response serveContentForUser(FirebaseToken decodedToken) {
    return null;
  }

  private Response serveContentForAdmin(FirebaseToken decodedToken) {
    return null;
  }

  // [START session_verify]
  @POST
  @Path("/profile")
  public Response verifySessionCookie(@CookieParam("session") Cookie cookie) {
    String sessionCookie = cookie.getValue();
    try {
      // Verify the session cookie. In this case an additional check is added to detect
      // if the user's Firebase session was revoked, user deleted/disabled, etc.
      final boolean checkRevoked = true;
      FirebaseToken decodedToken = FirebaseAuth.getInstance().verifySessionCookie(
          sessionCookie, checkRevoked);
      return serveContentForUser(decodedToken);
    } catch (FirebaseAuthException e) {
      // Session cookie is unavailable, invalid or revoked. Force user to login.
      return Response.temporaryRedirect(URI.create("/login")).build();
    }
  }
  // [END session_verify]

  public Response checkPermissions(String sessionCookie) {
    // [START session_verify_with_permission_check]
    try {
      final boolean checkRevoked = true;
      FirebaseToken decodedToken = FirebaseAuth.getInstance().verifySessionCookie(
          sessionCookie, checkRevoked);
      if (Boolean.TRUE.equals(decodedToken.getClaims().get("admin"))) {
        return serveContentForAdmin(decodedToken);
      }
      return Response.status(Status.UNAUTHORIZED).entity("Insufficient permissions").build();
    } catch (FirebaseAuthException e) {
      // Session cookie is unavailable, invalid or revoked. Force user to login.
      return Response.temporaryRedirect(URI.create("/login")).build();
    }
    // [END session_verify_with_permission_check]
  }

  // [START session_clear]
  @POST
  @Path("/sessionLogout")
  public Response clearSessionCookie(@CookieParam("session") Cookie cookie) {
    final int maxAge = 0;
    NewCookie newCookie = new NewCookie(cookie, null, maxAge, true);
    return Response.temporaryRedirect(URI.create("/login")).cookie(newCookie).build();
  }
  // [END session_clear]

  // [START session_clear_and_revoke]
  @POST
  @Path("/sessionLogout")
  public Response clearSessionCookieAndRevoke(@CookieParam("session") Cookie cookie) {
    String sessionCookie = cookie.getValue();
    try {
      FirebaseToken decodedToken = FirebaseAuth.getInstance().verifySessionCookie(sessionCookie);
      FirebaseAuth.getInstance().revokeRefreshTokens(decodedToken.getUid());
      final int maxAge = 0;
      NewCookie newCookie = new NewCookie(cookie, null, maxAge, true);
      return Response.temporaryRedirect(URI.create("/login")).cookie(newCookie).build();
    } catch (FirebaseAuthException e) {
      return Response.temporaryRedirect(URI.create("/login")).build();
    }
  }
  // [END session_clear_and_revoke]

  public void importUsers() {
    // [START build_user_list]
    // Up to 1000 users can be imported at once.
    List<ImportUserRecord> users = new ArrayList<>();
    users.add(ImportUserRecord.builder()
        .setUid("uid1")
        .setEmail("user1@example.com")
        .setPasswordHash("passwordHash1".getBytes())
        .setPasswordSalt("salt1".getBytes())
        .build());
    users.add(ImportUserRecord.builder()
        .setUid("uid2")
        .setEmail("user2@example.com")
        .setPasswordHash("passwordHash2".getBytes())
        .setPasswordSalt("salt2".getBytes())
        .build());
    // [END build_user_list]

    // [START import_users]
    UserImportOptions options = UserImportOptions.withHash(
        HmacSha256.builder()
            .setKey("secretKey".getBytes())
            .build());
    try {
      UserImportResult result = FirebaseAuth.getInstance().importUsers(users, options);
      System.out.println("Successfully imported " + result.getSuccessCount() + " users");
      System.out.println("Failed to import " + result.getFailureCount() + " users");
      for (ErrorInfo indexedError : result.getErrors()) {
        System.out.println("Failed to import user at index: " + indexedError.getIndex()
            + " due to error: " + indexedError.getReason());
      }
    } catch (FirebaseAuthException e) {
      // Some unrecoverable error occurred that prevented the operation from running.
    }
    // [END import_users]
  }

  public void importWithHmac() {
    // [START import_with_hmac]
    try {
      List<ImportUserRecord> users = Collections.singletonList(ImportUserRecord.builder()
          .setUid("some-uid")
          .setEmail("user@example.com")
          .setPasswordHash("password-hash".getBytes())
          .setPasswordSalt("salt".getBytes())
          .build());
      UserImportOptions options = UserImportOptions.withHash(
          HmacSha256.builder()
              .setKey("secret".getBytes())
              .build());
      UserImportResult result = FirebaseAuth.getInstance().importUsers(users, options);
      for (ErrorInfo indexedError : result.getErrors()) {
        System.out.println("Failed to import user: " + indexedError.getReason());
      }
    } catch (FirebaseAuthException e) {
      System.out.println("Error importing users: " + e.getMessage());
    }
    // [END import_with_hmac]
  }

  public void importWithPbkdf() {
    // [START import_with_pbkdf]
    try {
      List<ImportUserRecord> users = Collections.singletonList(ImportUserRecord.builder()
          .setUid("some-uid")
          .setEmail("user@example.com")
          .setPasswordHash("password-hash".getBytes())
          .setPasswordSalt("salt".getBytes())
          .build());
      UserImportOptions options = UserImportOptions.withHash(
          Pbkdf2Sha256.builder()
              .setRounds(100000)
              .build());
      UserImportResult result = FirebaseAuth.getInstance().importUsers(users, options);
      for (ErrorInfo indexedError : result.getErrors()) {
        System.out.println("Failed to import user: " + indexedError.getReason());
      }
    } catch (FirebaseAuthException e) {
      System.out.println("Error importing users: " + e.getMessage());
    }
    // [END import_with_pbkdf]
  }

  public void importWithStandardScrypt() {
    // [START import_with_standard_scrypt]
    try {
      List<ImportUserRecord> users = Collections.singletonList(ImportUserRecord.builder()
          .setUid("some-uid")
          .setEmail("user@example.com")
          .setPasswordHash("password-hash".getBytes())
          .setPasswordSalt("salt".getBytes())
          .build());
      UserImportOptions options = UserImportOptions.withHash(
          StandardScrypt.builder()
              .setMemoryCost(1024)
              .setParallelization(16)
              .setBlockSize(8)
              .setDerivedKeyLength(64)
              .build());
      UserImportResult result = FirebaseAuth.getInstance().importUsers(users, options);
      for (ErrorInfo indexedError : result.getErrors()) {
        System.out.println("Failed to import user: " + indexedError.getReason());
      }
    } catch (FirebaseAuthException e) {
      System.out.println("Error importing users: " + e.getMessage());
    }
    // [END import_with_standard_scrypt]
  }

  public void importWithBcrypt() {
    // [START import_with_bcrypt]
    try {
      List<ImportUserRecord> users = Collections.singletonList(ImportUserRecord.builder()
          .setUid("some-uid")
          .setEmail("user@example.com")
          .setPasswordHash("password-hash".getBytes())
          .setPasswordSalt("salt".getBytes())
          .build());
      UserImportOptions options = UserImportOptions.withHash(Bcrypt.getInstance());
      UserImportResult result = FirebaseAuth.getInstance().importUsers(users, options);
      for (ErrorInfo indexedError : result.getErrors()) {
        System.out.println("Failed to import user: " + indexedError.getReason());
      }
    } catch (FirebaseAuthException e) {
      System.out.println("Error importing users: " + e.getMessage());
    }
    // [END import_with_bcrypt]
  }

  public void importWithScrypt() {
    // [START import_with_scrypt]
    try {
      List<ImportUserRecord> users = Collections.singletonList(ImportUserRecord.builder()
          .setUid("some-uid")
          .setEmail("user@example.com")
          .setPasswordHash("password-hash".getBytes())
          .setPasswordSalt("salt".getBytes())
          .build());
      UserImportOptions options = UserImportOptions.withHash(
          Scrypt.builder()
              // All the parameters below can be obtained from the Firebase Console's "Users"
              // section. Base64 encoded parameters must be decoded into raw bytes.
              .setKey(BaseEncoding.base64().decode("base64-secret"))
              .setSaltSeparator(BaseEncoding.base64().decode("base64-salt-separator"))
              .setRounds(8)
              .setMemoryCost(14)
              .build());
      UserImportResult result = FirebaseAuth.getInstance().importUsers(users, options);
      for (ErrorInfo indexedError : result.getErrors()) {
        System.out.println("Failed to import user: " + indexedError.getReason());
      }
    } catch (FirebaseAuthException e) {
      System.out.println("Error importing users: " + e.getMessage());
    }
    // [END import_with_scrypt]
  }

  public void importWithoutPassword() {
    // [START import_without_password]
    try {
      List<ImportUserRecord> users = Collections.singletonList(ImportUserRecord.builder()
          .setUid("some-uid")
          .setDisplayName("John Doe")
          .setEmail("johndoe@gmail.com")
          .setPhotoUrl("http://www.example.com/12345678/photo.png")
          .setEmailVerified(true)
          .setPhoneNumber("+11234567890")
          .putCustomClaim("admin", true) // set this user as admin
          .addUserProvider(UserProvider.builder() // user with Google provider
              .setUid("google-uid")
              .setEmail("johndoe@gmail.com")
              .setDisplayName("John Doe")
              .setPhotoUrl("http://www.example.com/12345678/photo.png")
              .setProviderId("google.com")
              .build())
          .build());
      UserImportResult result = FirebaseAuth.getInstance().importUsers(users);
      for (ErrorInfo indexedError : result.getErrors()) {
        System.out.println("Failed to import user: " + indexedError.getReason());
      }
    } catch (FirebaseAuthException e) {
      System.out.println("Error importing users: " + e.getMessage());
    }
    // [END import_without_password]
  }

  public ActionCodeSettings initActionCodeSettings() {
    // [START init_action_code_settings]
    ActionCodeSettings actionCodeSettings = ActionCodeSettings.builder()
        .setUrl("https://www.example.com/checkout?cartId=1234")
        .setHandleCodeInApp(true)
        .setIosBundleId("com.example.ios")
        .setAndroidPackageName("com.example.android")
        .setAndroidInstallApp(true)
        .setAndroidMinimumVersion("12")
        .setDynamicLinkDomain("coolapp.page.link")
        .build();
    // [END init_action_code_settings]
    return actionCodeSettings;
  }

  public void generatePasswordResetLink() {
    final ActionCodeSettings actionCodeSettings = initActionCodeSettings();
    final String displayName = "Example User";
    // [START password_reset_link]
    String email = "user@example.com";
    try {
      String link = FirebaseAuth.getInstance().generatePasswordResetLink(
          email, actionCodeSettings);
      // Construct email verification template, embed the link and send
      // using custom SMTP server.
      sendCustomEmail(email, displayName, link);
    } catch (FirebaseAuthException e) {
      System.out.println("Error generating email link: " + e.getMessage());
    }
    // [END password_reset_link]
  }

  public void generateEmailVerificationLink() {
    final ActionCodeSettings actionCodeSettings = initActionCodeSettings();
    final String displayName = "Example User";
    // [START email_verification_link]
    String email = "user@example.com";
    try {
      String link = FirebaseAuth.getInstance().generateEmailVerificationLink(
          email, actionCodeSettings);
      // Construct email verification template, embed the link and send
      // using custom SMTP server.
      sendCustomEmail(email, displayName, link);
    } catch (FirebaseAuthException e) {
      System.out.println("Error generating email link: " + e.getMessage());
    }
    // [END email_verification_link]
  }

  public void generateSignInWithEmailLink() {
    final ActionCodeSettings actionCodeSettings = initActionCodeSettings();
    final String displayName = "Example User";
    // [START sign_in_with_email_link]
    String email = "user@example.com";
    try {
      String link = FirebaseAuth.getInstance().generateSignInWithEmailLink(
          email, actionCodeSettings);
      // Construct email verification template, embed the link and send
      // using custom SMTP server.
      sendCustomEmail(email, displayName, link);
    } catch (FirebaseAuthException e) {
      System.out.println("Error generating email link: " + e.getMessage());
    }
    // [END sign_in_with_email_link]
  }

  // =====================================================================================
  // https://cloud.google.com/identity-platform/docs/managing-providers-programmatically
  // =====================================================================================

  public void createSamlProviderConfig() throws FirebaseAuthException {
    // [START create_saml_provider]
    SamlProviderConfig.CreateRequest request = new SamlProviderConfig.CreateRequest()
        .setDisplayName("SAML provider name")
        .setEnabled(true)
        .setProviderId("saml.myProvider")
        .setIdpEntityId("IDP_ENTITY_ID")
        .setSsoUrl("https://example.com/saml/sso/1234/")
        .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT1...\n-----END CERTIFICATE-----")
        .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT2...\n-----END CERTIFICATE-----")
        .setRpEntityId("RP_ENTITY_ID")
        .setCallbackUrl("https://project-id.firebaseapp.com/__/auth/handler");
    SamlProviderConfig saml = FirebaseAuth.getInstance().createSamlProviderConfig(request);
    System.out.println("Created new SAML provider: " + saml.getProviderId());
    // [END create_saml_provider]
  }

  public void updateSamlProviderConfig() throws FirebaseAuthException {
    // [START update_saml_provider]
    SamlProviderConfig.UpdateRequest request =
        new SamlProviderConfig.UpdateRequest("saml.myProvider")
          .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT2...\n-----END CERTIFICATE-----")
          .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT3...\n-----END CERTIFICATE-----");
    SamlProviderConfig saml = FirebaseAuth.getInstance().updateSamlProviderConfig(request);
    System.out.println("Updated SAML provider: " + saml.getProviderId());
    // [END update_saml_provider]
  }

  public void getSamlProviderConfig() throws FirebaseAuthException {
    // [START get_saml_provider]
    SamlProviderConfig saml = FirebaseAuth.getInstance().getSamlProviderConfig("saml.myProvider");
    System.out.println(saml.getDisplayName() + ": " + saml.isEnabled());
    // [END get_saml_provider]
  }

  public void deleteSamlProviderConfig() throws FirebaseAuthException {
    // [START delete_saml_provider]
    FirebaseAuth.getInstance().deleteSamlProviderConfig("saml.myProvider");
    // [END delete_saml_provider]
  }

  public void listSamlProviderConfigs() throws FirebaseAuthException {
    // [START list_saml_providers]
    ListProviderConfigsPage<SamlProviderConfig> page = FirebaseAuth.getInstance()
        .listSamlProviderConfigs("nextPageToken");
    for (SamlProviderConfig config : page.iterateAll()) {
      System.out.println(config.getProviderId());
    }
    // [END list_saml_providers]
  }

  public void createOidcProviderConfig() throws FirebaseAuthException {
    // [START create_oidc_provider]
    OidcProviderConfig.CreateRequest request = new OidcProviderConfig.CreateRequest()
        .setDisplayName("OIDC provider name")
        .setEnabled(true)
        .setProviderId("oidc.myProvider")
        .setClientId("CLIENT_ID2")
        .setIssuer("https://oidc.com/CLIENT_ID2");
    OidcProviderConfig oidc = FirebaseAuth.getInstance().createOidcProviderConfig(request);
    System.out.println("Created new OIDC provider: " + oidc.getProviderId());
    // [END create_oidc_provider]
  }

  public void updateOidcProviderConfig() throws FirebaseAuthException {
    // [START update_oidc_provider]
    OidcProviderConfig.UpdateRequest request =
        new OidcProviderConfig.UpdateRequest("oidc.myProvider")
            .setDisplayName("OIDC provider name")
            .setEnabled(true)
            .setClientId("CLIENT_ID")
            .setIssuer("https://oidc.com");
    OidcProviderConfig oidc = FirebaseAuth.getInstance().updateOidcProviderConfig(request);
    System.out.println("Updated OIDC provider: " + oidc.getProviderId());
    // [END update_oidc_provider]
  }

  public void getOidcProviderConfig() throws FirebaseAuthException {
    // [START get_oidc_provider]
    OidcProviderConfig oidc = FirebaseAuth.getInstance().getOidcProviderConfig("oidc.myProvider");
    System.out.println(oidc.getDisplayName() + ": " + oidc.isEnabled());
    // [END get_oidc_provider]
  }

  public void deleteOidcProviderConfig() throws FirebaseAuthException {
    // [START delete_oidc_provider]
    FirebaseAuth.getInstance().deleteOidcProviderConfig("oidc.myProvider");
    // [END delete_oidc_provider]
  }

  public void listOidcProviderConfigs() throws FirebaseAuthException {
    // [START list_oidc_providers]
    ListProviderConfigsPage<OidcProviderConfig> page = FirebaseAuth.getInstance()
        .listOidcProviderConfigs("nextPageToken");
    for (OidcProviderConfig oidc : page.iterateAll()) {
      System.out.println(oidc.getProviderId());
    }
    // [END list_oidc_providers]
  }

  // ================================================================================
  // https://cloud.google.com/identity-platform/docs/multi-tenancy-managing-tenants
  // =================================================================================

  public TenantAwareFirebaseAuth getTenantAwareFirebaseAuth(String tenantId) {
    // [START get_tenant_client]
    FirebaseAuth auth = FirebaseAuth.getInstance();
    TenantManager tenantManager = auth.getTenantManager();
    TenantAwareFirebaseAuth tenantAuth = tenantManager.getAuthForTenant(tenantId);
    // [END get_tenant_client]

    return tenantAuth;
  }

  public void getTenant(String tenantId) throws FirebaseAuthException {
    // [START get_tenant]
    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().getTenant(tenantId);
    System.out.println("Retrieved tenant: " + tenant.getTenantId());
    // [END get_tenant]
  }

  public void createTenant() throws FirebaseAuthException {
    // [START create_tenant]
    Tenant.CreateRequest request = new Tenant.CreateRequest()
        .setDisplayName("myTenant1")
        .setEmailLinkSignInEnabled(true)
        .setPasswordSignInAllowed(true);
    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().createTenant(request);
    System.out.println("Created tenant: " + tenant.getTenantId());
    // [END create_tenant]
  }

  public void updateTenant(String tenantId) throws FirebaseAuthException {
    // [START update_tenant]
    Tenant.UpdateRequest request = new Tenant.UpdateRequest(tenantId)
        .setDisplayName("updatedName")
        .setPasswordSignInAllowed(false);
    Tenant tenant = FirebaseAuth.getInstance().getTenantManager().updateTenant(request);
    System.out.println("Updated tenant: " + tenant.getTenantId());
    // [END update_tenant]
  }

  public void deleteTenant(String tenantId) throws FirebaseAuthException {
    // [START delete_tenant]
    FirebaseAuth.getInstance().getTenantManager().deleteTenant(tenantId);
    // [END delete_tenant]
  }

  public void listTenants() throws FirebaseAuthException {
    // [START list_tenants]
    ListTenantsPage page = FirebaseAuth.getInstance().getTenantManager().listTenants(null);
    for (Tenant tenant : page.iterateAll()) {
      System.out.println("Retrieved tenant: " + tenant.getTenantId());
    }
    // [END list_tenants]
  }

  public void createProviderTenant() throws FirebaseAuthException {
    // [START get_tenant_client_short]
    TenantAwareFirebaseAuth tenantAuth = FirebaseAuth.getInstance().getTenantManager()
        .getAuthForTenant("TENANT-ID");
    // [END get_tenant_client_short]

    // [START create_saml_provider_tenant]
    SamlProviderConfig.CreateRequest request = new SamlProviderConfig.CreateRequest()
        .setDisplayName("SAML provider name")
        .setEnabled(true)
        .setProviderId("saml.myProvider")
        .setIdpEntityId("IDP_ENTITY_ID")
        .setSsoUrl("https://example.com/saml/sso/1234/")
        .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT1...\n-----END CERTIFICATE-----")
        .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT2...\n-----END CERTIFICATE-----")
        .setRpEntityId("RP_ENTITY_ID")
        .setCallbackUrl("https://project-id.firebaseapp.com/__/auth/handler");
    SamlProviderConfig saml = tenantAuth.createSamlProviderConfig(request);
    System.out.println("Created new SAML provider: " + saml.getProviderId());
    // [END create_saml_provider_tenant]
  }

  public void updateProviderTenant(
      TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START update_saml_provider_tenant]
    SamlProviderConfig.UpdateRequest request =
        new SamlProviderConfig.UpdateRequest("saml.myProvider")
          .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT2...\n-----END CERTIFICATE-----")
          .addX509Certificate("-----BEGIN CERTIFICATE-----\nCERT3...\n-----END CERTIFICATE-----");
    SamlProviderConfig saml = tenantAuth.updateSamlProviderConfig(request);
    System.out.println("Updated SAML provider: " + saml.getProviderId());
    // [END update_saml_provider_tenant]
  }

  public void getProviderTenant(TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START get_saml_provider_tenant]
    SamlProviderConfig saml = tenantAuth.getSamlProviderConfig("saml.myProvider");

    // Get display name and whether it is enabled.
    System.out.println(saml.getDisplayName() + " " + saml.isEnabled());
    // [END get_saml_provider_tenant]
  }

  public void listProvidersTenant(TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START list_saml_providers_tenant]
    ListProviderConfigsPage<SamlProviderConfig> page = tenantAuth.listSamlProviderConfigs(
        "nextPageToken");
    for (SamlProviderConfig saml : page.iterateAll()) {
      System.out.println(saml.getProviderId());
    }
    // [END list_saml_providers_tenant]
  }

  public void deleteProviderTenant(
      TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START delete_saml_provider_tenant]
    tenantAuth.deleteSamlProviderConfig("saml.myProvider");
    // [END delete_saml_provider_tenant]
  }

  public void getUserTenant(
      TenantAwareFirebaseAuth tenantAuth, String uid) throws FirebaseAuthException {
    // [START get_user_tenant]
    // Get an auth client from the firebase.App
    UserRecord user = tenantAuth.getUser(uid);
    System.out.println("Successfully fetched user data: " + user.getDisplayName());
    // [END get_user_tenant]
  }

  public void getUserByEmailTenant(
      TenantAwareFirebaseAuth tenantAuth, String email) throws FirebaseAuthException {
    // [START get_user_by_email_tenant]
    // Get an auth client from the firebase.App
    UserRecord user = tenantAuth.getUserByEmail(email);
    System.out.println("Successfully fetched user data: " + user.getDisplayName());
    // [END get_user_by_email_tenant]
  }

  public void createUserTenant(TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START create_user_tenant]
    UserRecord.CreateRequest request = new UserRecord.CreateRequest()
        .setEmail("user@example.com")
        .setEmailVerified(false)
        .setPhoneNumber("+15555550100")
        .setPassword("secretPassword")
        .setDisplayName("John Doe")
        .setPhotoUrl("http://www.example.com/12345678/photo.png")
        .setDisabled(false);
    UserRecord user = tenantAuth.createUser(request);
    System.out.println("Successfully created user: " + user.getDisplayName());
    // [END create_user_tenant]
  }

  public void updateUserTenant(
      TenantAwareFirebaseAuth tenantAuth, String uid) throws FirebaseAuthException {
    // [START update_user_tenant]
    UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(uid)
        .setEmail("user@example.com")
        .setEmailVerified(true)
        .setPhoneNumber("+15555550100")
        .setPassword("newPassword")
        .setDisplayName("John Doe")
        .setPhotoUrl("http://www.example.com/12345678/photo.png")
        .setDisabled(true);
    UserRecord user = tenantAuth.updateUser(request);
    System.out.println("Successfully updated user: " + user.getDisplayName());
    // [END update_user_tenant]
  }

  public void deleteUserTenant(
      TenantAwareFirebaseAuth tenantAuth, String uid) throws FirebaseAuthException {
    // [START delete_user_tenant]
    tenantAuth.deleteUser(uid);

    System.out.println("Successfully deleted user: " + uid);
    // [END delete_user_tenant]
  }

  public void listUsersTenant(TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START list_all_users_tenant]
    // Note, behind the scenes, the ListUsersPage retrieves 1000 Users at a time
    // through the API
    ListUsersPage  page = tenantAuth.listUsers(null);
    for (ExportedUserRecord user : page.iterateAll()) {
      System.out.println("User: " + user.getUid());
    }

    // Iterating by pages 100 users at a time.
    page = tenantAuth.listUsers(null, 100);
    while (page != null) {
      for (ExportedUserRecord user : page.getValues()) {
        System.out.println("User: " + user.getUid());
      }

      page = page.getNextPage();
    }
    // [END list_all_users_tenant]
  }

  public void importWithHmacTenant(
      TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START import_with_hmac_tenant]
    List<ImportUserRecord> users = new ArrayList<>();
    users.add(ImportUserRecord.builder()
        .setUid("uid1")
        .setEmail("user1@example.com")
        .setPasswordHash("password-hash-1".getBytes())
        .setPasswordSalt("salt1".getBytes())
        .build());
    users.add(ImportUserRecord.builder()
        .setUid("uid2")
        .setEmail("user2@example.com")
        .setPasswordHash("password-hash-2".getBytes())
        .setPasswordSalt("salt2".getBytes())
        .build());
    UserImportHash hmacSha256 = HmacSha256.builder()
        .setKey("secret".getBytes())
        .build();
    UserImportResult result = tenantAuth.importUsers(users, UserImportOptions.withHash(hmacSha256));

    for (ErrorInfo error : result.getErrors()) {
      System.out.println("Failed to import user: " + error.getReason());
    }
    // [END import_with_hmac_tenant]
  }

  public void importWithoutPasswordTenant(
      TenantAwareFirebaseAuth tenantAuth) throws FirebaseAuthException {
    // [START import_without_password_tenant]
    List<ImportUserRecord> users = new ArrayList<>();
    users.add(ImportUserRecord.builder()
        .setUid("some-uid")
        .setDisplayName("John Doe")
        .setEmail("johndoe@acme.com")
        .setPhotoUrl("https://www.example.com/12345678/photo.png")
        .setEmailVerified(true)
        .setPhoneNumber("+11234567890")
        // Set this user as admin.
        .putCustomClaim("admin", true)
        // User with SAML provider.
        .addUserProvider(UserProvider.builder()
            .setUid("saml-uid")
            .setEmail("johndoe@acme.com")
            .setDisplayName("John Doe")
            .setPhotoUrl("https://www.example.com/12345678/photo.png")
            .setProviderId("saml.acme")
            .build())
        .build());

    UserImportResult result = tenantAuth.importUsers(users);

    for (ErrorInfo error : result.getErrors()) {
      System.out.println("Failed to import user: " + error.getReason());
    }
    // [END import_without_password_tenant]
  }

  public void verifyIdTokenTenant(TenantAwareFirebaseAuth tenantAuth, String idToken) {
    // [START verify_id_token_tenant]
    try {
      // idToken comes from the client app
      FirebaseToken token = tenantAuth.verifyIdToken(idToken);
      // TenantId on the FirebaseToken should be set to TENANT-ID.
      // Otherwise "tenant-id-mismatch" error thrown.
      System.out.println("Verified ID token from tenant: " + token.getTenantId());
    } catch (FirebaseAuthException e) {
      System.out.println("error verifying ID token: " + e.getMessage());
    }
    // [END verify_id_token_tenant]
  }

  public void verifyIdTokenAccessControlTenant(TenantAwareFirebaseAuth tenantAuth, String idToken) {
    // [START id_token_access_control_tenant]
    try {
      // idToken comes from the client app
      FirebaseToken token = tenantAuth.verifyIdToken(idToken);
      if ("TENANT-ID1".equals(token.getTenantId())) {
        // Allow appropriate level of access for TENANT-ID1.
      } else if ("TENANT-ID2".equals(token.getTenantId())) {
        // Allow appropriate level of access for TENANT-ID2.
      } else {
        // Access not allowed -- Handle error
      }
    } catch (FirebaseAuthException e) {
      System.out.println("error verifying ID token: " + e.getMessage());
    }
    // [END id_token_access_control_tenant]
  }

  public void revokeRefreshTokensTenant(
      TenantAwareFirebaseAuth tenantAuth, String uid) throws FirebaseAuthException {
    // [START revoke_tokens_tenant]
    // Revoke all refresh tokens for a specified user in a specified tenant for whatever reason.
    // Retrieve the timestamp of the revocation, in seconds since the epoch.
    tenantAuth.revokeRefreshTokens(uid);

    // accessing the user's TokenValidAfter
    UserRecord user = tenantAuth.getUser(uid);


    long timestamp = user.getTokensValidAfterTimestamp() / 1000;
    System.out.println("the refresh tokens were revoked at: " + timestamp + " (UTC seconds)");
    // [END revoke_tokens_tenant]
  }

  public void verifyIdTokenAndCheckRevokedTenant(
      TenantAwareFirebaseAuth tenantAuth, String idToken) {
    // [START verify_id_token_and_check_revoked_tenant]
    // Verify the ID token for a specific tenant while checking if the token is revoked.
    boolean checkRevoked = true;
    try {
      FirebaseToken token = tenantAuth.verifyIdToken(idToken, checkRevoked);
      System.out.println("Verified ID token for: " + token.getUid());
    } catch (FirebaseAuthException e) {
      if ("id-token-revoked".equals(e.getErrorCode())) {
        // Token is revoked. Inform the user to re-authenticate or signOut() the user.
      } else {
        // Token is invalid
      }
    }
    // [END verify_id_token_and_check_revoked_tenant]
  }

  public void customClaimsVerifyTenant(
      TenantAwareFirebaseAuth tenantAuth, String idToken) throws FirebaseAuthException {
    // [START verify_custom_claims_tenant]
    // Verify the ID token first.
    FirebaseToken token = tenantAuth.verifyIdToken(idToken);
    if (Boolean.TRUE.equals(token.getClaims().get("admin"))) {
      //Allow access to requested admin resource.
    }
    // [END verify_custom_claims_tenant]
  }

  public void generateEmailVerificationLinkTenant(
      TenantAwareFirebaseAuth tenantAuth,
      String email,
      String displayName) throws FirebaseAuthException {
    // [START email_verification_link_tenant]
    ActionCodeSettings actionCodeSettings = ActionCodeSettings.builder()
        // URL you want to redirect back to. The domain (www.example.com) for
        // this URL must be whitelisted in the GCP Console.
        .setUrl("https://www.example.com/checkout?cartId=1234")
        // This must be true for email link sign-in.
        .setHandleCodeInApp(true)
        .setIosBundleId("com.example.ios")
        .setAndroidPackageName("com.example.android")
        .setAndroidInstallApp(true)
        .setAndroidMinimumVersion("12")
        // FDL custom domain.
        .setDynamicLinkDomain("coolapp.page.link")
        .build();

    String link = tenantAuth.generateEmailVerificationLink(email, actionCodeSettings);

    // Construct email verification template, embed the link and send
    // using custom SMTP server.
    sendCustomEmail(email, displayName, link);
    // [END email_verification_link_tenant]
  }

  // Place holder method to make the compiler happy. This is referenced by all email action
  // link snippets.
  private void sendCustomEmail(String email, String displayName, String link) {}
}
