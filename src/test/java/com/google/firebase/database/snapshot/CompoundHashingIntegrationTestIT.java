package com.google.firebase.database.snapshot;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.SynchronousConnection;
import com.google.firebase.database.core.utilities.ChildKeyGenerator;
import net.java.quickcheck.Generator;
import net.java.quickcheck.generator.support.IntegerGenerator;
import net.java.quickcheck.generator.support.StringGenerator;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static net.java.quickcheck.generator.PrimitiveGenerators.characters;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CompoundHashingIntegrationTestIT {

  private static final Random random = new Random();
  /* Use a fixed set of keys so updates might affect existing keys */
  private static final ChildKey[] keys;
  private static final StringGenerator leafStringGenerator =
      new StringGenerator(new IntegerGenerator(0, 500), characters('\u0000', '\u5000'));

  static {
    int numKeys = 20;
    keys = new ChildKey[numKeys];
    Generator<ChildKey> keyGen = new ChildKeyGenerator(25, /*includePriority=*/ false);
    for (int i = 0; i < numKeys; i++) {
      keys[i] = keyGen.next();
    }
  }

  private static Path randomPath(int maxLen) {
    int length = random.nextInt(maxLen);
    Path path = Path.getEmptyPath();
    for (int j = 0; j < length; j++) {
      path = path.child(keys[random.nextInt(keys.length)]);
    }
    boolean addPriority = Math.random() < 0.05;
    // There is an edge case where the server can (rightfully) send a node with only priority to the
    // client. Since this is parsed as empty node without priority on the client, the range merge
    // fails. For simplicity (and because priority has a whole bunch of other broken edge cases) we
    // will ignore it for now
    if (false && addPriority) {
      path = path.child(ChildKey.getPriorityKey());
    }
    return path;
  }

  private static void forEachLeaf(
      final Path currentPath, Node node, final LeafVisitor leafVisitor) {
    if (node.isLeafNode()) {
      leafVisitor.onLeaf(currentPath, (LeafNode<?>) node);
    } else if (node.isEmpty()) {
      // ignore
    } else {
      assert node instanceof ChildrenNode;
      ((ChildrenNode) node)
          .forEachChild(
              new ChildrenNode.ChildVisitor() {
                @Override
                public void visitChild(ChildKey name, Node child) {
                  forEachLeaf(currentPath.child(name), child, leafVisitor);
                }
              });
      if (!node.getPriority().isEmpty()) {
        forEachLeaf(currentPath.child(ChildKey.getPriorityKey()), node.getPriority(), leafVisitor);
      }
    }
  }

  private static Node extractRange(Node node, final Path optStart, final Path optEnd) {
    final Node[] result = new Node[] {EmptyNode.Empty()};
    forEachLeaf(
        Path.getEmptyPath(),
        node,
        new LeafVisitor() {
          @Override
          public void onLeaf(Path path, LeafNode<?> leaf) {
            boolean startIncluded = (optStart == null) || path.compareTo(optStart) > 0;
            boolean endIncluded = (optEnd == null) || path.compareTo(optEnd) <= 0;
            if (startIncluded && endIncluded) {
              result[0] = result[0].updateChild(path, leaf);
            }
          }
        });
    return result[0];
  }

  private static Node randomLeaf(boolean isPriority, boolean includeEmpty) {
    int type = random.nextInt(5);
    if (type == 0) {
      return includeEmpty ? EmptyNode.Empty() : randomLeaf(isPriority, false);
    } else if (type == 1) {
      return new StringNode(leafStringGenerator.next(), EmptyNode.Empty());
    } else if (type == 2) {
      return isPriority
          ? randomLeaf(true, includeEmpty)
          : new LongNode(random.nextLong(), EmptyNode.Empty());
    } else if (type == 3) {
      return new DoubleNode(random.nextDouble(), EmptyNode.Empty());
    } else if (type == 4) {
      return isPriority
          ? randomLeaf(true, includeEmpty)
          : new BooleanNode(random.nextBoolean(), EmptyNode.Empty());
    } else {
      throw new IllegalStateException("should not happen");
    }
  }

  private static Node randomNode(int numLeafs) {
    Node node = EmptyNode.Empty();
    for (int i = 0; i < numLeafs; i++) {
      Path path = randomPath(4);
      boolean isPriority = !path.isEmpty() && path.getBack().isPriorityChildName();
      node = node.updateChild(path, randomLeaf(isPriority, false));
    }
    return node;
  }

  private void oneRound() throws Throwable {
    final Node initialState = randomNode(random.nextInt(5000));
    Node currentState = initialState;
    String host = "http://tests.fblocal.com:9000";
    SynchronousConnection conn = new SynchronousConnection(host);
    conn.connect();
    conn.setValue(Path.getEmptyPath(), initialState, /*wait=*/ false);
    int numUpdates = random.nextInt(30);
    for (int i = 0; i < numUpdates; i++) {
      Path randomPath;
      Node randomLeaf;
      boolean isPriority;
      // Make sure we only create valid priorites updates or normal leaf node updates
      do {
        randomPath = randomPath(4);
        isPriority = !randomPath.isEmpty() && randomPath.getBack().isPriorityChildName();
        randomLeaf = randomLeaf(isPriority, true);
      } while (isPriority && currentState.getChild(randomPath.getParent()).isEmpty());
      currentState = currentState.updateChild(randomPath, randomLeaf);
      conn.setValue(randomPath, randomLeaf, /*wait=*/ false);
    }

    CompoundHash hash =
        CompoundHash.fromNode(
            initialState,
            new CompoundHash.SplitStrategy() {
              @Override
              public boolean shouldSplit(CompoundHash.CompoundHashBuilder state) {
                return random.nextInt(10) == 0
                    && (state.currentPath().isEmpty()
                    || !state.currentPath().getBack().isPriorityChildName());
              }
            });

    List<RangeMerge> merges =
        conn.listenWithHash(Path.getEmptyPath(), hash, initialState.getHash());

    Node afterMerge = initialState;
    if (merges != null) {
      for (RangeMerge merge : merges) {
        afterMerge = merge.applyTo(afterMerge);
      }
    }

    if (merges != null) {
      for (RangeMerge merge : merges) {
        Node initialRange = extractRange(initialState, merge.getStart(), merge.getEnd());
        Node afterMergeRange = extractRange(afterMerge, merge.getStart(), merge.getEnd());
        // If we got a range merge, the nodes must be different or hashing is broken
        assertFalse(afterMergeRange.equals(initialRange));
      }
    }
    assertEquals(currentState, afterMerge);

    conn.destroy();
  }

  @Test
  public void randomIntegrationTest() throws Throwable {
    for (int i = 0; i < 30; i++) {
      oneRound();
    }
  }

  interface LeafVisitor {

    void onLeaf(Path path, LeafNode<?> leaf);
  }
}
