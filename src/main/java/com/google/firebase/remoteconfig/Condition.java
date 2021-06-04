/*
 * Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Strings;
import com.google.firebase.internal.NonNull;
import com.google.firebase.internal.Nullable;
import com.google.firebase.remoteconfig.internal.TemplateResponse.ConditionResponse;

import java.util.Objects;

/**
 * Represents a Remote Config condition that can be included in a {@link Template}.
 * A condition targets a specific group of users. A list of these conditions make up
 * part of a Remote Config template.
 */
public final class Condition {

  private String name;
  private String expression;
  private TagColor tagColor;

  /**
   * Creates a new {@link Condition}.
   *
   * @param name A non-null, non-empty, and unique name of this condition.
   * @param expression A non-null and non-empty expression of this condition.
   */
  public Condition(@NonNull String name, @NonNull String expression) {
    this(name, expression, null);
  }

  /**
   * Creates a new {@link Condition}.
   *
   * @param name A non-null, non-empty, and unique name of this condition.
   * @param expression A non-null and non-empty expression of this condition.
   * @param tagColor A color associated with this condition for display purposes in the
   *                 Firebase Console. Not specifying this value results in the console picking an
   *                 arbitrary color to associate with the condition.
   */
  public Condition(@NonNull String name, @NonNull String expression, @Nullable TagColor tagColor) {
    checkArgument(!Strings.isNullOrEmpty(name), "condition name must not be null or empty");
    checkArgument(!Strings.isNullOrEmpty(expression),
            "condition expression must not be null or empty");
    this.name = name;
    this.expression = expression;
    this.tagColor = tagColor;
  }

  Condition(@NonNull ConditionResponse conditionResponse) {
    checkNotNull(conditionResponse);
    this.name = conditionResponse.getName();
    this.expression = conditionResponse.getExpression();
    if (!Strings.isNullOrEmpty(conditionResponse.getTagColor())) {
      this.tagColor = TagColor.valueOf(conditionResponse.getTagColor());
    }
  }

  /**
   * Gets the name of the condition.
   *
   * @return The name of the condition.
   */
  @NonNull
  public String getName() {
    return name;
  }

  /**
   * Gets the expression of the condition.
   *
   * @return The expression of the condition.
   */
  @NonNull
  public String getExpression() {
    return expression;
  }

  /**
   * Gets the tag color of the condition used for display purposes in the Firebase Console.
   *
   * @return The tag color of the condition.
   */
  @NonNull
  public TagColor getTagColor() {
    return tagColor;
  }

  /**
   * Sets the name of the condition.
   *
   * @param name A non-empty and unique name of this condition.
   * @return This {@link Condition}.
   */
  public Condition setName(@NonNull String name) {
    checkArgument(!Strings.isNullOrEmpty(name), "condition name must not be null or empty");
    this.name = name;
    return this;
  }

  /**
   * Sets the expression of the condition.
   *
   * <p>See <a href="https://firebase.google.com/docs/remote-config/condition-reference">
   * condition expressions</a> for the expected syntax of this field.
   *
   * @param expression The logic of this condition.
   * @return This {@link Condition}.
   */
  public Condition setExpression(@NonNull String expression) {
    checkArgument(!Strings.isNullOrEmpty(expression),
            "condition expression must not be null or empty");
    this.expression = expression;
    return this;
  }

  /**
   * Sets the tag color of the condition.
   *
   * <p>The color associated with this condition for display purposes in the Firebase Console.
   * Not specifying this value results in the console picking an arbitrary color to associate
   * with the condition.
   *
   * @param tagColor The tag color of this condition.
   * @return This {@link Condition}.
   */
  public Condition setTagColor(@Nullable TagColor tagColor) {
    this.tagColor = tagColor;
    return this;
  }

  ConditionResponse toConditionResponse() {
    return new ConditionResponse()
            .setName(this.name)
            .setExpression(this.expression)
            .setTagColor(this.tagColor == null ? null : this.tagColor.getColor());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Condition condition = (Condition) o;
    return Objects.equals(name, condition.name)
            && Objects.equals(expression, condition.expression) && tagColor == condition.tagColor;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, expression, tagColor);
  }
}
