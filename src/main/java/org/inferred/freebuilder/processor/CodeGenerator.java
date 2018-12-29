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

import static com.google.common.collect.Iterables.any;
import static org.inferred.freebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;
import static org.inferred.freebuilder.processor.Metadata.GET_CODE_GENERATOR;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.ABSENT;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.FINAL;
import static org.inferred.freebuilder.processor.ToStringGenerator.addToString;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.LazyName.addLazyDefinitions;
import static org.inferred.freebuilder.processor.util.ObjectsExcerpts.Nullability.NOT_NULLABLE;
import static org.inferred.freebuilder.processor.util.ObjectsExcerpts.Nullability.NULLABLE;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.ObjectsExcerpts;
import org.inferred.freebuilder.processor.util.ObjectsExcerpts.Nullability;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Code generation for the &#64;{@link FreeBuilder} annotation.
 */
public class CodeGenerator {

  static final FieldAccess UNSET_PROPERTIES = new FieldAccess("_unsetProperties");

  /** Write the source code for a generated builder. */
  void writeBuilderSource(SourceBuilder code, Metadata metadata) {
    if (!metadata.hasBuilder()) {
      writeStubSource(code, metadata);
      return;
    }

    addBuilderTypeDeclaration(code, metadata);
    code.addLine(" {");
    addStaticFromMethod(code, metadata);
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      addPropertyEnum(metadata, code);
    }

    addFieldDeclarations(code, metadata);

    addAccessors(metadata, code);
    addMergeFromValueMethod(code, metadata);
    addMergeFromBuilderMethod(code, metadata);
    addClearMethod(code, metadata);
    addBuildMethod(code, metadata);
    addBuildPartialMethod(code, metadata);

    addValueType(code, metadata);
    addPartialType(code, metadata);
    for (Function<Metadata, Excerpt> nestedClass : metadata.getNestedClasses()) {
      code.add(nestedClass.apply(metadata));
    }
    addLazyDefinitions(code);
    code.addLine("}");
  }

  private void addBuilderTypeDeclaration(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of %s,", metadata.getBuilder().javadocLink())
        .addLine(" * derived from the API of %s.", metadata.getType().javadocLink())
        .addLine(" */")
        .add(Excerpts.generated(getClass()));
    for (Excerpt annotation : metadata.getGeneratedBuilderAnnotations()) {
      code.add(annotation);
    }
    code.add("abstract class %s", metadata.getGeneratedBuilder().declaration());
    if (metadata.isBuilderSerializable()) {
      code.add(" implements %s", Serializable.class);
    }
  }

  private static void addStaticFromMethod(SourceBuilder code, Metadata metadata) {
    BuilderFactory builderFactory = metadata.getBuilderFactory().orNull();
    if (builderFactory == null) {
      return;
    }
    code.addLine("")
        .addLine("/**")
        .addLine(" * Creates a new builder using {@code value} as a template.")
        .addLine(" */")
        .addLine("public static %s %s from(%s value) {",
            metadata.getType().declarationParameters(),
            metadata.getBuilder(),
            metadata.getType())
        .addLine("  return %s.mergeFrom(value);",
            builderFactory.newBuilder(metadata.getBuilder(), EXPLICIT_TYPES))
        .addLine("}");
  }

  private static void addFieldDeclarations(SourceBuilder code, Metadata metadata) {
    code.addLine("");
    for (Property property : metadata.getProperties()) {
      PropertyCodeGenerator codeGenerator = property.getCodeGenerator();
      codeGenerator.addBuilderFieldDeclaration(code);
    }
    // Unset properties
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      code.addLine("private final %s<%s> %s =",
              EnumSet.class, metadata.getPropertyEnum(), UNSET_PROPERTIES)
          .addLine("    %s.allOf(%s.class);", EnumSet.class, metadata.getPropertyEnum());
    }
  }

  private static void addAccessors(Metadata metadata, SourceBuilder body) {
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addBuilderFieldAccessors(body);
    }
  }

  private static void addBuildMethod(SourceBuilder code, Metadata metadata) {
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a newly-created %s based on the contents of the {@code %s}.",
            metadata.getType().javadocLink(), metadata.getBuilder().getSimpleName());
    if (hasRequiredProperties) {
      code.addLine(" *")
          .addLine(" * @throws IllegalStateException if any field has not been set");
    }
    code.addLine(" */")
        .addLine("public %s build() {", metadata.getType());
    if (hasRequiredProperties) {
      code.add(PreconditionExcerpts.checkState(
          Excerpts.add("%s.isEmpty()", UNSET_PROPERTIES), "Not set: %s", UNSET_PROPERTIES));
    }
    code.addLine("  return %s(this);", metadata.getValueType().constructor())
        .addLine("}");
  }

  private static void addMergeFromValueMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets all property values using the given {@code %s} as a template.",
            metadata.getType().getQualifiedName())
        .addLine(" */")
        .addLine("public %s mergeFrom(%s value) {", metadata.getBuilder(), metadata.getType());
    Block body = methodBody(code, "value");
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addMergeFromValue(body, "value");
    }
    body.addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private static void addMergeFromBuilderMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Copies values from the given {@code %s}.",
            metadata.getBuilder().getSimpleName())
        .addLine(" * Does not affect any properties not set on the input.")
        .addLine(" */")
        .addLine("public %1$s mergeFrom(%1$s template) {", metadata.getBuilder());
    Block body = methodBody(code, "template");
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addMergeFromBuilder(body, "template");
    }
    body.addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private static void addClearMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Resets the state of this builder.")
        .addLine(" */")
        .addLine("public %s clear() {", metadata.getBuilder());
    Block body = methodBody(code);
    List<PropertyCodeGenerator> codeGenerators =
        Lists.transform(metadata.getProperties(), GET_CODE_GENERATOR);
    for (PropertyCodeGenerator codeGenerator : codeGenerators) {
      codeGenerator.addClearField(body);
    }
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      Optional<Excerpt> defaults = Declarations.freshBuilder(body, metadata);
      if (defaults.isPresent()) {
        body.addLine("  %s.clear();", UNSET_PROPERTIES)
            .addLine("  %s.addAll(%s);", UNSET_PROPERTIES, UNSET_PROPERTIES.on(defaults.get()));
      }
    }
    body.addLine("  return (%s) this;", metadata.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private static void addBuildPartialMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a newly-created partial %s", metadata.getType().javadocLink())
        .addLine(" * for use in unit tests. State checking will not be performed.");
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      code.addLine(" * Unset properties will throw an {@link %s}",
              UnsupportedOperationException.class)
          .addLine(" * when accessed via the partial object.");
    }
    if (metadata.getHasToBuilderMethod()
        && metadata.getBuilderFactory() == Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR)) {
      code.addLine(" *")
          .addLine(" * <p>The builder returned by a partial's {@link %s#toBuilder() toBuilder}",
              metadata.getType())
          .addLine(" * method overrides {@link %s#build() build()} to return another partial.",
              metadata.getBuilder())
          .addLine(" * This allows for robust tests of modify-rebuild code.");
    }
    code.addLine(" *")
        .addLine(" * <p>Partials should only ever be used in tests. They permit writing robust")
        .addLine(" * test cases that won't fail if this type gains more application-level")
        .addLine(" * constraints (e.g. new required fields) in future. If you require partially")
        .addLine(" * complete values in production code, consider using a Builder.")
        .addLine(" */");
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("@%s()", VisibleForTesting.class);
    }
    code.addLine("public %s buildPartial() {", metadata.getType())
        .addLine("  return %s(this);", metadata.getPartialType().constructor())
        .addLine("}");
  }

  private static void addPropertyEnum(Metadata metadata, SourceBuilder code) {
    code.addLine("")
        .addLine("private enum %s {", metadata.getPropertyEnum().getSimpleName());
    for (Property property : metadata.getProperties()) {
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("  %s(\"%s\"),", property.getAllCapsName(), property.getName());
      }
    }
    code.addLine("  ;")
        .addLine("")
        .addLine("  private final %s name;", String.class)
        .addLine("")
        .addLine("  private %s(%s name) {",
            metadata.getPropertyEnum().getSimpleName(), String.class)
        .addLine("    this.name = name;")
        .addLine("  }")
        .addLine("")
        .addLine("  @%s public %s toString() {", Override.class, String.class)
        .addLine("    return name;")
        .addLine("  }")
        .addLine("}");
  }

  private static void addValueType(SourceBuilder code, Metadata metadata) {
    code.addLine("");
    for (Excerpt annotation : metadata.getValueTypeAnnotations()) {
      code.add(annotation);
    }
    code.addLine("%s static final class %s %s {",
        metadata.getValueTypeVisibility(),
        metadata.getValueType().declaration(),
        extending(metadata.getType(), metadata.isInterfaceType()));
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getField());
    }
    // Constructor
    code.addLine("")
        .addLine("  private %s(%s builder) {",
            metadata.getValueType().getSimpleName(),
            metadata.getGeneratedBuilder());
    Block body = methodBody(code, "builder");
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addFinalFieldAssignment(body, property.getField().on("this"), "builder");
    }
    code.add(body)
        .addLine("  }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().addAccessorAnnotations(code);
      property.getCodeGenerator().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      code.add("    return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getField());
      code.add(";\n");
      code.addLine("  }");
    }
    // toBuilder
    if (metadata.getHasToBuilderMethod()) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s toBuilder() {", metadata.getBuilder());
      BuilderFactory builderFactory = metadata.getBuilderFactory().orNull();
      if (builderFactory != null) {
        code.addLine("    return %s.mergeFrom(this);",
                builderFactory.newBuilder(metadata.getBuilder(), EXPLICIT_TYPES));
      } else {
        code.addLine("    throw new %s();", UnsupportedOperationException.class);
      }
      code.addLine("  }");
    }
    // Equals
    switch (metadata.standardMethodUnderride(StandardMethod.EQUALS)) {
      case ABSENT:
        addValueTypeEquals(code, metadata);
        break;

      case OVERRIDEABLE:
        // Partial-respecting override if a non-final user implementation exists.
        code.addLine("")
            .addLine("  @%s", Override.class)
            .addLine("  public boolean equals(Object obj) {")
            .addLine("    return (!(obj instanceof %s) && super.equals(obj));",
                metadata.getPartialType().getQualifiedName())
            .addLine("  }");
        break;

      case FINAL:
        // Cannot override if a final user implementation exists.
        break;
    }
    // Hash code
    if (metadata.standardMethodUnderride(StandardMethod.HASH_CODE) == ABSENT) {
      FieldAccessList properties = getFields(metadata.getProperties());
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public int hashCode() {");
      if (code.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        code.addLine("    return %s.hash(%s);",
            code.feature(SOURCE_LEVEL).javaUtilObjects().get(), properties);
      } else {
        code.addLine("    return %s.hashCode(new Object[] { %s });", Arrays.class, properties);
      }
      code.addLine("  }");
    }
    // toString
    if (metadata.standardMethodUnderride(StandardMethod.TO_STRING) == ABSENT) {
      addToString(code, metadata, false);
    }
    code.addLine("}");
  }

  private static void addValueTypeEquals(SourceBuilder code, Metadata metadata) {
    // Default implementation if no user implementation exists.
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public boolean equals(Object obj) {");
    Block body = methodBody(code, "obj");
    body.addLine("    if (!(obj instanceof %s)) {", metadata.getValueType().getQualifiedName())
        .addLine("      return false;")
        .addLine("    }")
        .addLine("    %1$s other = (%1$s) obj;", metadata.getValueType().withWildcards());
    if (metadata.getProperties().isEmpty()) {
      body.addLine("    return true;");
    } else if (body.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
      String prefix = "    return ";
      for (Property property : metadata.getProperties()) {
        body.add(prefix);
        body.add(ObjectsExcerpts.equals(
            property.getField(),
            property.getField().on("other"),
            property.getType().getKind(),
            nullabilityOf(property, false)));
        prefix = "\n        && ";
      }
      body.add(";\n");
    } else {
      for (Property property : metadata.getProperties()) {
        body.addLine("    if (%s) {", ObjectsExcerpts.notEquals(
                property.getField(),
                property.getField().on("other"),
                property.getType().getKind(),
                nullabilityOf(property, false)))
            .addLine("      return false;")
            .addLine("    }");
      }
      body.addLine("    return true;");
    }
    code.add(body)
        .addLine("  }");
  }

  private static void addPartialType(SourceBuilder code, Metadata metadata) {
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("")
        .addLine("private static final class %s %s {",
            metadata.getPartialType().declaration(),
            extending(metadata.getType(), metadata.isInterfaceType()));
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getField());
    }
    if (hasRequiredProperties) {
      code.addLine("  private final %s<%s> %s;",
          EnumSet.class, metadata.getPropertyEnum(), UNSET_PROPERTIES);
    }
    addPartialConstructor(code, metadata, hasRequiredProperties);
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().addAccessorAnnotations(code);
      property.getCodeGenerator().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    if (%s.contains(%s.%s)) {",
                UNSET_PROPERTIES, metadata.getPropertyEnum(), property.getAllCapsName())
            .addLine("      throw new %s(\"%s not set\");",
                UnsupportedOperationException.class, property.getName())
            .addLine("    }");
      }
      code.add("    return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getField());
      code.add(";\n");
      code.addLine("  }");
    }
    addPartialToBuilderMethod(code, metadata);
    // Equals
    if (metadata.standardMethodUnderride(StandardMethod.EQUALS) != FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public boolean equals(Object obj) {");
      Block body = methodBody(code, "obj");
      body.addLine("    if (!(obj instanceof %s)) {", metadata.getPartialType().getQualifiedName())
          .addLine("      return false;")
          .addLine("    }")
          .addLine("    %1$s other = (%1$s) obj;", metadata.getPartialType().withWildcards());
      if (metadata.getProperties().isEmpty()) {
        body.addLine("    return true;");
      } else if (body.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        String prefix = "    return ";
        for (Property property : metadata.getProperties()) {
          body.add(prefix);
          body.add(ObjectsExcerpts.equals(
              property.getField(),
              property.getField().on("other"),
              property.getType().getKind(),
              nullabilityOf(property, true)));
          prefix = "\n        && ";
        }
        if (hasRequiredProperties) {
          body.add(prefix);
          body.add("%s.equals(%s, %s)",
              body.feature(SOURCE_LEVEL).javaUtilObjects().get(),
              UNSET_PROPERTIES,
              UNSET_PROPERTIES.on("other"));
        }
        body.add(";\n");
      } else {
        for (Property property : metadata.getProperties()) {
          body.addLine("    if (%s) {", ObjectsExcerpts.notEquals(
                  property.getField(),
                  property.getField().on("other"),
                  property.getType().getKind(),
                  nullabilityOf(property, true)))
              .addLine("      return false;")
              .addLine("    }");
        }
        if (hasRequiredProperties) {
          body.addLine("    return %s.equals(%s);",
              UNSET_PROPERTIES, UNSET_PROPERTIES.on("other"));
        } else {
          body.addLine("    return true;");
        }
      }
      code.add(body)
          .addLine("  }");
    }
    // Hash code
    if (metadata.standardMethodUnderride(StandardMethod.HASH_CODE) != FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public int hashCode() {");

      FieldAccessList properties = getFields(metadata.getProperties());
      if (hasRequiredProperties) {
        properties = properties.plus(UNSET_PROPERTIES);
      }

      if (code.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        code.addLine("    return %s.hash(%s);",
            code.feature(SOURCE_LEVEL).javaUtilObjects().get(), properties);
      } else {
        code.addLine("    return %s.hashCode(new Object[] { %s });", Arrays.class, properties);
      }
      code.addLine("  }");
    }
    // toString
    if (metadata.standardMethodUnderride(StandardMethod.TO_STRING) != FINAL) {
      addToString(code, metadata, true);
    }
    code.addLine("}");
  }

  private static void addPartialConstructor(
      SourceBuilder code, Metadata metadata, boolean hasRequiredProperties) {
    code.addLine("")
        .addLine("  %s(%s builder) {",
            metadata.getPartialType().getSimpleName(),
            metadata.getGeneratedBuilder());
    Block body = methodBody(code, "builder");
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addPartialFieldAssignment(body, property.getField().on("this"), "builder");
    }
    if (hasRequiredProperties) {
      body.addLine("    %s = %s.clone();",
          UNSET_PROPERTIES.on("this"), UNSET_PROPERTIES.on("builder"));
    }
    code.add(body)
        .addLine("  }");
  }

  private static void addPartialToBuilderMethod(SourceBuilder code, Metadata metadata) {
    if (!metadata.getHasToBuilderMethod()) {
      return;
    }
    if (metadata.isExtensible()) {
      code.addLine("")
          .addLine("  private static class PartialBuilder%s extends %s {",
              metadata.getType().declarationParameters(), metadata.getBuilder())
          .addLine("    @Override public %s build() {", metadata.getType())
          .addLine("      return buildPartial();")
          .addLine("    }")
          .addLine("  }");
    }
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public %s toBuilder() {", metadata.getBuilder());
    Block body = methodBody(code);
    Variable builder = new Variable("builder");
    if (metadata.isExtensible()) {
      code.addLine("    %s builder = new PartialBuilder%s();",
              metadata.getBuilder(), metadata.getBuilder().typeParametersOrDiamondOperator());
      for (Property property : metadata.getProperties()) {
        property.getCodeGenerator().addSetBuilderFromPartial(body, builder);
      }
      body.addLine("    return %s;", builder);
    } else {
      body.addLine("    throw new %s();", UnsupportedOperationException.class);
    }
    code.add(body)
        .addLine("  }");
  }

  private void writeStubSource(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.",
            metadata.getType())
        .addLine(" */")
        .add(Excerpts.generated(getClass()))
        .addLine("abstract class %s {}", metadata.getGeneratedBuilder().declaration());
  }

  private static Nullability nullabilityOf(Property property, boolean inPartial) {
    switch (property.getCodeGenerator().getType()) {
      case HAS_DEFAULT:
        return NOT_NULLABLE;

      case OPTIONAL:
        return NULLABLE;

      case REQUIRED:
        return inPartial ? NULLABLE : NOT_NULLABLE;
    }
    throw new IllegalStateException(
        "Unexpected property type " + property.getCodeGenerator().getType());
  }

  /** Returns an {@link Excerpt} of "implements/extends {@code type}". */
  private static Excerpt extending(final Object type, final boolean isInterface) {
    return Excerpts.add(isInterface ? "implements %s" : "extends %s", type);
  }

  private static class FieldAccessList extends Excerpt {
    private final List<FieldAccess> fieldAccesses;

    FieldAccessList(List<FieldAccess> fieldAccesses) {
      this.fieldAccesses = ImmutableList.copyOf(fieldAccesses);
    }

    @Override
    public void addTo(SourceBuilder source) {
      String separator = "";
      for (FieldAccess field : fieldAccesses) {
        source.add(separator).add(field);
        separator = ", ";
      }
    }

    public FieldAccessList plus(FieldAccess fieldAccess) {
      return new FieldAccessList(ImmutableList.<FieldAccess>builder()
          .addAll(fieldAccesses)
          .add(fieldAccess)
          .build());
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("fields", this.fieldAccesses);
    }
  }

  private static FieldAccessList getFields(Iterable<Property> properties) {
    ImmutableList.Builder<FieldAccess> fieldAccesses = ImmutableList.builder();
    for (Property property : properties) {
      fieldAccesses.add(property.getField());
    }
    return new FieldAccessList(fieldAccesses.build());
  }

  private static final Predicate<Property> IS_REQUIRED = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().getType() == Type.REQUIRED;
    }
  };
}
