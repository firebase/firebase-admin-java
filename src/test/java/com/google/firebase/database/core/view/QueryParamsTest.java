package com.google.firebase.database.core.view;

import static com.google.firebase.database.TestHelpers.ck;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.snapshot.EmptyNode;
import java.util.Map;
import org.junit.Test;

public class QueryParamsTest {

  @Test
  public void startAtNullIsSerializable() {
    QueryParams params = QueryParams.DEFAULT_PARAMS;
    params = params.startAt(EmptyNode.Empty(), ck("key"));
    Map<String, Object> serialized = params.getWireProtocolParams();
    QueryParams parsed = QueryParams.fromQueryObject(serialized);
    assertEquals(params, parsed);
    assertTrue(params.hasStart());
  }

  @Test
  public void endAtNullIsSerializable() {
    QueryParams params = QueryParams.DEFAULT_PARAMS;
    params = params.endAt(EmptyNode.Empty(), ck("key"));
    Map<String, Object> serialized = params.getWireProtocolParams();
    QueryParams parsed = QueryParams.fromQueryObject(serialized);
    assertEquals(params, parsed);
    assertTrue(params.hasEnd());
  }
}
