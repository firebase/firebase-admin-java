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

import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.SessionCookieOptions;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.UserRecord.CreateRequest;
import com.google.firebase.auth.UserRecord.UpdateRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
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

  public static void getUserById(String uid) throws InterruptedException, ExecutionException {
    // [START get_user_by_id]
    UserRecord userRecord = FirebaseAuth.getInstance().getUserAsync(uid).get();
    // See the UserRecord reference doc for the contents of userRecord.
    System.out.println("Successfully fetched user data: " + userRecord.getUid());
    // [END get_user_by_id]
  }

  public static void getUserByEmail(String email) throws InterruptedException, ExecutionException {
    // [START get_user_by_email]
    UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmailAsync(email).get();
    // See the UserRecord reference doc for the contents of userRecord.
    System.out.println("Successfully fetched user data: " + userRecord.getEmail());
    // [END get_user_by_email]
  }

  public static void getUserByPhoneNumber(
      String phoneNumber) throws InterruptedException, ExecutionException {
    // [START get_user_by_phone]
    UserRecord userRecord = FirebaseAuth.getInstance().getUserByPhoneNumberAsync(phoneNumber).get();
    // See the UserRecord reference doc for the contents of userRecord.
    System.out.println("Successfully fetched user data: " + userRecord.getPhoneNumber());
    // [END get_user_by_phone]
  }

  public static void createUser() throws InterruptedException, ExecutionException {
    // [START create_user]
    CreateRequest request = new CreateRequest()
        .setEmail("user@example.com")
        .setEmailVerified(false)
        .setPassword("secretPassword")
        .setPhoneNumber("+11234567890")
        .setDisplayName("John Doe")
        .setPhotoUrl("http://www.example.com/12345678/photo.png")
        .setDisabled(false);

    UserRecord userRecord = FirebaseAuth.getInstance().createUserAsync(request).get();
    System.out.println("Successfully created new user: " + userRecord.getUid());
    // [END create_user]
  }

  public static void createUserWithUid() throws InterruptedException, ExecutionException {
    // [START create_user_with_uid]
    CreateRequest request = new CreateRequest()
        .setUid("some-uid")
        .setEmail("user@example.com")
        .setPhoneNumber("+11234567890");

    UserRecord userRecord = FirebaseAuth.getInstance().createUserAsync(request).get();
    System.out.println("Successfully created new user: " + userRecord.getUid());
    // [END create_user_with_uid]
  }

  public static void updateUser(String uid) throws InterruptedException, ExecutionException {
    // [START update_user]
    UpdateRequest request = new UpdateRequest(uid)
        .setEmail("user@example.com")
        .setPhoneNumber("+11234567890")
        .setEmailVerified(true)
        .setPassword("newPassword")
        .setDisplayName("Jane Doe")
        .setPhotoUrl("http://www.example.com/12345678/photo.png")
        .setDisabled(true);

    UserRecord userRecord = FirebaseAuth.getInstance().updateUserAsync(request).get();
    System.out.println("Successfully updated user: " + userRecord.getUid());
    // [END update_user]
  }

  public static void setCustomUserClaims(
      String uid) throws InterruptedException, ExecutionException {
    // [START set_custom_user_claims]
    // Set admin privilege on the user corresponding to uid.
    Map<String, Object> claims = new HashMap<>();
    claims.put("admin", true);
    FirebaseAuth.getInstance().setCustomUserClaimsAsync(uid, claims).get();
    // The new custom claims will propagate to the user's ID token the
    // next time a new one is issued.
    // [END set_custom_user_claims]

    String idToken = "id_token";
    // [START verify_custom_claims]
    // Verify the ID token first.
    FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdTokenAsync(idToken).get();
    if (Boolean.TRUE.equals(decoded.getClaims().get("admin"))) {
      // Allow access to requested admin resource.
    }
    // [END verify_custom_claims]

    // [START read_custom_user_claims]
    // Lookup the user associated with the specified uid.
    UserRecord user = FirebaseAuth.getInstance().getUserAsync(uid).get();
    System.out.println(user.getCustomClaims().get("admin"));
    // [END read_custom_user_claims]
  }

  public static void setCustomUserClaimsScript() throws InterruptedException, ExecutionException {
    // [START set_custom_user_claims_script]
    UserRecord user = FirebaseAuth.getInstance()
        .getUserByEmailAsync("user@admin.example.com").get();
    // Confirm user is verified.
    if (user.isEmailVerified()) {
      Map<String, Object> claims = new HashMap<>();
      claims.put("admin", true);
      FirebaseAuth.getInstance().setCustomUserClaimsAsync(user.getUid(), claims).get();
    }
    // [END set_custom_user_claims_script]
  }

  public static void setCustomUserClaimsInc() throws InterruptedException, ExecutionException {
    // [START set_custom_user_claims_incremental]
    UserRecord user = FirebaseAuth.getInstance()
        .getUserByEmailAsync("user@admin.example.com").get();
    // Add incremental custom claim without overwriting the existing claims.
    Map<String, Object> currentClaims = user.getCustomClaims();
    if (Boolean.TRUE.equals(currentClaims.get("admin"))) {
      // Add level.
      currentClaims.put("level", 10);
      // Add custom claims for additional privileges.
      FirebaseAuth.getInstance().setCustomUserClaimsAsync(user.getUid(), currentClaims).get();
    }
    // [END set_custom_user_claims_incremental]
  }

  public static void listAllUsers() throws InterruptedException, ExecutionException  {
    // [START list_all_users]
    // Start listing users from the beginning, 1000 at a time.
    ListUsersPage page = FirebaseAuth.getInstance().listUsersAsync(null).get();
    while (page != null) {
      for (ExportedUserRecord user : page.getValues()) {
        System.out.println("User: " + user.getUid());
      }
      page = page.getNextPage();
    }

    // Iterate through all users. This will still retrieve users in batches,
    // buffering no more than 1000 users in memory at a time.
    page = FirebaseAuth.getInstance().listUsersAsync(null).get();
    for (ExportedUserRecord user : page.iterateAll()) {
      System.out.println("User: " + user.getUid());
    }
    // [END list_all_users]
  }

  public static void deleteUser(String uid) throws InterruptedException, ExecutionException {
    // [START delete_user]
    FirebaseAuth.getInstance().deleteUserAsync(uid).get();
    System.out.println("Successfully deleted user.");
    // [END delete_user]
  }

  public static void createCustomToken() throws InterruptedException, ExecutionException {
    // [START custom_token]
    String uid = "some-uid";

    String customToken = FirebaseAuth.getInstance().createCustomTokenAsync(uid).get();
    // Send token back to client
    // [END custom_token]
    System.out.println("Created custom token: " + customToken);
  }

  public static void createCustomTokenWithClaims() throws InterruptedException, ExecutionException {
    // [START custom_token_with_claims]
    String uid = "some-uid";
    Map<String, Object> additionalClaims = new HashMap<String, Object>();
    additionalClaims.put("premiumAccount", true);

    String customToken = FirebaseAuth.getInstance()
        .createCustomTokenAsync(uid, additionalClaims).get();
    // Send token back to client
    // [END custom_token_with_claims]
    System.out.println("Created custom token: " + customToken);
  }

  public static void verifyIdToken(
      String idToken) throws InterruptedException, ExecutionException {
    // [START verify_id_token]
    // idToken comes from the client app (shown above)
    FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(idToken).get();
    String uid = decodedToken.getUid();
    // [END verify_id_token]
    System.out.println("Decoded ID token from user: " + uid);
  }

  public static void verifyIdTokenCheckRevoked(String idToken) throws InterruptedException {
    // [START verify_id_token_check_revoked]
    try {
      // Verify the ID token while checking if the token is revoked by passing checkRevoked
      // as true.
      boolean checkRevoked = true;
      FirebaseToken decodedToken = FirebaseAuth.getInstance()
          .verifyIdTokenAsync(idToken, checkRevoked).get();
      // Token is valid and not revoked.
      String uid = decodedToken.getUid();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof FirebaseAuthException) {
        FirebaseAuthException authError = (FirebaseAuthException) e.getCause();
        if (authError.getErrorCode().equals("id-token-revoked")) {
          // Token has been revoked. Inform the user to reauthenticate or signOut() the user.
        } else {
          // Token is invalid.
        }
      }
    }
    // [END verify_id_token_check_revoked]
  }

  public static void revokeIdTokens(
      String idToken) throws InterruptedException, ExecutionException {
    String uid = "someUid";
    // [START revoke_tokens]
    FirebaseAuth.getInstance().revokeRefreshTokensAsync(uid).get();
    UserRecord user = FirebaseAuth.getInstance().getUserAsync(uid).get();
    // Convert to seconds as the auth_time in the token claims is in seconds too.
    long revocationSecond = user.getTokensValidAfterTimestamp() / 1000;
    System.out.println("Tokens revoked at: " + revocationSecond);
    // [END revoke_tokens]

    // [START save_revocation_in_db]
    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("metadata/" + uid);
    Map<String, Object> userData = new HashMap<>();
    userData.put("revokeTime", revocationSecond);
    ref.setValueAsync(userData).get();
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
      String sessionCookie = FirebaseAuth.getInstance().createSessionCookieAsync(
          idToken, options).get();
      // Set cookie policy parameters as required.
      NewCookie cookie = new NewCookie("session", sessionCookie /* ... other parameters */);
      return Response.ok().cookie(cookie).build();
    } catch (Exception e) {
      return Response.status(Status.UNAUTHORIZED).entity("Failed to create a session cookie")
          .build();
    }
  }
  // [END session_login]

  public Response checkAuthTime(String idToken) throws Exception {
    // [START check_auth_time]
    // To ensure that cookies are set only on recently signed in users, check auth_time in
    // ID token before creating a cookie.
    FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(idToken).get();
    long authTimeMillis = (long) decodedToken.getClaims().get("auth_time") * 1000L;

    // Only process if the user signed in within the last 5 minutes.
    if (System.currentTimeMillis() - authTimeMillis < TimeUnit.MINUTES.toMillis(5)) {
      long expiresIn = TimeUnit.DAYS.toMillis(5);
      SessionCookieOptions options = SessionCookieOptions.builder()
          .setExpiresIn(expiresIn)
          .build();
      String sessionCookie = FirebaseAuth.getInstance().createSessionCookieAsync(
          idToken, options).get();
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
      FirebaseToken decodedToken = FirebaseAuth.getInstance().verifySessionCookieAsync(
          sessionCookie, checkRevoked).get();
      return serveContentForUser(decodedToken);
    } catch (Exception e) {
      // Session cookie is unavailable, invalid or revoked. Force user to login.
      return Response.temporaryRedirect(URI.create("/login")).build();
    }
  }
  // [END session_verify]

  public Response checkPermissions(String sessionCookie) {
    // [START session_verify_with_permission_check]
    try {
      final boolean checkRevoked = true;
      FirebaseToken decodedToken = FirebaseAuth.getInstance().verifySessionCookieAsync(
          sessionCookie, checkRevoked).get();
      if (Boolean.TRUE.equals(decodedToken.getClaims().get("admin"))) {
        return serveContentForAdmin(decodedToken);
      }
      return Response.status(Status.UNAUTHORIZED).entity("Insufficient permissions").build();
    } catch (Exception e) {
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
      FirebaseToken decodedToken = FirebaseAuth.getInstance().verifySessionCookieAsync(
          sessionCookie).get();
      FirebaseAuth.getInstance().revokeRefreshTokensAsync(decodedToken.getUid()).get();
      final int maxAge = 0;
      NewCookie newCookie = new NewCookie(cookie, null, maxAge, true);
      return Response.temporaryRedirect(URI.create("/login")).cookie(newCookie).build();
    } catch (Exception e) {
      return Response.temporaryRedirect(URI.create("/login")).build();
    }
  }
  // [END session_clear_and_revoke]
}