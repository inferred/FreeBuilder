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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.addAll;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import org.inferred.freebuilder.processor.util.TypeReference;
import org.inferred.freebuilder.processor.util.ValueType;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Metadata about a &#64;{@link org.inferred.freebuilder.FreeBuilder FreeBuilder} type.
 */
public class Metadata extends ValueType {

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

  private final Elements elements;
  private final TypeElement type;
  @Nullable private final TypeElement builder;
  @Nullable private final BuilderFactory builderFactory;
  private final TypeReference generatedBuilder;
  private final TypeReference valueType;
  private final TypeReference partialType;
  private final TypeReference propertyEnum;
  private final ImmutableList<Property> properties;
  private final ImmutableMap<StandardMethod, UnderrideLevel> standardMethodUnderrides;
  private final boolean builderSerializable;
  private final boolean gwtCompatible;
  private final boolean gwtSerializable;

  private Metadata(Builder builder) {
    this.elements = builder.elements;
    this.type = builder.type;
    this.builder = builder.builder;
    this.builderFactory = builder.builderFactory;
    this.generatedBuilder = builder.generatedBuilder;
    this.valueType = builder.valueType;
    this.partialType = builder.partialType;
    this.propertyEnum = builder.propertyEnum;
    this.properties = ImmutableList.copyOf(builder.properties);
    this.standardMethodUnderrides = ImmutableMap.copyOf(builder.standardMethodUnderrides);
    this.builderSerializable = builder.builderSerializable;
    this.gwtCompatible = builder.gwtCompatible;
    this.gwtSerializable = builder.gwtSerializable;
  }

  /** Returns the package the type is in. */
  public PackageElement getPackage() {
    return elements.getPackageOf(type);
  }

  /** Returns the type itself. */
  public TypeElement getType() {
    return type;
  }

  /**
   * Returns true if there is a user-visible Builder subclass defined.
   */
  public boolean hasBuilder() {
    return (builder != null);
  }

  /**
   * Returns the builder type that users will see.
   *
   * @throws IllegalStateException if {@link #hasBuilder} returns false.
   */
  public TypeElement getBuilder() {
    checkState(hasBuilder());
    return builder;
  }

  /** Returns the builder factory mechanism the user has exposed, if any. */
  public Optional<BuilderFactory> getBuilderFactory() {
    return Optional.fromNullable(builderFactory);
  }

  /** Returns the builder class that should be generated. */
  public TypeReference getGeneratedBuilder() {
    return generatedBuilder;
  }

  /** Returns the value class that should be generated. */
  public TypeReference getValueType() {
    return valueType;
  }

  /** Returns the partial value class that should be generated. */
  public TypeReference getPartialType() {
    return partialType;
  }

  /** Returns the Property enum that may be generated. */
  public TypeReference getPropertyEnum() {
    return propertyEnum;
  }

  /** Returns metadata about the properies of the type. */
  public ImmutableList<Property> getProperties() {
    return properties;
  }

  public UnderrideLevel standardMethodUnderride(StandardMethod standardMethod) {
    return standardMethodUnderrides.get(standardMethod);
  }

  public ImmutableMap<StandardMethod, UnderrideLevel> getStandardMethodUnderrides() {
    return standardMethodUnderrides;
  }

  /** Returns whether the builder type should be serializable. */
  public boolean isBuilderSerializable() {
    return builderSerializable;
  }

  /** Returns whether the type (and hence the generated builder type) is GWT compatible. */
  public boolean isGwtCompatible() {
    return gwtCompatible;
  }

  /** Returns whether the type (and hence the generated value type) is GWT serializable. */
  public boolean isGwtSerializable() {
    return gwtSerializable;
  }

  /** Metadata about a property of a {@link Metadata}. */
  public static class Property extends ValueType {
    private final TypeMirror type;
    private final TypeMirror boxedType;
    private final String name;
    private final String capitalizedName;
    private final String getterName;
    private final String allCapsName;
    private final PropertyCodeGenerator codeGenerator;
    private final boolean fullyCheckedCast;
    private final ImmutableSet<TypeElement> nullableAnnotations;

    private Property(Builder builder) {
      this.type = builder.type;
      this.boxedType = builder.boxedType;
      this.name = builder.name;
      this.capitalizedName = builder.capitalizedName;
      this.allCapsName = builder.allCapsName;
      this.getterName = builder.getterName;
      this.codeGenerator = builder.codeGenerator;
      this.fullyCheckedCast = builder.fullyCheckedCast;
      this.nullableAnnotations = ImmutableSet.copyOf(builder.nullableAnnotations);
    }

    /** Returns the type of the property. */
    public TypeMirror getType() {
      return type;
    }

    /** Returns the boxed form of {@link #getType()}, or null if type is not primitive. */
    public TypeMirror getBoxedType() {
      return boxedType;
    }

    /** Returns the name of the property, e.g. myProperty. */
    public String getName() {
      return name;
    }

    /** Returns the capitalized name of the property, e.g. MyProperty. */
    public String getCapitalizedName() {
      return capitalizedName;
    }

    /** Returns the name of the property in all-caps with underscores, e.g. MY_PROPERTY. */
    public String getAllCapsName() {
      return allCapsName;
    }

    /** Returns the name of the getter for the property, e.g. getMyProperty, or isSomethingTrue. */
    public String getGetterName() {
      return getterName;
    }

    /**
     * Returns the code generator to use for this property, or null if no generator has been picked
     * (i.e. when passed to {@link PropertyCodeGenerator.Factory#create}.
     */
    public PropertyCodeGenerator getCodeGenerator() {
      return codeGenerator;
    }

    /**
     * Returns true if a cast to this property type is guaranteed to be fully checked at runtime.
     * This is true for any type that is non-generic, raw, or parameterized with unbounded
     * wildcards, such as {@code Integer}, {@code List} or {@code Map<?, ?>}.
     */
    public boolean isFullyCheckedCast() {
      return fullyCheckedCast;
    }

    /**
     * Returns the {@code @Nullable} annotations that have been applied to this property.
     */
    public ImmutableSet<TypeElement> getNullableAnnotations() {
      return nullableAnnotations;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("type", type.toString());
      fields.add("boxedType", boxedType.toString());
      fields.add("name", name);
      fields.add("capitalizedName", capitalizedName);
      fields.add("getterName", getterName);
      fields.add("allCapsName", allCapsName);
      fields.add("codeGenerator", codeGenerator);
      fields.add("fullyCheckedCast", fullyCheckedCast);
      fields.add("nullableAnnotations", nullableAnnotations);
    }

    /** Builder for {@link Property}. */
    public static class Builder {
      private TypeMirror type;
      private TypeMirror boxedType;
      private String name;
      private String capitalizedName;
      private String getterName;
      private String allCapsName;
      private PropertyCodeGenerator codeGenerator;
      private Boolean fullyCheckedCast;
      private final Set<TypeElement> nullableAnnotations =
          new LinkedHashSet<TypeElement>();

      /** Sets the type of the property. */
      public Builder setType(TypeMirror type) {
        this.type = type;
        return this;
      }

      /** Sets the boxed type of the property (null if the original type is not primitive). */
      public Builder setBoxedType(TypeMirror type) {
        this.boxedType = type;
        return this;
      }

      /** Sets the name of the property. */
      public Builder setName(String name) {
        this.name = name;
        return this;
      }

      /** Sets the capitalized name of the property. */
      public Builder setCapitalizedName(String capitalizedName) {
        this.capitalizedName = capitalizedName;
        return this;
      }

      /** Sets the all-caps name of the property. */
      public Builder setAllCapsName(String allCapsName) {
        this.allCapsName = allCapsName;
        return this;
      }

      /** Sets the name of the getter for the property. */
      public Builder setGetterName(String getterName) {
        this.getterName = getterName;
        return this;
      }

      /** Sets the code generator to use for this property. */
      public Builder setCodeGenerator(PropertyCodeGenerator codeGenerator) {
        this.codeGenerator = codeGenerator;
        return this;
      }

      /**
       * Sets whether a cast to this property type is guaranteed to be fully checked by the
       * compiler.
       */
      public Builder setFullyCheckedCast(Boolean fullyCheckedCast) {
        this.fullyCheckedCast = checkNotNull(fullyCheckedCast);
        return this;
      }

      /**
       * Adds {@code @Nullable} annotations that have been applied to this property.
       */
      public Builder addAllNullableAnnotations(
          Iterable<? extends TypeElement> nullableAnnotations) {
          addAll(this.nullableAnnotations, nullableAnnotations);
          return this;
      }

      /** Returns a newly-built {@link Property} based on the content of the {@code Builder}. */
      public Property build() {
        checkState(type != null, "type not set");
        checkState(name != null, "name not set");
        checkState(capitalizedName != null, "capitalized not set");
        checkState(allCapsName != null, "allCapsName not set");
        checkState(getterName != null, "getter name not set");
        checkState(fullyCheckedCast != null, "fullyCheckedCast not set");
        // codeGenerator may be null
        return new Property(this);
      }
    }

    public static final Function<Property, PropertyCodeGenerator> GET_CODE_GENERATOR =
        new Function<Property, PropertyCodeGenerator>() {
          @Override
          public PropertyCodeGenerator apply(Property input) {
            return input.getCodeGenerator();
          }
        };
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("type", type.toString());
    fields.add("builder", hasBuilder() ? builder.toString() : null);
    fields.add("builderFactory", builderFactory);
    fields.add("generatedBuilder", generatedBuilder.toString());
    fields.add("valueType", valueType.toString());
    fields.add("partialType", partialType.toString());
    fields.add("propertyEnum", propertyEnum.toString());
    fields.add("properties", properties);
    fields.add("standardMethodUnderrides", standardMethodUnderrides);
    fields.add("builderSerializable", builderSerializable);
    fields.add("gwtCompatible", gwtCompatible);
    fields.add("gwtSerializable", gwtSerializable);
  }

  /** Builder for {@link Metadata}. */
  public static class Builder {

    private final Elements elements;
    private TypeElement type;
    private TypeElement builder;
    private BuilderFactory builderFactory;
    private TypeReference generatedBuilder;
    public TypeReference valueType;
    public TypeReference partialType;
    public TypeReference propertyEnum;
    private final List<Property> properties = new ArrayList<Property>();
    private final Map<StandardMethod, UnderrideLevel> standardMethodUnderrides = noUnderrides();
    private Boolean builderSerializable;
    private Boolean gwtCompatible;
    private Boolean gwtSerializable;

    public Builder(Elements elements) {
      this.elements = checkNotNull(elements);
    }

    /** Sets the type the metadata object being built is referring to. */
    public Builder setType(TypeElement type) {
      this.type = checkNotNull(type);
      return this;
    }

    /** Sets the builder class that users will see, if any. */
    public Builder setBuilder(Optional<TypeElement> builder) {
      this.builder = builder.orNull();
      return this;
    }

    /** Sets the builder class that users will see. */
    public Builder setBuilder(TypeElement builder) {
      this.builder = checkNotNull(builder);
      return this;
    }

    /** Sets the builder factory mechanism the user has exposed. */
    public Builder setBuilderFactory(BuilderFactory builderFactory) {
      this.builderFactory = checkNotNull(builderFactory);
      return this;
    }

    /** Sets the builder factory mechanism the user has exposed, if any. */
    public Builder setBuilderFactory(Optional<BuilderFactory> builderFactory) {
      this.builderFactory = builderFactory.orNull();
      return this;
    }

    /** Sets the builder class that should be generated. */
    public Builder setGeneratedBuilder(TypeReference generatedBuilder) {
      this.generatedBuilder = checkNotNull(generatedBuilder);
      return this;
    }

    /** Sets the value type that should be generated. */
    public Builder setValueType(TypeReference valueType) {
      this.valueType = valueType;
      return this;
    }

    /** Sets the partial type that should be generated. */
    public Builder setPartialType(TypeReference partialType) {
      this.partialType = partialType;
      return this;
    }

    /** Sets the property enum that may be generated.  */
    public Builder setPropertyEnum(TypeReference propertyEnum) {
      this.propertyEnum = propertyEnum;
      return this;
    }

    /** Adds metadata about a property of the type. */
    public Builder addProperty(Property property) {
      this.properties.add(property);
      return this;
    }

    /** Adds metadata about a set of properties of the type. */
    public Builder addAllProperties(Iterable<Property> properties) {
      addAll(this.properties, properties);
      return this;
    }

    /** Puts an underridden standard method into the map. */
    public Builder putStandardMethodUnderride(
        StandardMethod standardMethod, UnderrideLevel underrideLevel) {
      this.standardMethodUnderrides.put(standardMethod, underrideLevel);
      return this;
    }

    /** Copies all the entries from a map of underridden standard methods. */
    public Builder putAllStandardMethodUnderrides(
        Map<? extends StandardMethod, ? extends UnderrideLevel> standardMethodUnderrides) {
      this.standardMethodUnderrides.putAll(standardMethodUnderrides);
      return this;
    }

    /** Sets whether the generated builder should be serializable. */
    public Builder setBuilderSerializable(boolean builderSerializable) {
      this.builderSerializable = builderSerializable;
      return this;
    }

    /** Sets whether the type (and hence the generated builder type) is GWT compatible. */
    public Builder setGwtCompatible(boolean gwtCompatible) {
      this.gwtCompatible = gwtCompatible;
      return this;
    }

    /** Sets whether the type (and hence the generated value type) is GWT serializable. */
    public Builder setGwtSerializable(boolean gwtSerializable) {
      this.gwtSerializable = gwtSerializable;
      return this;
    }

    /**
     * Returns a newly-built {@link Metadata} based on the content of the {@code Builder}.
     */
    public Metadata build() {
      checkState(generatedBuilder != null, "generatedBuilder not set");
      checkState(type != null, "type not set");
      checkState(valueType != null, "valueType not set");
      checkState(valueType.getEnclosingType().equals(generatedBuilder),
          "%s not a nested class of %s", valueType, generatedBuilder);
      checkState(partialType != null, "partialType not set");
      checkState(partialType.getEnclosingType().equals(generatedBuilder),
          "%s not a nested class of %s", partialType, generatedBuilder);
      checkState(propertyEnum != null, "propertyEnum not set");
      checkState(propertyEnum.getEnclosingType().equals(generatedBuilder),
          "%s not a nested class of %s", propertyEnum, generatedBuilder);
      checkState(builderSerializable != null, "builderSerializable not set");
      checkState(gwtCompatible != null, "gwtCompatible not set");
      checkState(gwtSerializable != null, "gwtSerializable not set");
      return new Metadata(this);
    }

    private static Map<StandardMethod, UnderrideLevel> noUnderrides() {
      Map<StandardMethod, UnderrideLevel> map =
          new EnumMap<StandardMethod, UnderrideLevel>(StandardMethod.class);
      for (StandardMethod standardMethod : StandardMethod.values()) {
        map.put(standardMethod, UnderrideLevel.ABSENT);
      }
      return map;
    }
  }
}
