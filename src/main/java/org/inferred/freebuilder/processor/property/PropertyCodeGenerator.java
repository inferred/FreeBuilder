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
package org.inferred.freebuilder.processor.property;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.Variable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Property-type-specific code generation interface. */
public abstract class PropertyCodeGenerator {

  /** Data available to {@link Factory} instances when creating a {@link PropertyCodeGenerator}. */
  public interface Config {
    /** Returns the element this property was inferred from. */
    ExecutableElement getSourceElement();

    /** Returns datatype about the builder being generated. */
    Datatype getDatatype();

    /** Returns datatype about the property requiring code generation. */
    Property getProperty();

    /** Returns annotations on the property requiring code generation. */
    List<? extends AnnotationMirror> getAnnotations();

    /**
     * The user's Builder type. If generic, will be parameterized with the type variables of the
     * value type, for simpler type comparisons.
     */
    DeclaredType getBuilder();

    /**
     * A set of methods that are definitely invoked in the builder constructor. This may have false
     * negatives (e.g. if method introspection has not been implemented for the current compiler),
     * so must only be used for making optimizations.
     */
    Set<String> getMethodsInvokedInBuilderConstructor();

    /** The compiler's {@link ProcessingEnvironment} implementation. */
    ProcessingEnvironment getEnvironment();

    /** The compiler's {@link Elements} implementation. */
    Elements getElements();

    /** The compiler's {@link Types} implementation. */
    Types getTypes();
  }

  /** Factory interface for {@link PropertyCodeGenerator}. */
  public interface Factory {
    /**
     * Create a new {@link PropertyCodeGenerator} for the property described in {@code config}.
     *
     * @return A new {@link PropertyCodeGenerator}, or {@link Optional#absent()} if the factory
     *     does not support this type of property.
     */
    Optional<? extends PropertyCodeGenerator> create(Config config);
  }

  protected final Datatype datatype;
  protected final Property property;

  public PropertyCodeGenerator(Datatype datatype, Property property) {
    this.datatype = datatype;
    this.property = property;
  }

  /** General behaviour type for a fresh or reset property. */
  public enum Initially {

    /**
     * The property must have a value set before build can be called.
     *
     * <p>This may simply mean we have not detected the property being set in the builder's
     * constructor. The property's field may be null in the builder or on a partial, but will never
     * be null on a value instance.
     */
    REQUIRED,

    /**
     * The property need not be set.
     *
     * <p>This may mean the user chose an explicit Optional type (e.g. Java 8's Optional), or it
     * may mean a Nullable annotation has been spotted. The property's field may be null.
     */
    OPTIONAL,

    /**
     * The property is known to have a default value.
     *
     * <p>This may be because we detected the property being set in the builder's constructor,
     * or because the type itself has a reasonable default (e.g. an empty collection). The
     * property's field will never be null.
     */
    HAS_DEFAULT
  }

  /** Returns whether the property is required, optional, or has a default. */
  public Initially initialState() {
    return Initially.HAS_DEFAULT;
  }

  /** Add the field declaration for the property to the value's source code. */
  public abstract void addValueFieldDeclaration(SourceBuilder code);

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

  /** Adds an assignment to the field on the builder from the Value/Partial implementation. */
  public abstract void addAssignToBuilder(SourceBuilder code, Variable builder);

  /** Add a merge from value for the property to the builder's source code. */
  public abstract void addMergeFromValue(SourceBuilder code, String value);

  /** Add a merge from builder for the property to the builder's source code. */
  public abstract void addMergeFromBuilder(SourceBuilder code, String builder);

  /** Returns the actions taken in mergeFrom, for generating JavaDoc. */
  public abstract Set<MergeAction> getMergeActions();

  /** Adds method annotations for the value type getter method. */
  public void addGetterAnnotations(SourceBuilder code) {
    for (Excerpt annotation : property.getGetterAnnotations()) {
      code.add(annotation);
    }
  }

  /** Adds a fragment converting the value object's field to the property's type. */
  public void addReadValueFragment(SourceBuilder code, Excerpt finalField) {
    code.add("%s", finalField);
  }

  /** Adds a set call for the property from a function result to the builder's source code. */
  public abstract void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable);

  /** Adds a clear call for the property given a template builder to the builder's source code. */
  public abstract void addClearField(SourceBuilder code);

  /**
   * Adds condition statement for an initially optional property to be included in the toString
   * output for the Value/Partial types.
   *
   * @throws IllegalStateException if {@link #initialState()} is not {@link Initially#OPTIONAL}
   */
  public void addToStringCondition(SourceBuilder code) {
    checkState(initialState() == Initially.OPTIONAL);
    code.add("%s != null", property.getField());
  }

  /**
   * Adds value to an ongoing toString concatenation or append sequence.
   */
  public void addToStringValue(SourceBuilder code) {
    code.add(property.getField());
  }

  public void addAccessorAnnotations(SourceBuilder code) {
    for (Excerpt annotation : property.getAccessorAnnotations()) {
      code.add(annotation);
    }
  }

  public void addPutAnnotations(SourceBuilder code) {
    for (Excerpt annotation : property.getPutAnnotations()) {
      code.add(annotation);
    }
  }

  @Override
  public boolean equals(Object obj) {
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
