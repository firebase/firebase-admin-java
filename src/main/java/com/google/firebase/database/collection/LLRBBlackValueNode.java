package com.google.firebase.database.collection;

public class LLRBBlackValueNode<K, V> extends LLRBValueNode<K, V> {

  LLRBBlackValueNode(K key, V value, LLRBNode<K, V> left, LLRBNode<K, V> right) {
    super(key, value, left, right);
  }

  @Override
  protected Color getColor() {
    return Color.BLACK;
  }

  @Override
  public boolean isRed() {
    return false;
  }

  @Override
  protected LLRBValueNode<K, V> copy(K key, V value, LLRBNode<K, V> left, LLRBNode<K, V> right) {
    K newKey = key == null ? this.getKey() : key;
    V newValue = value == null ? this.getValue() : value;
    LLRBNode<K, V> newLeft = left == null ? this.getLeft() : left;
    LLRBNode<K, V> newRight = right == null ? this.getRight() : right;
    return new LLRBBlackValueNode<>(newKey, newValue, newLeft, newRight);
  }
}
