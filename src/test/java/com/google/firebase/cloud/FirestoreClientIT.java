package com.google.firebase.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.testing.IntegrationTestUtils;
import java.util.Map;
import org.junit.Test;

public class FirestoreClientIT {

  @Test
  public void testFirestoreAccess() throws Exception {
    Firestore firestore = FirestoreClient.getFirestore(IntegrationTestUtils.ensureDefaultApp());
    DocumentReference reference = firestore.collection("cities").document("Mountain View");
    ImmutableMap<String, Object> expected = ImmutableMap.<String, Object>of(
        "name", "Mountain View",
        "country", "USA",
        "population", 77846L,
        "capital", false
    );
    WriteResult result = reference.set(expected).get();
    assertNotNull(result);

    Map<String, Object> data = reference.get().get().getData();
    assertEquals(expected.size(), data.size());
    for (Map.Entry<String, Object> entry : expected.entrySet()) {
      assertEquals(entry.getValue(), data.get(entry.getKey()));
    }

    reference.delete().get();
    assertFalse(reference.get().get().exists());
  }
}
