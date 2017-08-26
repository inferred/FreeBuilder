/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.StaticExcerpt;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Property-type-specific code generation interface. */
public abstract class PropertyCodeGenerator {

  /** Data available to {@link Factory} instances when creating a {@link PropertyCodeGenerator}. */
  interface Config {
    /** Returns metadata about the builder being generated. */
    Metadata getMetadata();

    /** Returns metadata about the property requiring code generation. */
    Metadata.Property getProperty();

    /** Returns annotations on the property requiring code generation. */
    List<? extends AnnotationMirror> getAnnotations();

    /** The user's Builder type. */
    TypeElement getBuilder();

    /**
     * A set of methods that are definitely invoked in the builder constructor. This may have false
     * negatives (e.g. if method introspection has not been implemented for the current compiler),
     * so must only be used for making optimizations.
     */
    Set<String> getMethodsInvokedInBuilderConstructor();

    /** The compiler's {@link Elements} implementation. */
    Elements getElements();

    /** The compiler's {@link Types} implementation. */
    Types getTypes();
  }

  /** Factory interface for {@link PropertyCodeGenerator}. */
  interface Factory {
    /**
     * Create a new {@link PropertyCodeGenerator} for the property described in {@code config}.
     *
     * @return A new {@link PropertyCodeGenerator}, or {@link Optional#absent()} if the factory
     *     does not support this type of property.
     */
    Optional<? extends PropertyCodeGenerator> create(Config config);
  }

  protected final Metadata metadata;
  protected final Property property;

  public PropertyCodeGenerator(Metadata metadata, Property property) {
    this.metadata = metadata;
    this.property = property;
  }

  /** Property type. */
  public enum Type { REQUIRED, OPTIONAL, HAS_DEFAULT }

  /** Returns whether the property is required, optional, or has a default. */
  public Type getType() {
    return Type.HAS_DEFAULT;
  }

  /** Add the field declaration for the property to the value's source code. */
  public void addValueFieldDeclaration(SourceBuilder code, FieldAccess finalField) {
    code.addLine("private final %s %s;", property.getType(), finalField);
  }

  /** Add the field declaration for the property to the builder's source code. */
  public abstract void addBuilderFieldDeclaration(SourceBuilder code);

  /** Add the accessor methods for the property to the builder's source code. */
  public abstract void addBuilderFieldAccessors(SourceBuilder code);

  /** Add the final assignment of the property to the value object's source code. */
  public abstract void addFinalFieldAssignment(
      SourceBuilder code, Excerpt finalField, String builder);

  /** Add the final assignment of the property to the partial value object's source code. */
  public void addPartialFieldAssignment(
      SourceBuilder code, Excerpt finalField, String builder) {
    addFinalFieldAssignment(code, finalField, builder);
  }

  /** Add a merge from value for the property to the builder's source code. */
  public abstract void addMergeFromValue(Block code, String value);

  /** Add a merge from builder for the property to the builder's source code. */
  public abstract void addMergeFromBuilder(Block code, String builder);

  /** Sets the property on a builder from within a partial value's toBuilder() method. */
  public void addSetBuilderFromPartial(Block code, String builder) {
    addSetFromResult(code, Excerpts.add(builder), property.getField());
  }

  /** Adds method annotations for the value type getter method. */
  public void addGetterAnnotations(@SuppressWarnings("unused") SourceBuilder code) {}

  /** Adds a fragment converting the value object's field to the property's type. */
  public void addReadValueFragment(SourceBuilder code, Excerpt finalField) {
    code.add("%s", finalField);
  }

  /** Adds a set call for the property from a function result to the builder's source code. */
  public abstract void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable);

  /** Adds a clear call for the property given a template builder to the builder's source code. */
  public abstract void addClearField(Block code);

  /** Returns excerpts for any static types or methods added by this generator. */
  public Set<? extends StaticExcerpt> getStaticExcerpts() {
    return ImmutableSet.of();
  }

  protected void addAccessorAnnotations(SourceBuilder code) {
    for (Excerpt annotation : property.getAccessorAnnotations()) {
      code.add(annotation);
    }
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == null || !getClass().isInstance(obj)) {
      return false;
    }
    PropertyCodeGenerator other = (PropertyCodeGenerator) obj;
    return fieldValues().equals(other.fieldValues());
  }

  @Override
  public int hashCode() {
    return ImmutableList.copyOf(fieldValues().values()).hashCode();
  }

  @Override
  public String toString() {
    ToStringHelper stringHelper = MoreObjects.toStringHelper(this);
    for (Map.Entry<String, Object> fieldValue : fieldValues().entrySet()) {
      stringHelper.add(fieldValue.getKey(), fieldValue.getValue());
    }
    return stringHelper.toString();
  }

  private Map<String, Object> fieldValues() {
    ImmutableMap.Builder<String, Object> valuesBuilder = ImmutableMap.builder();
    addFieldValues(getClass(), valuesBuilder);
    return valuesBuilder.build();
  }

  private void addFieldValues(Class<?> cls, ImmutableMap.Builder<String, Object> valuesBuilder) {
    try {
      if (cls.getSuperclass() != null) {
        addFieldValues(cls.getSuperclass(), valuesBuilder);
      }
      for (Field field : cls.getDeclaredFields()) {
        field.setAccessible(true);
        valuesBuilder.put(field.getName(), field.get(this));
      }
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }
}
