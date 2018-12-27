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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.Optional;
import java.util.function.Function;

import javax.lang.model.type.TypeMirror;

/**
 * Metadata about a &#64;{@link org.inferred.freebuilder.FreeBuilder FreeBuilder} type.
 */
public interface Metadata {

  /** Standard Java methods that may be underridden. */
  enum StandardMethod {
    TO_STRING, HASH_CODE, EQUALS
  }

  /** How compulsory the underride is. */
  enum UnderrideLevel {
    /** There is no underride. */
    ABSENT,
    /** The underride can be overridden (viz. to respect Partials). */
    OVERRIDEABLE,
    /** The underride is declared final. */
    FINAL;
  }

  class Visibility extends Excerpt {
    public static final Visibility PUBLIC = new Visibility(0, "PUBLIC", "public ");
    public static final Visibility PROTECTED = new Visibility(1, "PROTECTED", "protected ");
    public static final Visibility PACKAGE = new Visibility(2, "PACKAGE", "");
    public static final Visibility PRIVATE = new Visibility(3, "PRIVATE", "private ");

    private final int order;
    private final String name;
    private final String excerpt;

    Visibility(int order, String name, String excerpt) {
      this.order = order;
      this.name = name;
      this.excerpt = excerpt;
    }

    public static Visibility mostVisible(Visibility a, Visibility b) {
      return (a.order < b.order) ? a : b;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.add(excerpt);
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("excerpt", excerpt);
    }
  }

  /** Returns the type itself. */
  ParameterizedType getType();

  /** Returns true if the type is an interface. */
  boolean isInterfaceType();

  Optional<ParameterizedType> getOptionalBuilder();

  /**
   * Returns true if there is a user-visible Builder subclass defined.
   */
  default boolean hasBuilder() {
    return getOptionalBuilder().isPresent();
  }

  /**
   * Returns the builder type that users will see.
   *
   * @throws IllegalStateException if {@link #hasBuilder} returns false.
   */
  default ParameterizedType getBuilder() {
    return getOptionalBuilder().get();
  }

  /** Whether there is a package-visible, no-args constructor so we can subclass the Builder. */
  boolean isExtensible();

  /** Returns the builder factory mechanism the user has exposed, if any. */
  Optional<BuilderFactory> getBuilderFactory();

  /** Returns the builder class that should be generated. */
  ParameterizedType getGeneratedBuilder();

  /** Returns the value class that should be generated. */
  ParameterizedType getValueType();

  /** Returns the partial value class that should be generated. */
  ParameterizedType getPartialType();

  /**
   * Returns a set of nested types that will be visible in the generated class, either because they
   * will be generated, or because they are present in a superclass.
   */
  ImmutableSet<QualifiedName> getVisibleNestedTypes();

  /** Returns the Property enum that may be generated. */
  ParameterizedType getPropertyEnum();

  /** Returns metadata about the properties of the type. */
  ImmutableList<Property> getProperties();

  default UnderrideLevel standardMethodUnderride(StandardMethod standardMethod) {
    UnderrideLevel underrideLevel = getStandardMethodUnderrides().get(standardMethod);
    return (underrideLevel == null) ? UnderrideLevel.ABSENT : underrideLevel;
  }

  ImmutableMap<StandardMethod, UnderrideLevel> getStandardMethodUnderrides();

  /** Returns whether the builder type should be serializable. */
  boolean isBuilderSerializable();

  /** Returns whether the value type has a toBuilder method that needs to be generated. */
  boolean getHasToBuilderMethod();

  /** Returns a list of annotations that should be applied to the generated builder class. */
  ImmutableList<Excerpt> getGeneratedBuilderAnnotations();

  /** Returns a list of annotations that should be applied to the generated value class. */
  ImmutableList<Excerpt> getValueTypeAnnotations();

  /** Returns the visibility of the generated value class. */
  Visibility getValueTypeVisibility();

  /** Returns a list of nested classes that should be added to the generated builder class. */
  ImmutableList<Function<Metadata, Excerpt>> getNestedClasses();

  Builder toBuilder();

  /** Metadata about a property of a {@link Metadata}. */
  interface Property {

    /** Returns the type of the property. */
    TypeMirror getType();

    /** Returns the boxed form of {@link #getType()}, if type is primitive. */
    Optional<TypeMirror> getBoxedType();

    /** Returns the name of the property, e.g. myProperty. */
    String getName();

    /** Returns the field name that stores the property, e.g. myProperty. */
    default FieldAccess getField() {
      return new FieldAccess(getName());
    }

    /** Returns the capitalized name of the property, e.g. MyProperty. */
    String getCapitalizedName();

    /** Returns the name of the property in all-caps with underscores, e.g. MY_PROPERTY. */
    String getAllCapsName();

    /** Returns true if getters start with "get"; setters should follow suit with "set". */
    boolean isUsingBeanConvention();

    /** Returns the name of the getter for the property, e.g. getMyProperty, or isSomethingTrue. */
    String getGetterName();

    /**
     * Returns the code generator to use for this property, if one has been picked
     * (i.e. after being passed to {@link PropertyCodeGenerator.Factory#create}.
     */
    Optional<PropertyCodeGenerator> getCodeGenerator();

    /**
     * Returns true if a cast to this property type is guaranteed to be fully checked at runtime.
     * This is true for any type that is non-generic, raw, or parameterized with unbounded
     * wildcards, such as {@code Integer}, {@code List} or {@code Map<?, ?>}.
     */
    boolean isFullyCheckedCast();

    /**
     * Returns a list of annotations that should be applied to the accessor methods of this
     * property; that is, the getter method, and a single setter method that will accept the result
     * of the getter method as its argument. For a list, for example, that would be getX() and
     * addAllX().
     */
    ImmutableList<Excerpt> getAccessorAnnotations();

    Builder toBuilder();

    /** Builder for {@link Property}. */
    class Builder extends Metadata_Property_Builder {}
  }

  Function<Property, PropertyCodeGenerator> GET_CODE_GENERATOR =
      property -> property.getCodeGenerator().orElse(null);

  /** Builder for {@link Metadata}. */
  class Builder extends Metadata_Builder {

    public Builder() {
      super.setValueTypeVisibility(Visibility.PRIVATE);
      super.setHasToBuilderMethod(false);
    }

    /**
     * Sets the value to be returned by {@link Metadata#getValueTypeVisibility()} to the most
     * visible of the current value and {@code visibility}. Will not decrease visibility.
     *
     * @return this {@code Builder} object
     * @throws NullPointerException if {@code visibility} is null
     */
    @Override
    public Builder setValueTypeVisibility(Visibility visibility) {
      return super.setValueTypeVisibility(
          Visibility.mostVisible(getValueTypeVisibility(), visibility));
    }

    /** Sets the builder class that users will see, if any. */
    public Builder setBuilder(Optional<ParameterizedType> builder) {
      return setOptionalBuilder(builder);
    }

    /** Sets the builder class that users will see. */
    public Builder setBuilder(ParameterizedType builder) {
      return setOptionalBuilder(builder);
    }

    /**
     * Returns a newly-built {@link Metadata} based on the content of the {@code Builder}.
     */
    @Override
    public Metadata build() {
      Metadata metadata = super.build();
      QualifiedName generatedBuilder = metadata.getGeneratedBuilder().getQualifiedName();
      checkState(metadata.getValueType().getQualifiedName().getEnclosingType()
              .equals(generatedBuilder),
          "%s not a nested class of %s", metadata.getValueType(), generatedBuilder);
      checkState(metadata.getPartialType().getQualifiedName().getEnclosingType()
              .equals(generatedBuilder),
          "%s not a nested class of %s", metadata.getPartialType(), generatedBuilder);
      checkState(metadata.getPropertyEnum().getQualifiedName().getEnclosingType()
              .equals(generatedBuilder),
          "%s not a nested class of %s", metadata.getPropertyEnum(), generatedBuilder);
      return metadata;
    }
  }
}
