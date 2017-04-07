package com.google.firebase.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.internal.Preconditions;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.json.JSONObject;

public class IntegrationTestUtils {
  
  public static final long ASYNC_WAIT_TIME_MS = 2000;
  
  private static JSONObject IT_SERVICE_ACCOUNT;
  
  public static String getDatabaseUrl() {
    String url = System.getProperty("firebase.it.url");
    return Preconditions.checkNotEmpty(url, "Database URL not set. Set the firebase.it.url "
        + "system property and try again.");
  }
  
  private synchronized static JSONObject ensureServiceAccount() {
    if (IT_SERVICE_ACCOUNT == null) {
      String certificatePath = System.getProperty("firebase.it.certificate");
      Preconditions.checkNotEmpty(certificatePath, "Service account certificate path not set. Set the "
          + "file.it.certificate system property and try again.");
      try (InputStreamReader reader = new InputStreamReader(new FileInputStream(certificatePath))) {
        IT_SERVICE_ACCOUNT = new JSONObject(CharStreams.toString(reader));
      } catch (IOException e) {
        throw new RuntimeException("Failed to read service account certificate", e);
      }
    }
    return IT_SERVICE_ACCOUNT;
  }
  
  public static InputStream getServiceAccountCertificate() {
    return new ByteArrayInputStream(ensureServiceAccount().toString().getBytes());
  }
  
  public static String getProjectId() {
    return ensureServiceAccount().get("project_id").toString();
  }
  
  public static String getProjectNumber() {
    return ensureServiceAccount().get("client_id").toString();
  }
  
  public static FirebaseApp initDefaultApp() {
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setDatabaseUrl(getDatabaseUrl())
            .setCredential(FirebaseCredentials.fromCertificate(getServiceAccountCertificate()))
            .build();
    return FirebaseApp.initializeApp(options);
  }
  
  public static DatabaseReference getRandomNode(FirebaseApp app) {
    return getRandomNodes(app, 1).get(0);
  }
  
  public static List<DatabaseReference> getRandomNodes(FirebaseApp app, int count) {
    FirebaseDatabase database = FirebaseDatabase.getInstance(app);
    ImmutableList.Builder<DatabaseReference> builder = ImmutableList.builder();
    for (int i = 0; i < count; i++) {
      builder.add(database.getReference().push());
    }
    return builder.build();
  }
  
}
