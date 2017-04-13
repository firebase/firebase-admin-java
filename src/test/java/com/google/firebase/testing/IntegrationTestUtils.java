package com.google.firebase.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.TestOnlyImplFirebaseTrampolines;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.internal.GetTokenResult;
import com.google.firebase.internal.Preconditions;
import com.google.firebase.tasks.Continuation;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class IntegrationTestUtils {

  public static final long ASYNC_WAIT_TIME_MS = 5000;

  private static JSONObject IT_SERVICE_ACCOUNT;

  public static String getDatabaseUrl() {
    String url = System.getProperty("firebase.it.url");
    return Preconditions.checkNotEmpty(
        url, "Database URL not set. Set the firebase.it.url " + "system property and try again.");
  }

  private static synchronized JSONObject ensureServiceAccount() {
    if (IT_SERVICE_ACCOUNT == null) {
      String certificatePath = System.getProperty("firebase.it.certificate");
      Preconditions.checkNotEmpty(
          certificatePath,
          "Service account certificate path not set. Set the "
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
  
  public static class AppHttpClient {
    
    private final FirebaseApp app;
    
    public AppHttpClient() {
      this(FirebaseApp.getInstance());
    }
    
    public AppHttpClient(FirebaseApp app) {
      this.app = Preconditions.checkNotNull(app);
    }
    
    public ResponseInfo put(String path, String data) throws IOException {
      String url = app.getOptions().getDatabaseUrl() + path + "?access_token=" + getToken();
      HttpPut put = new HttpPut(url);
      HttpEntity entity = new StringEntity(data, "UTF-8");
      put.setEntity(entity);
      
      HttpClient httpClient = new DefaultHttpClient();
      HttpResponse response = httpClient.execute(put);
      return new ResponseInfo(response);
    }
    
    private String getToken() {
      // TODO: We should consider exposing getToken (or similar) publicly for the
      // purpose of servers doing authenticated REST requests like this.
      Task<GetTokenResult> task = TestOnlyImplFirebaseTrampolines.getToken(app, false);
      try {
        return Tasks.await(task).getToken();
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
  public static class ResponseInfo {
    private final int status;
    private final byte[] payload;
    
    private ResponseInfo(HttpResponse response) throws IOException {
      this.status = response.getStatusLine().getStatusCode();
      this.payload = EntityUtils.toByteArray(response.getEntity());
    }

    public int getStatus() {
      return status;
    }

    public byte[] getPayload() {
      return payload;
    }
  }
}
