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

import java.util.Set;

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

/** Property-type-specific code generation interface. */
public abstract class PropertyCodeGenerator {

  /** Data available to {@link Factory} instances when creating a {@link PropertyCodeGenerator}. */
  interface Config {
    /** Returns metadata about the property requiring code generation. */
    Metadata.Property getProperty();

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

  protected final Property property;

  public PropertyCodeGenerator(Property property) {
    this.property = property;
  }

  /** Property type. */
  public enum Type { REQUIRED, OPTIONAL, HAS_DEFAULT }

  /** Returns whether the property is required, optional, or has a default. */
  public Type getType() {
    return Type.HAS_DEFAULT;
  }

  /** Add the field declaration for the property to the value's source code. */
  public void addValueFieldDeclaration(SourceBuilder code, String finalField) {
    code.addLine("private final %s %s;", property.getType(), finalField);
  }

  /** Add the field declaration for the property to the builder's source code. */
  public abstract void addBuilderFieldDeclaration(SourceBuilder code);

  /** Add the accessor methods for the property to the builder's source code. */
  public abstract void addBuilderFieldAccessors(SourceBuilder code, Metadata metadata);

  /** Add the final assignment of the property to the value object's source code. */
  public abstract void addFinalFieldAssignment(
      SourceBuilder code, String finalField, String builder);

  /** Add the final assignment of the property to the partial value object's source code. */
  public void addPartialFieldAssignment(SourceBuilder code, String finalField, String builder) {
    addFinalFieldAssignment(code, finalField, builder);
  }

  /** Add a merge from value for the property to the builder's source code. */
  public abstract void addMergeFromValue(SourceBuilder code, String value);

  /** Add a merge from builder for the property to the builder's source code. */
  public abstract void addMergeFromBuilder(SourceBuilder code, Metadata metadata, String builder);

  /** Adds a fragment converting the value object's field to the property's type. */
  public void addReadValueFragment(SourceBuilder code, String finalField) {
    code.add("%s", finalField);
  }

  /** Adds a set call for the property from a function result to the builder's source code. */
  public abstract void addSetFromResult(SourceBuilder code, String builder, String variable);

  /** Returns true if the clear method requires a template builder to operate correctly. */
  public abstract boolean isTemplateRequiredInClear();

  /** Adds a clear call for the property given a template builder to the builder's source code. */
  public abstract void addClear(SourceBuilder code, String template);

  /** Adds a partial clear call for the property to the builder's source code. */
  public abstract void addPartialClear(SourceBuilder code);

  public static final Predicate<PropertyCodeGenerator> IS_TEMPLATE_REQUIRED_IN_CLEAR =
      new Predicate<PropertyCodeGenerator>() {
        @Override public boolean apply(PropertyCodeGenerator input) {
          return input.isTemplateRequiredInClear();
        }
      };
}
