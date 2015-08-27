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

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Metadata about a &#64;{@link org.inferred.freebuilder.FreeBuilder FreeBuilder} type.
 */
public abstract class Metadata {

  /** Standard Java methods that may be underridden. */
  public enum StandardMethod {
    TO_STRING, HASH_CODE, EQUALS
  }

  /** How compulsory the underride is. */
  public enum UnderrideLevel {
    /** There is no underride. */
    ABSENT,
    /** The underride can be overridden (viz. to respect Partials). */
    OVERRIDEABLE,
    /** The underride is declared final. */
    FINAL;
  }

  /** Returns the type itself. */
  public abstract ParameterizedType getType();

  /** Returns true if the type is an interface. */
  public abstract boolean isInterfaceType();

  abstract Optional<ParameterizedType> getOptionalBuilder();

  /**
   * Returns true if there is a user-visible Builder subclass defined.
   */
  public boolean hasBuilder() {
    return getOptionalBuilder().isPresent();
  }

  /**
   * Returns the builder type that users will see.
   *
   * @throws IllegalStateException if {@link #hasBuilder} returns false.
   */
  public ParameterizedType getBuilder() {
    return getOptionalBuilder().get();
  }

  /** Returns the builder factory mechanism the user has exposed, if any. */
  public abstract Optional<BuilderFactory> getBuilderFactory();

  /** Returns the builder class that should be generated. */
  public abstract ParameterizedType getGeneratedBuilder();

  /** Returns the value class that should be generated. */
  public abstract ParameterizedType getValueType();

  /** Returns the partial value class that should be generated. */
  public abstract ParameterizedType getPartialType();

  /**
   * Returns a set of nested types that will be visible in the generated class, either because they
   * will be generated, or because they are present in a superclass.
   */
  public abstract ImmutableSet<QualifiedName> getVisibleNestedTypes();

  /** Returns the Property enum that may be generated. */
  public abstract ParameterizedType getPropertyEnum();

  /** Returns metadata about the properties of the type. */
  public abstract ImmutableList<Property> getProperties();

  public UnderrideLevel standardMethodUnderride(StandardMethod standardMethod) {
    UnderrideLevel underrideLevel = getStandardMethodUnderrides().get(standardMethod);
    return (underrideLevel == null) ? UnderrideLevel.ABSENT : underrideLevel;
  }

  public abstract ImmutableMap<StandardMethod, UnderrideLevel> getStandardMethodUnderrides();

  /** Returns whether the builder type should be serializable. */
  public abstract boolean isBuilderSerializable();

  /** Returns whether the type (and hence the generated builder type) is GWT compatible. */
  public abstract boolean isGwtCompatible();

  /** Returns whether the type (and hence the generated value type) is GWT serializable. */
  public abstract boolean isGwtSerializable();

  /** Metadata about a property of a {@link Metadata}. */
  public interface Property {

    /** Returns the type of the property. */
    TypeMirror getType();

    /** Returns the boxed form of {@link #getType()}, or null if type is not primitive. */
    @Nullable TypeMirror getBoxedType();

    /** Returns the name of the property, e.g. myProperty. */
    String getName();

    /** Returns the capitalized name of the property, e.g. MyProperty. */
    String getCapitalizedName();

    /** Returns the name of the property in all-caps with underscores, e.g. MY_PROPERTY. */
    String getAllCapsName();

    /** Returns the name of the getter for the property, e.g. getMyProperty, or isSomethingTrue. */
    String getGetterName();

    /**
     * Returns the code generator to use for this property, or null if no generator has been picked
     * (i.e. when passed to {@link PropertyCodeGenerator.Factory#create}.
     */
    @Nullable PropertyCodeGenerator getCodeGenerator();

    /**
     * Returns true if a cast to this property type is guaranteed to be fully checked at runtime.
     * This is true for any type that is non-generic, raw, or parameterized with unbounded
     * wildcards, such as {@code Integer}, {@code List} or {@code Map<?, ?>}.
     */
    boolean isFullyCheckedCast();

    /**
     * Returns the {@code @Nullable} annotations that have been applied to this property.
     */
    ImmutableSet<TypeElement> getNullableAnnotations();

    /**
     * Returns a list of annotations that should be applied to the accessor methods of this
     * property; that is, the getter method, and a single setter method that will accept the result
     * of the getter method as its argument. For a list, for example, that would be getX() and
     * addAllX().
     */
    ImmutableList<AnnotationMirror> getAccessorAnnotations();

    /** Builder for {@link Property}. */
    class Builder extends Metadata_Property_Builder {}
  }

  public static final Function<Property, PropertyCodeGenerator> GET_CODE_GENERATOR =
      new Function<Property, PropertyCodeGenerator>() {
        @Override
        public PropertyCodeGenerator apply(Property input) {
          return input.getCodeGenerator();
        }
      };

  /** Builder for {@link Metadata}. */
  public static class Builder extends Metadata_Builder {

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
