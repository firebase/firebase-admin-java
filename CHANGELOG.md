# Unreleased

- [fixed] Fixing error handling in FCM. The SDK now checks the key
  `type.googleapis.com/google.firebase.fcm.v1.FcmError` to set error
  code.
- [fixed] FCM errors sent by the back-end now include more details
  that are helpful when debugging problems.
- [changed] Migrated the `FirebaseAuth` user management API to the
  new Identity Toolkit endpoint.

# v6.5.0

- [added] `FirebaseOptions.Builder` class now provides a
  `setFirestoreOptions()` method for configuring the Firestore client.
- [changed] Upgraded the Cloud Firestore client to 0.61.0-beta.
- [changed] Upgraded the Cloud Storage client to 1.43.0.

# v6.4.0

- [added] `WebpushNotification` type now supports arbitrary key-value
  pairs in its payload.

# v6.3.0

- [added] Implemented the ability to create custom tokens without
  service account credentials.
- [added] Added the `setServiceAccount()` method to the
  `FirebaseOptions.Builder` API.
- [added] The SDK can now read the Firebase/GCP project ID from both
  `GCLOUD_PROJECT` and `GOOGLE_CLOUD_PROJECT` environment variables.

# v6.2.0

- [added] Added new `importUsersAsync()` API for bulk importing users
  into Firebase Auth.

# v6.1.0

- [changed] Deprecated the `FirebaseAuth.setCustomClaims()` method.
  Developers should use the `FirebaseAuth.setCustomUserClaims()` method
  instead.

# v6.0.0

- [added] `FirebaseAuth`, `FirebaseMessaging` and `FirebaseInstanceId`
  interfaces now expose a set of blocking methods. Each operation has
  blocking an asynchronous versions.
- [changed] Removed the deprecated `FirebaseCredential` interface.
- [changed] Removed the deprecated `Task` interface along with the
  `com.google.firebase.tasks` package.
- [changed] Dropped support for App Engine's Java 7 runtime. Developers
  are advised to use the Admin SDK with Java 8 when deploying to App
  Engine.
- [changed] Removed the deprecated `FirebaseDatabase.setLogLevel()` API
  and the related logging utilities. Developers should use SLF4J to
  configure logging directly.

# v5.11.0

- [added] A new `FirebaseAuth.createSessionCookieAsync()` method for
  creating a long-lived session cookie given a valid ID token.
- [added] A new `FirebaseAuth.verifySessionCookieAsync()` method for
  verifying a given cookie string is valid.
- [fixed] Upgraded Cloud Firestore dependency version to 0.45.0-beta.
- [fixed] Upgraded Cloud Storage dependency version to 1.27.0.
- [fixed] Upgraded Netty dependency version to 4.1.22.

# v5.10.0

- [fixed] Using the `HttpTransport` specified at `FirebaseOptions` in
  `GooglePublicKeysManager`. This enables developers to use a custom
  transport to fetch public keys when verifying ID tokens and session
  cookies.
- [added] Connection timeout and read timeout for HTTP/REST connections
  can now be configured via `FirebaseOptions.Builder` at app
  initialization.
- [added] Added new `setMutableContent()`, `putCustomData()` and
  `putAllCustomData()` methods to the `Aps.Builder` API.
- [fixed] Improved error handling in FCM by mapping more server-side
  errors to client-side error codes.

# v5.9.0

### Cloud Messaging

- [feature] Added the `FirebaseCloudMessaging` API for sending
  Firebase notifications and managing topic subscriptions.

### Authentication

- [added] The [`verifyIdTokenAsync()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#verifyIdTokenAsync)
  method has an overload that accepts a boolean `checkRevoked` parameter.
  When `true`, an additional check is performed to see whether the token
  has been revoked.
- [added] A new [`revokeRefreshTokensAsync()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#revokeRefreshTokens)
  method has been added to invalidate all tokens issued to a user.
- [added] A new getter `getTokensValidAfterTimestamp()` has been added
  to the [`UserRecord`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/UserRecord)
  class, which denotes the time before which tokens are not valid.

### Realtime Database

- [fixed] Exceptions thrown by database event handlers are now logged.

### Initialization

- [fixed] The [`FirebaseOptions.Builder.setStorageBucket()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions.Builder.html#setStorageBucket(java.lang.String))
  method now throws a clear exception when invoked with a bucket URL
  instead of the name.
- [fixed] Implemented a fix for a potential Guava version conflict which
  was causing an `IllegalStateException` (precondition failure) in some
  environments.

### Cloud Firestore

- [fixed] Upgraded the Cloud Firestore client to the latest available
  version.

# v5.8.0

### Initialization

- [added] The [`FirebaseApp.initializeApp()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseApp.html#initializeApp())
  method now provides an overload that does not require any arguments. This
  initializes an app using Google Application Default Credentials, and other
  [`FirebaseOptions`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions)
  loaded from the `FIREBASE_CONFIG` environment variable.

### Authentication

- [changed] Improved error handling in user management APIs in the
  [`FirebaseAuth`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth)
  class. These operations now throw exceptions with well-defined
  [error codes](https://firebase.google.com/docs/auth/admin/errors).

### Realtime Database

- [changed] The SDK now serializes large whole double values as longs when
  appropriate.

# v5.7.0

- [added] A new [`FirebaseInstanceId`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/iid/FirebaseInstanceId)
  API that facilitates deleting instance IDs and associated user data from
  Firebase projects.

### Authentication
- [changed] No longer using `org.json` dependency in Authentication APIs, which
  makes it easier to use the API in environments with conflicting JSON
  libraries.

# v5.6.0

- [changed] Upgraded the version of Google API Common dependency to the latest
  (1.2.0).

### Authentication
- [added] Added the
  [`listUsersAsync()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#listUsersAsync(java.lang.String))
  method to the
  [`FirebaseAuth`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth)
  class. This method enables listing or iterating over all user accounts
  in a Firebase project.
- [added] Added the
  [`setCustomUserClaimsAsync()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#setCustomUserClaimsAsync(java.lang.String, java.util.Map<java.lang.String, java.lang.Object>))
  method to the
  [`FirebaseAuth`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth)
  class. This method enables setting custom claims on a Firebase user.
  The custom claims can be accessed via that user's ID token.

### Realtime Database
- [changed] Re-implemented the WebSocket communication layer of the
  Realtime Database client using Netty.

# v5.5.0

- [added] A new [`FirestoreClient`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/cloud/FirestoreClient)
  API that enables access to [Cloud Firestore](https://firebase.google.com/docs/firestore) databases.

### Realtime Database
- [changed] Ensured graceful termination of database worker threads upon
  callng [`FirebaseApp.delete()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseApp.html#delete()).

# v5.4.0

- [added] A new [`ThreadManager`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/ThreadManager)
  API that can be used to specify the thread
  pool and the `ThreadFactory` that should be used by the SDK.
- [added] All APIs that support asynchronous operations now return an
  [`ApiFuture`](https://googleapis.github.io/api-common-java/1.1.0/apidocs/com/google/api/core/ApiFuture.html).
  The old [`Task`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/tasks/Task)
  API has been deprecated. For each method `x()`
  that returns a `Task`, a new `xAsync()` method that returns an `ApiFuture`
  has been introduced.
- [changed] The SDK now guarantees the graceful termination of all started
  threads. In most environments, the SDK will use daemons for all background
  activities. The developer can also initiate a graceful termination of threads
  by calling [`FirebaseApp.delete()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseApp.html#delete()).

### Initialization

- [added] [`FirebaseOptions`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions)
  can now be initialized with
  [`GoogleCredentials`](http://google.github.io/google-auth-library-java/releases/0.7.1/apidocs/com/google/auth/oauth2/GoogleCredentials.html).
  This is the new recommended way to specify credentials when initializing the
  SDK. The old [`FirebaseCredential`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredential)
  and [`FirebaseCredentials`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredentials)
  APIs have been deprecated.

# v5.3.1

### Authentication
- [changed] Throwing an accurate and more detailed error from
  [`verifyIdToken()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#verifyIdToken(java.lang.String))
  in the event of a low-level exception.

### Realtime Database
- [changed] Proper handling and logging of exceptions thrown by the
  [`onComplete()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/database/Transaction.Handler.html#onComplete(com.google.firebase.database.DatabaseError, boolean, com.google.firebase.database.DataSnapshot))
  event of transaction handlers.

# v5.3.0
- [added] A new [{{firebase_storage}} API](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/cloud/StorageClient)
  that facilitates accessing Google Cloud Storage buckets using the
  [`google-cloud-storage`](http://googlecloudplatform.github.io/google-cloud-java/latest/apidocs/com/google/cloud/storage/Storage.html)
  library.
- [added] Integrated with the [SLF4J](https://www.slf4j.org/) library for all logging
  purposes.

### Authentication
- [added] Added the method
  [`getUserByPhoneNumber()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#getUserByPhoneNumber(java.lang.String))
  to the [`FirebaseAuth`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth)
  interface. This method
  enables retrieving user profile information by a phone number.
- [added] [`CreateRequest`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/UserRecord.CreateRequest)
  and [`UpdateRequest`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/UserRecord.UpdateRequest) types
  now provide setters for specifying a phone number, which can be used to create users with a phone
  number field and/or update the phone number associated with a user.
- [added] Added the `getPhoneNumber()` method to
  [`UserRecord`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/UserRecord),
  which exposes the phone number associated with a user account.
- [added] Added the `getPhoneNumber()` method to
  [`UserInfo`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/UserInfo),
  which exposes the phone number associated with a user account by a linked
  identity provider.

### Realtime Database
- {{changed}} Deprecated the `FirebaseDatabase.setLogLevel()` method. Use SLF4J to configure logging.
- [changed] Logging a detailed error when the database client fails to authenticate with the backend
  Firebase servers.

# v5.2.0
- [added] New factory methods in the
  [`FirebaseCredentials`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredentials)
  class
  that accept `HttpTransport` and `JsonFactory` arguments. These settings are
  used when the credentials make HTTP calls to obtain OAuth2 access tokens.
- [added] New `setHttpTransport()` and `setJsonFactory()` methods in the
  [`FirebaseOptions`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions)
  class. These settings are used by all services of the SDK except
  `FirebaseDatabase`.

# v5.1.0

### Authentication

- [added] A new user management API that allows provisioning and managing
  Firebase users from Java applications. This API adds `getUser()`,
  `getUserByEmail()`, `createUser()`, `updateUser()` and `deleteUser()` methods
  to the [`FirebaseAuth`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth)
  interface.

# v5.0.1

### Realtime Database

- [changed] Fixed a database API thread leak that made the SDK
  unstable when running in the Google App Engine environment.

# v5.0.0

### Initialization

- [added] Factory methods in
  [`FirebaseCredentials`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredentials)
  class can now throw `IOExceptions`, providing fail-fast semantics while
  facilitating simpler error handling.
- [added] The deprecated `setServiceAccount()` method has been removed
  from the
  [`FirebaseOptions.Builder`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions.Builder)
  class in favor of the `setCredential()` method.
- [added] Trying to initialize the SDK without setting a credential now
  results in an exception.
- {{changed}} The
  [`FirebaseCredential`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredential)
  interface now returns a new
  [`GoogleOAuthAccessToken`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/GoogleOAuthAccessToken)
  type, which encapsulates both token string and its expiry time.

# v4.1.7

- [added] Introducing a new `FirebaseApp.delete()` method, which can
  be used to gracefully shut down app instances. All app invocations after a
  call to `delete()` will throw exceptions. Deleted app instances can also be
  re-initialized with the same name if necessary.

- [changed] Upgraded SDK dependencies. Guava, Google API Client, and JSON
  libraries that the SDK depends on have been upgraded to more recent versions.

# v4.1.6

### Realtime Database

- [changed] Updated the SDK to select the correct thread pool
  implementation when running on a regular JVM with App Engine
  libraries in the `classpath`.

# v4.1.5

### Realtime Database

- [changed] Fixed the invalid SDK version constant in the `FirebaseDatabase`
  class that was released in v4.1.4.

# v4.1.4

### Authentication

- [changed] Updated the SDK to periodically refresh the OAuth access token
  internally used by `FirebaseApp`. This reduces the number of authentication
  failures encountered at runtime by various SDK components (e.g. Realtime Database)
  to nearly zero. This feature is active by default when running in typical
  Java environments, or the Google App Engine environment with background
  threads support.

# v4.1.3

### Realtime Database

- [changed] Updated Realtime Database to properly swap out the ID token used to
  authenticate the underlying websocket when a new ID token is generated. The
  websocket connection is still disconnected and reconnected every hour when an
  ID token expires unless you manually call
  [`getAccessToken`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredential.html#getAccessToken(boolean))
  on the `FirebaseCredential` used to authenticate the SDK. In a future
  release, the SDK will proactively refresh ID tokens automatically before they
  expire.


# v4.1.2

### Initialization

- [changed] Updated `initalizeApp()` to synchronously read from an `InputStream`
  to avoid issues with closing the stream after initializing the SDK.
- [changed] Improved confusing error messages when initializing the SDK with
  a `null` or malformed `InputStream`.


# v4.1.1
### Authentication

- [changed] Fixed a dependency issue which caused the
  [`verifyIdToken()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#verifyIdToken(java.lang.String))
  method to always throw an exception.


# v4.1.0
### Initialization

- {{deprecated}} The
  [`FirebaseOptions.Builder.setServiceAccount()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions.Builder.html#setServiceAccount(java.io.InputStream))
  method has been deprecated in favor of a new
  [`FirebaseOptions.Builder.setCredential()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions.Builder.html#setCredential(com.google.firebase.auth.FirebaseCredential))
  method. See [Initialize the SDK](https://firebase.google.com/docs/admin/setup/#initialize_the_sdk) for
  usage instructions.
- [added] The new
  [`FirebaseCredential.fromCertificate()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredentials.html#fromCertificate(java.io.InputStream))
  method allows you to authenticate the SDK with a service account certificate
  file.
- [added] The new
  [`FirebaseCredential.fromRefreshToken()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredentials.html#fromRefreshToken(java.io.InputStream))
  method allows you to authenticate the SDK with a Google OAuth2 refresh token.
- [added] The new
  [`FirebaseCredential.applicationDefault()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseCredentials.html#applicationDefault())
  method allows you to authenticate the SDK with Google Application Default
  Credentials.

### Authentication

- [issue] The
  [`verifyIdToken()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#verifyIdToken(java.lang.String))
  method is broken in this release and throws an exception due to an incorrect
  dependency. This was fixed in version [`4.1.1`](#4.1.1).


# v4.0.4

- [changed] Fixed issue which caused threads to be terminated in Google App
  Engine after 24 hours, rendering the SDK unresponsive.
- [changed] Fixed issues which caused asynchronous task execution to fail on
  automatically-scaled Google App Engine instances.

### Authentication

- [added] Improved error messages and added App Engine support for the
  [`verifyIdToken()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#verifyIdToken(java.lang.String))
  method.

### Realtime Database

- [changed] Fixed a race condition which could occur when new writes are added
  while the connection is being closed.


# v4.0.3

### Initialization

- [changed] Fixed an issue that caused a `null` input to the
  [`setDatabaseAuthVariableOverride()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/FirebaseOptions.Builder.html#setDatabaseAuthVariableOverride(java.util.Map<java.lang.String, java.lang.Object>))
  method to be ignored, which caused the app to still have full admin access.
  Now, passing this value has the expected behavior: the app has unauthenticated
  access to the Realtime Database, and behaves as if no user is logged into the
  app.

### Realtime Database

- [changed] Use of the
  [`updateChildren()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/database/DatabaseReference.html#updateChildren(java.util.Map<java.lang.String, java.lang.Object>))
  method now only cancels transactions that are directly included in the updated
  paths (not transactions in adjacent paths). For example, an update at `/move`
  for a child node `walk` will cancel transactions at `/`, `/move`, and
  `/move/walk` and in any child nodes under `/move/walk`. But, it will no longer
  cancel transactions at sibling nodes, such as `/move/run`.


# v4.0.2

- [changed] This update restores Java 7 compatibilty for the Admin Java SDK.


# v4.0.1

- [changed] Fixed an issue with a missing dependency in the [`4.0.0`](#4.0.0)
  JAR which caused the Database API to not work.
- [issue] This version was compiled for Java 8 and does not support Java 7.
  This was fixed in version [`4.0.2`](#4.0.2).


# v4.0.0

- [added] The Admin Java SDK (available on Maven as `firebase-admin`)
  replaces the pre-existing `firebase-server-sdk` Maven package, which is now
  deprecated. See
  [Add the Firebase Admin SDK to your Server](https://firebase.google.com/docs/admin/setup/) to get
  started.
- [issue] This version is missing a dependency which causes the Database API
  to not work. This was fixed in version [`4.0.1`](#4.0.1).
- [issue] This version was compiled for Java 8 and does not support Java 7.
  This was fixed in version [`4.0.2`](#4.0.2).

### Authentication

- [changed] The
  [`createCustomToken()`](https://firebase.google.com/docs/reference/admin/java/reference/com/google/firebase/auth/FirebaseAuth.html#createCustomToken(java.lang.String))
  method is now asynchronous, returning a `Task<String>` instead of a `String`.


