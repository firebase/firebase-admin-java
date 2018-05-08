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

package com.google.firebase.database;

import com.google.firebase.database.annotations.Nullable;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.SnapshotHolder;
import com.google.firebase.database.core.ValidationPath;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import com.google.firebase.database.snapshot.PriorityUtilities;
import com.google.firebase.database.utilities.Validation;
import com.google.firebase.database.utilities.encoding.CustomClassMapper;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Instances of this class encapsulate the data and priority at a location. It is used in
 * transactions, and it is intended to be inspected and then updated to the desired data at that
 * location. <br>
 * <br>
 * Note that changes made to a child MutableData instance will be visible to the parent and vice
 * versa.
 */
public class MutableData {

  private final SnapshotHolder holder;
  private final Path prefixPath;

  /**
   * Create a MutableData instance from a data node.
   *
   * @param node The data
   */
  MutableData(Node node) {
    this(new SnapshotHolder(node), new Path(""));
  }

  private MutableData(SnapshotHolder holder, Path path) {
    this.holder = holder;
    prefixPath = path;
    ValidationPath.validateWithObject(prefixPath, getValue());
  }

  Node getNode() {
    return holder.getNode(prefixPath);
  }

  /** Returns true if the data at this location has children, and false otherwise. */
  public boolean hasChildren() {
    Node node = getNode();
    return !node.isLeafNode() && !node.isEmpty();
  }

  /**
   * @param path A relative path
   * @return True if data exists at the given path, otherwise false
   */
  public boolean hasChild(String path) {
    return !getNode().getChild(new Path(path)).isEmpty();
  }

  /**
   * Used to obtain a MutableData instance that encapsulates the data and priority at the given
   * relative path.
   *
   * @param path A relative path
   * @return An instance encapsulating the data and priority at the given path
   */
  public MutableData child(String path) {
    Validation.validatePathString(path);
    return new MutableData(holder, prefixPath.child(new Path(path)));
  }

  /** 
   * @return The number of immediate children at this location
   */
  public long getChildrenCount() {
    return getNode().getChildCount();
  }

  /**
   * Used to iterate over the immediate children at this location <code>
   * <br>for (MutableData child : parent.getChildren()) {
   * <br>&nbsp;&nbsp;&nbsp;&nbsp;...
   * <br>}
   * </code>
   *
   * @return The immediate children at this location
   */
  public Iterable<MutableData> getChildren() {
    Node node = getNode();
    if (node.isEmpty() || node.isLeafNode()) {
      return new Iterable<MutableData>() {
        @Override
        public Iterator<MutableData> iterator() {
          return new Iterator<MutableData>() {
            @Override
            public boolean hasNext() {
              return false;
            }

            @Override
            public MutableData next() {
              throw new NoSuchElementException();
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException("remove called on immutable collection");
            }
          };
        }
      };
    } else {
      final Iterator<NamedNode> iter = IndexedNode.from(node).iterator();
      return new Iterable<MutableData>() {
        @Override
        public Iterator<MutableData> iterator() {
          return new Iterator<MutableData>() {
            @Override
            public boolean hasNext() {
              return iter.hasNext();
            }

            @Override
            public MutableData next() {
              NamedNode namedNode = iter.next();
              return new MutableData(holder, prefixPath.child(namedNode.getName()));
            }

            @Override
            public void remove() {
              throw new UnsupportedOperationException("remove called on immutable collection");
            }
          };
        }
      };
    }
  }

  /** 
   * @return The key name of this location, or null if it is the top-most location
   */
  public String getKey() {
    return prefixPath.getBack() != null ? prefixPath.getBack().asString() : null;
  }

  /**
   * getValue() returns the data contained in this instance as native types. The possible types
   * returned are:
   *
   * <ul>
   *   <li>Boolean
   *   <li>String
   *   <li>Long
   *   <li>Double
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * <p>This list is recursive; the possible types for <code>Object</code> in the above list is
   * given by the same list. These types correspond to the types available in JSON.
   *
   * @return The data contained in this instance as native types, or null if there is no data at
   *     this location.
   */
  @Nullable
  public Object getValue() {
    return getNode().getValue();
  }

  /**
   * Due to the way that Java implements generics, it takes an extra step to get back a
   * properly-typed Collection. So, in the case where you want a <code>List</code> of Message
   * instances, you will need to do something like the following:
   *
   * <pre><code>
   *     GenericTypeIndicator&lt;List&lt;Message&gt;&gt; t =
   *         new GenericTypeIndicator&lt;List&lt;Message&gt;&gt;() {};
   *     List&lt;Message&gt; messages = mutableData.getValue(t);
   * </code></pre>
   *
   * <p>It is important to use a subclass of {@link GenericTypeIndicator}. See {@link
   * GenericTypeIndicator} for more details
   *
   * @param t A subclass of {@link GenericTypeIndicator} indicating the type of generic collection
   *     to be returned.
   * @param <T> The type to return. Implicitly defined from the {@link GenericTypeIndicator} passed
   *     in
   * @return A properly typed collection, populated with the data from this instance, or null if
   *     there is no data at this location.
   */
  @Nullable
  public <T> T getValue(GenericTypeIndicator<T> t) {
    Object value = getNode().getValue();
    return CustomClassMapper.convertToCustomClass(value, t);
  }

  /**
   * This method is used to marshall the data contained in this instance into a class of your
   * choosing. The class must fit 2 simple constraints:
   *
   * <ol>
   *   <li>The class must have a default constructor that takes no arguments
   *   <li>The class must define public getters for the properties to be assigned. Properties
   *       without a public getter will be set to their default value when an instance is
   *       deserialized
   * </ol>
   *
   * <p>An example class might look like:
   *
   * <pre><code>
   *     class Message {
   *         private String author;
   *         private String text;
   *
   *         private Message() {}
   *
   *         public Message(String author, String text) {
   *             this.author = author;
   *             this.text = text;
   *         }
   *
   *         public String getAuthor() {
   *             return author;
   *         }
   *
   *         public String getText() {
   *             return text;
   *         }
   *     }
   *
   *
   *     // Later
   *     Message m = mutableData.getValue(Message.class);
   * </code></pre>
   *
   * @param valueType The class into which this data in this instance should be marshalled
   * @param <T> The type to return. Implicitly defined from the class passed in
   * @return An instance of the class passed in, populated with the data from this instance, or null
   *     if there is no data at this location.
   */
  @Nullable
  public <T> T getValue(Class<T> valueType) {
    Object value = getNode().getValue();
    return CustomClassMapper.convertToCustomClass(value, valueType);
  }

  /**
   * Set the data at this location to the given value. The native types accepted by this method for
   * the value correspond to the JSON types:
   *
   * <ul>
   *   <li>Boolean
   *   <li>Long
   *   <li>Double
   *   <li>Map&lt;String, Object&gt;
   *   <li>List&lt;Object&gt;
   * </ul>
   *
   * <br>
   * <br>
   * In addition, you can set instances of your own class into this location, provided they satisfy
   * the following constraints:
   *
   * <ol>
   *   <li>The class must have a default constructor that takes no arguments
   *   <li>The class must define public getters for the properties to be assigned. Properties
   *       without a public getter will be set to their default value when an instance is
   *       deserialized
   * </ol>
   *
   * <br>
   * <br>
   * Generic collections of objects that satisfy the above constraints are also permitted, i.e.
   * <code>Map&lt;String, MyPOJO&gt;</code>, as well as null values.
   *
   * <p>Note that this overrides the priority, which must be set separately.
   *
   * @param value The value to set at this location
   */
  public void setValue(Object value) throws DatabaseException {
    ValidationPath.validateWithObject(prefixPath, value);
    Object bouncedValue = CustomClassMapper.convertToPlainJavaTypes(value);
    Validation.validateWritableObject(bouncedValue);
    holder.update(prefixPath, NodeUtilities.NodeFromJSON(bouncedValue));
  }

  /**
   * Gets the current priority at this location. The possible return types are:
   *
   * <ul>
   *   <li>Double
   *   <li>String
   * </ul>
   *
   * <p>Note that null is allowed.
   *
   * @return The priority at this location as a native type
   */
  public Object getPriority() {
    return getNode().getPriority().getValue();
  }

  /**
   * Sets the priority at this location
   *
   * @param priority The desired priority
   */
  public void setPriority(Object priority) {
    holder.update(prefixPath, getNode().updatePriority(
        PriorityUtilities.parsePriority(prefixPath, priority)));
  }

  @Override
  public boolean equals(Object o) {
    // Look for the same snapshot holder and the same prefix path
    return o instanceof MutableData
        && holder.equals(((MutableData) o).holder)
        && prefixPath.equals(((MutableData) o).prefixPath);
  }

  @Override
  public String toString() {
    ChildKey front = this.prefixPath.getFront();
    return "MutableData { key = "
        + (front != null ? front.asString() : "<none>")
        + ", value = "
        + this.holder.getRootNode().getValue(true)
        + " }";
  }
}
