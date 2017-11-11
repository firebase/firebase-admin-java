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

package com.google.firebase.database.snapshot;

import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.LeafNode.LeafType;
import com.google.firebase.database.snapshot.Node.HashVersion;
import java.util.Map;
import org.junit.Test;

public class NodeTest {

  @Test
  public void getHashWorksCorrectly() {
    Map<String, Object> data =
        new MapBuilder()
            .put("intNode", 4)
            .put("doubleNode", 4.5623)
            .put("stringNode", "hey guys")
            .put("boolNode", true)
            .build();

    Node node = NodeFromJSON(data);

    Node child = node.getImmediateChild(ChildKey.fromString("intNode"));
    String hash = child.getHash();
    assertEquals("eVih19a6ZDz3NL32uVBtg9KSgQY=", hash);

    child = node.getImmediateChild(ChildKey.fromString("doubleNode"));
    hash = child.getHash();
    assertEquals("vf1CL0tIRwXXunHcG/irRECk3lY=", hash);

    child = node.getImmediateChild(ChildKey.fromString("stringNode"));
    hash = child.getHash();
    assertEquals("CUNLXWpCVoJE6z7z1vE57lGaKAU=", hash);

    child = node.getImmediateChild(ChildKey.fromString("boolNode"));
    hash = child.getHash();
    assertEquals("E5z61QM0lN/U2WsOnusszCTkR8M=", hash);

    hash = node.getHash();
    assertEquals("6Mc4jFmNdrLVIlJJjz2/MakTK9I=", hash);
  }

  @Test
  public void matchServerHash() {
    Map<String, Object> wireData =
        new MapBuilder()
            .put("c", new MapBuilder().put(".value", 99).put(".priority", "abc").build())
            .put(".priority", "def")
            .build();
    Node node = NodeFromJSON(wireData);
    node = EmptyNode.Empty().updateChild(new Path("root"), node);
    String hash = node.getHash();
    assertEquals("Fm6tzN4CVEu5WxFDZUdTtqbTVaA=", hash);
  }

  @Test
  public void leadingZeroesWorkCorrectly() {
    Map<String, Object> data =
        new MapBuilder().put("1", 1).put("01", 2).put("001", 3).put("0001", 4).build();

    Node node = NodeFromJSON(data);

    Node child = node.getImmediateChild(ChildKey.fromString("1"));
    assertEquals(1L, child.getValue());

    child = node.getImmediateChild(ChildKey.fromString("01"));
    assertEquals(2L, child.getValue());

    child = node.getImmediateChild(ChildKey.fromString("001"));
    assertEquals(3L, child.getValue());

    child = node.getImmediateChild(ChildKey.fromString("0001"));
    assertEquals(4L, child.getValue());
  }

  @Test
  public void leadingZerosDoNotOverwriteOtherKeys() {
    Map<String, Object> data =
        new MapBuilder()
            .put("1", "value1")
            .put("01", "value2")
            .put("001", "value3")
            .put("0001", "value4")
            .build();

    Node node = NodeFromJSON(data);
    assertEquals(node.getImmediateChild(ChildKey.fromString("1")).getValue(), "value1");
    assertEquals(node.getImmediateChild(ChildKey.fromString("01")).getValue(), "value2");
    assertEquals(node.getImmediateChild(ChildKey.fromString("001")).getValue(), "value3");
    assertEquals(node.getImmediateChild(ChildKey.fromString("0001")).getValue(), "value4");
  }

  @Test
  public void leadingZerosDoNotOverwriteKeysInValue() {
    Map<String, Object> data = new MapBuilder().put("1", "value1").put("01", "value2").build();

    Node node = NodeFromJSON(data);
    assertEquals(node.getValue(), data);
  }

  @Test
  public void emptyNodeEqualsEmptyChildrenNode() {
    assertEquals(EmptyNode.Empty(), new ChildrenNode());
    assertEquals(new ChildrenNode(), EmptyNode.Empty());
  }

  @Test
  public void updatingEmptyChildrenDoesntOverwriteLeafNode() {
    LeafNode<StringNode> node = new StringNode("value", PriorityUtilities.NullPriority());
    assertEquals(node, node.updateChild(new Path(".priority"), EmptyNode.Empty()));
    assertEquals(node, node.updateChild(new Path("child"), EmptyNode.Empty()));
    assertEquals(node, node.updateChild(new Path("child/.priority"), EmptyNode.Empty()));
    assertEquals(node, node.updateImmediateChild(ChildKey.fromString("child"), EmptyNode.Empty()));
    assertEquals(node, node.updateImmediateChild(ChildKey.getPriorityKey(), EmptyNode.Empty()));
  }

  @Test
  public void updatingPrioritiesOnEmptyNodesIsANoop() {
    Node priority = PriorityUtilities.parsePriority("prio");
    assertTrue(EmptyNode.Empty().updatePriority(priority).getPriority().isEmpty());
    assertTrue(
        EmptyNode.Empty().updateChild(new Path(".priority"), priority).getPriority().isEmpty());
    assertTrue(
        EmptyNode.Empty()
            .updateImmediateChild(ChildKey.getPriorityKey(), priority)
            .getPriority()
            .isEmpty());

    Node reemptiedChildren =
        EmptyNode.Empty()
            .updateChild(new Path("child"), NodeFromJSON("value"))
            .updateChild(new Path("child"), EmptyNode.Empty());
    assertTrue(reemptiedChildren.updatePriority(priority).getPriority().isEmpty());
    assertTrue(
        reemptiedChildren.updateChild(new Path(".priority"), priority).getPriority().isEmpty());
    assertTrue(
        reemptiedChildren
            .updateImmediateChild(ChildKey.getPriorityKey(), priority)
            .getPriority()
            .isEmpty());
  }

  @Test
  public void deletingLastChildFromChildrenNodeRemovesPriority() {
    Node priority = PriorityUtilities.parsePriority("prio");
    Node withPriority =
        EmptyNode.Empty()
            .updateChild(new Path("child"), NodeFromJSON("value"))
            .updatePriority(priority);
    assertEquals(priority, withPriority.getPriority());
    Node deletedChild = withPriority.updateChild(new Path("child"), EmptyNode.Empty());
    assertTrue(deletedChild.getPriority().isEmpty());
  }

  @Test
  public void nodeFromJsonReturnsEmptyNodesWithoutPriority() {
    Node empty1 = NodeFromJSON(new MapBuilder().put(".priority", "prio").build());
    assertTrue(empty1.getPriority().isEmpty());

    Node empty2 =
        NodeFromJSON(new MapBuilder().put("dummy-node", null).put(".priority", "prio").build());
    assertTrue(empty2.getPriority().isEmpty());
  }

  @Test
  public void testEmptyChildrenNode() {
    ChildrenNode node = new ChildrenNode();
    assertTrue(node.isEmpty());
    assertNull(node.getValue());
    assertEquals("{ }", node.toString());
    assertEquals(0, node.hashCode());
    assertEquals(0, node.compareTo(new ChildrenNode()));
  }

  @Test
  public void testEmptyChildrenNodeWithPriority() {
    ImmutableSortedMap<ChildKey, Node> map = ImmutableSortedMap.Builder.fromMap(
        ImmutableMap.<ChildKey, Node>of(), null);
    try {
      new ChildrenNode(map, PriorityUtilities.parsePriority(1));
      fail("No error thrown for empty children node");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testChildrenNode() {
    ImmutableMap<ChildKey, Node> source = ImmutableMap.of(
        ChildKey.fromString("foo"), NodeUtilities.NodeFromJSON("value1"),
        ChildKey.fromString("bar"), NodeUtilities.NodeFromJSON("value2")
    );
    ImmutableSortedMap<ChildKey, Node> map = ImmutableSortedMap.Builder.fromMap(source, null);
    ChildrenNode node = new ChildrenNode(map, PriorityUtilities.parsePriority(1));
    assertFalse(node.isEmpty());
    assertEquals(ImmutableMap.of("foo", "value1", "bar", "value2"), node.getValue());
    assertEquals(
        "{\n"
        + "  bar=value2\n"
        + "  foo=value1\n"
        + "  .priority=1.0\n"
        + "}", node.toString());

    ChildrenNode other = new ChildrenNode(map, PriorityUtilities.parsePriority(1));
    assertEquals(node, other);
    assertEquals(node.hashCode(), other.hashCode());
  }

  @Test
  public void testDeferredValueNode() {
    Map<Object, Object> map = ImmutableMap.<Object, Object>of("foo", "bar", "int", 5);
    DeferredValueNode node = new DeferredValueNode(map, PriorityUtilities.parsePriority(1));
    assertEquals(map, node.getValue());
    assertEquals(LeafType.DeferredValue, node.getLeafType());
    assertNotNull(node.getHashRepresentation(HashVersion.V1));

    DeferredValueNode otherNode = new DeferredValueNode(
        ImmutableMap.<Object, Object>of("other", "value"),
        PriorityUtilities.parsePriority(2));
    assertEquals(0, node.compareLeafValues(otherNode));

    assertNotEquals(node, otherNode);
    assertNotEquals(node.hashCode(), otherNode.hashCode());
  }
}
