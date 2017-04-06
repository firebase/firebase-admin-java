package com.google.firebase.database.collection;

import java.util.Comparator;

public interface LLRBNode<K, V> {

  LLRBNode<K, V> copy(K key, V value, Color color, LLRBNode<K, V> left, LLRBNode<K, V> right);

  LLRBNode<K, V> insert(K key, V value, Comparator<K> comparator);

  LLRBNode<K, V> remove(K key, Comparator<K> comparator);

  boolean isEmpty();

  boolean isRed();

  K getKey();

  V getValue();

  LLRBNode<K, V> getLeft();

  LLRBNode<K, V> getRight();

  LLRBNode<K, V> getMin();

  LLRBNode<K, V> getMax();

  int count();

  void inOrderTraversal(NodeVisitor<K, V> visitor);

  boolean shortCircuitingInOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor);

  boolean shortCircuitingReverseOrderTraversal(ShortCircuitingNodeVisitor<K, V> visitor);

  enum Color {
    RED,
    BLACK
  }

  interface ShortCircuitingNodeVisitor<K, V> {

    boolean shouldContinue(K key, V value);
  }

  abstract class NodeVisitor<K, V> implements ShortCircuitingNodeVisitor<K, V> {

    @Override
    public boolean shouldContinue(K key, V value) {
      visitEntry(key, value);
      return true;
    }

    public abstract void visitEntry(K key, V value);
  }
}
