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

package com.google.firebase.database.utilities;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import org.junit.Test;

public class NodeSizeEstimatorTest {

  @Test
  public void testEstimateNodeSize() {
    Node node = NodeUtilities.NodeFromJSON(ImmutableMap.of());
    assertEquals(4L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(null);
    assertEquals(4L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON("leaf");
    assertEquals(6L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(10);
    assertEquals(8L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(10.01);
    assertEquals(8L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(true);
    assertEquals(4L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(false);
    assertEquals(4L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of(".value", "leaf", ".priority", 10));
    assertEquals(38L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of("foo", "bar"));
    assertEquals(13L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of("foo", "bar"));
    assertEquals(13L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of(
        ".value",  ImmutableMap.of("foo", "bar"),
        ".priority", 10
    ));
    assertEquals(33L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of("foo", "value", "bar", "value"));
    assertEquals(29L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of(
        "foo", "value",
        "bar", ImmutableMap.of("child", "value"))
    );
    assertEquals(39L, NodeSizeEstimator.estimateSerializedNodeSize(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of(
        "foo", "value",
        "bar", ImmutableMap.of("child1", "value1", "child2", "value2"))
    );
    assertEquals(59L, NodeSizeEstimator.estimateSerializedNodeSize(node));
  }

  @Test
  public void testNodeCount() {
    Node node = NodeUtilities.NodeFromJSON(ImmutableMap.of());
    assertEquals(0, NodeSizeEstimator.nodeCount(node));

    node = NodeUtilities.NodeFromJSON(null);
    assertEquals(0, NodeSizeEstimator.nodeCount(node));

    node = NodeUtilities.NodeFromJSON("leaf");
    assertEquals(1, NodeSizeEstimator.nodeCount(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of(".value", "leaf", ".priority", 10));
    assertEquals(1, NodeSizeEstimator.nodeCount(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of("foo", "bar"));
    assertEquals(1, NodeSizeEstimator.nodeCount(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of("foo", "value", "bar", "value"));
    assertEquals(2, NodeSizeEstimator.nodeCount(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of(
        "foo", "value",
        "bar", ImmutableMap.of("child", "value"))
    );
    assertEquals(2, NodeSizeEstimator.nodeCount(node));

    node = NodeUtilities.NodeFromJSON(ImmutableMap.of(
        "foo", "value",
        "bar", ImmutableMap.of("child1", "value1", "child2", "value2"))
    );
    assertEquals(3, NodeSizeEstimator.nodeCount(node));
  }

}
