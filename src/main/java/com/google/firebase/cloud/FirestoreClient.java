package com.google.firebase.cloud;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.common.base.Strings;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.internal.FirebaseService;
import com.google.firebase.internal.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code FirestoreClient} provides access to Google Cloud Firestore. Use this API to obtain a
 * <a href="https://googlecloudplatform.github.io/google-cloud-java/google-cloud-clients/apidocs/com/google/cloud/firestore/Firestore.html">{@code Firestore}</a>
 * instance, which provides methods for updating and querying data in Firestore.
 *
 * <p>A Google Cloud project ID is required to access Firestore. FirestoreClient determines the
 * project ID from the {@link com.google.firebase.FirebaseOptions} used to initialize the underlying
 * {@link FirebaseApp}. If that is not available, it examines the credentials used to initialize
 * the app. Finally it attempts to get the project ID by looking up the GOOGLE_CLOUD_PROJECT and
 * GCLOUD_PROJECT environment variables. If a project ID cannot be determined by any of these
 * methods, this API will throw a runtime exception.
 */
public class FirestoreClient {

  private static final Logger logger = LoggerFactory.getLogger(FirestoreClient.class);

  private final Firestore firestore;

  private FirestoreClient(FirebaseApp app, String databaseId) {
    checkNotNull(app, "FirebaseApp must not be null");
    String projectId = ImplFirebaseTrampolines.getProjectId(app);
    checkArgument(!Strings.isNullOrEmpty(projectId),
        "Project ID is required for accessing Firestore. Use a service account credential or "
            + "set the project ID explicitly via FirebaseOptions. Alternatively you can also "
            + "set the project ID via the GOOGLE_CLOUD_PROJECT environment variable.");
    FirestoreOptions userOptions = ImplFirebaseTrampolines.getFirestoreOptions(app);
    FirestoreOptions.Builder builder = userOptions != null
        ? userOptions.toBuilder() : FirestoreOptions.newBuilder();
    this.firestore = builder
        // CredentialsProvider has highest priority in FirestoreOptions, so we set that.
        .setCredentialsProvider(
            FixedCredentialsProvider.create(ImplFirebaseTrampolines.getCredentials(app)))
        .setProjectId(projectId)
        .setDatabaseId(databaseId)
        .build()
        .getService();
  }

  /**
   * Returns the default Firestore instance associated with the default Firebase app. Returns the
   * same instance for all invocations. The Firestore instance and all references obtained from it
   * becomes unusable, once the default app is deleted.
   *
   * @return A non-null <a href="https://googlecloudplatform.github.io/google-cloud-java/google-cloud-clients/apidocs/com/google/cloud/firestore/Firestore.html">{@code Firestore}</a>
   *     instance.
   */
  @NonNull
  public static Firestore getFirestore() {
    return getFirestore(FirebaseApp.getInstance());
  }

  /**
   * Returns the default Firestore instance associated with the specified Firebase app. For a given
   * app, invocation always returns the same instance. The Firestore instance and all references
   * obtained from it becomes unusable, once the specified app is deleted.
   *
   * @param app A non-null {@link FirebaseApp}.
   * @return A non-null <a href="https://googlecloudplatform.github.io/google-cloud-java/google-cloud-clients/apidocs/com/google/cloud/firestore/Firestore.html">{@code Firestore}</a>
   *     instance.
   */
  @NonNull
  public static Firestore getFirestore(FirebaseApp app) {
    return getFirestore(app, ImplFirebaseTrampolines.getFirestoreOptions(app).getDatabaseId());
  }

  /**
   * Returns the Firestore instance associated with the specified Firebase app. Returns the same
   * instance for all invocations given the same app and database parameter. The Firestore instance
   * and all references obtained from it becomes unusable, once the specified app is deleted.
   *
   * @param app      A non-null {@link FirebaseApp}.
   * @param database - The name of database.
   * @return A non-null <a href="https://googlecloudplatform.github.io/google-cloud-java/google-cloud-clients/apidocs/com/google/cloud/firestore/Firestore.html">{@code Firestore}</a>
   *     instance.
   */
  @NonNull
  static Firestore getFirestore(FirebaseApp app, String database) {
    return getInstance(app, database).firestore;
  }

  /**
   * Returns the Firestore instance associated with the default Firebase app. Returns the same
   * instance for all invocations given the same database parameter. The Firestore instance and all
   * references obtained from it becomes unusable, once the default app is deleted.
   *
   * @param database - The name of database.
   * @return A non-null <a href="https://googlecloudplatform.github.io/google-cloud-java/google-cloud-clients/apidocs/com/google/cloud/firestore/Firestore.html">{@code Firestore}</a>
   *     instance.
   */
  @NonNull
  static Firestore getFirestore(String database) {
    return getFirestore(FirebaseApp.getInstance(), database);
  }

  private static synchronized FirestoreClient getInstance(FirebaseApp app, String database) {
    FirestoreClientService service = ImplFirebaseTrampolines.getService(app,
        SERVICE_ID, FirestoreClientService.class);
    if (service == null) {
      service = ImplFirebaseTrampolines.addService(app, new FirestoreClientService(app));
    }
    return service.getInstance().get(database);
  }

  private static final String SERVICE_ID = FirestoreClient.class.getName();

  private static class FirestoreClientService extends FirebaseService<FirestoreInstances> {

    FirestoreClientService(FirebaseApp app) {
      super(SERVICE_ID, new FirestoreInstances(app));
    }

    @Override
    public void destroy() {
      instance.destroy();
    }
  }

  private static class FirestoreInstances {

    private final FirebaseApp app;

    private final Map<String, FirestoreClient> clients =
        Collections.synchronizedMap(new HashMap<>());

    private FirestoreInstances(FirebaseApp app) {
      this.app = app;
    }

    FirestoreClient get(String databaseId) {
      return clients.computeIfAbsent(databaseId, id -> new FirestoreClient(app, id));
    }

    void destroy() {
      synchronized (clients) {
        for (FirestoreClient client : clients.values()) {
          try {
            client.firestore.close();
          } catch (Exception e) {
            logger.warn("Error while closing the Firestore instance", e);
          }
        }
        clients.clear();
      }
    }
  }

}
