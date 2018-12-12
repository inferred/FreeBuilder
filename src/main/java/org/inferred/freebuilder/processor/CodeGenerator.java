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
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.inferred.freebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;
import static org.inferred.freebuilder.processor.Metadata.GET_CODE_GENERATOR;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.ABSENT;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.FINAL;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.LazyName.addLazyDefinitions;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

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
    addConstantDeclarations(metadata, code);
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
            metadata.getBuilder().declarationParameters(),
            metadata.getBuilder(),
            metadata.getType())
        .addLine("  return %s.mergeFrom(value);",
            builderFactory.newBuilder(metadata.getBuilder(), EXPLICIT_TYPES))
        .addLine("}");
  }

  private static void addConstantDeclarations(Metadata metadata, SourceBuilder body) {
    if (body.feature(GUAVA).isAvailable() && metadata.getProperties().size() > 1) {
      body.addLine("")
          .addLine("private static final %1$s COMMA_JOINER = %1$s.on(\", \").skipNulls();",
              Joiner.class);
    }
  }

  private static void addFieldDeclarations(SourceBuilder code, Metadata metadata) {
    code.addLine("");
    for (Property property : metadata.getProperties()) {
      PropertyCodeGenerator codeGenerator = property.getCodeGenerator().get();
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
      property.getCodeGenerator().get().addBuilderFieldAccessors(body);
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
      property.getCodeGenerator().get().addMergeFromValue(body, "value");
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
      property.getCodeGenerator().get().addMergeFromBuilder(body, "template");
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
    Block body = new Block(code);
    List<PropertyCodeGenerator> codeGenerators =
        Lists.transform(metadata.getProperties(), GET_CODE_GENERATOR);
    for (PropertyCodeGenerator codeGenerator : codeGenerators) {
      codeGenerator.addClearField(body);
    }
    code.add(body);
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      Optional<Excerpt> defaults = Declarations.freshBuilder(body, metadata);
      if (defaults.isPresent()) {
        code.addLine("  %s.clear();", UNSET_PROPERTIES)
            .addLine("  %s.addAll(%s);", UNSET_PROPERTIES, UNSET_PROPERTIES.on(defaults.get()));
      }
    }
    code.addLine("  return (%s) this;", metadata.getBuilder())
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
      if (property.getCodeGenerator().get().getType() == Type.REQUIRED) {
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
      property.getCodeGenerator().get().addValueFieldDeclaration(code, property.getField());
    }
    // Constructor
    code.addLine("")
        .addLine("  private %s(%s builder) {",
            metadata.getValueType().getSimpleName(),
            metadata.getGeneratedBuilder());
    Block body = methodBody(code, "builder");
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().get()
          .addFinalFieldAssignment(body, property.getField().on("this"), "builder");
    }
    code.add(body)
        .addLine("  }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().get().addAccessorAnnotations(code);
      property.getCodeGenerator().get().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      code.add("    return ");
      property.getCodeGenerator().get().addReadValueFragment(code, property.getField());
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
          .addLine("  public int hashCode() {")
          .addLine("    return %s.hash(%s);", Objects.class, properties)
          .addLine("  }");
    }
    // toString
    if (metadata.standardMethodUnderride(StandardMethod.TO_STRING) == ABSENT) {
      addValueTypeToString(code, metadata);
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
    } else {
      String prefix = "    return ";
      for (Property property : metadata.getProperties()) {
        body.add(prefix);
        body.add(ObjectsExcerpts.equals(
            property.getField(),
            property.getField().on("other"),
            property.getType().getKind()));
        prefix = "\n        && ";
      }
      body.add(";\n");
    }
    code.add(body)
        .addLine("  }");
  }

  private static void addValueTypeToString(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public %s toString() {", String.class);
    Block body = methodBody(code);
    switch (metadata.getProperties().size()) {
      case 0: {
        body.addLine("    return \"%s{}\";", metadata.getType().getSimpleName());
        break;
      }

      case 1: {
        body.add("    return \"%s{", metadata.getType().getSimpleName());
        Property property = getOnlyElement(metadata.getProperties());
        if (property.getCodeGenerator().get().getType() == Type.OPTIONAL) {
          body.add("\" + (%1$s != null ? \"%2$s=\" + %1$s : \"\") + \"}\";\n",
              property.getField(), property.getName());
        } else {
          body.add("%s=\" + %s + \"}\";\n", property.getName(), property.getField());
        }
        break;
      }

      default: {
        if (!any(metadata.getProperties(), IS_OPTIONAL)) {
          // If none of the properties are optional, use string concatenation for performance.
          body.addLine("    return \"%s{\"", metadata.getType().getSimpleName());
          Property lastProperty = getLast(metadata.getProperties());
          for (Property property : metadata.getProperties()) {
            body.add("        + \"%s=\" + %s", property.getName(), property.getField());
            if (property != lastProperty) {
              body.add(" + \", \"\n");
            } else {
              body.add(" + \"}\";\n");
            }
          }
        } else if (body.feature(GUAVA).isAvailable()) {
          // If Guava is available, use COMMA_JOINER for readability.
          body.addLine("    return \"%s{\"", metadata.getType().getSimpleName())
              .addLine("        + COMMA_JOINER.join(");
          Property lastProperty = getLast(metadata.getProperties());
          for (Property property : metadata.getProperties()) {
            body.add("            ");
            if (property.getCodeGenerator().get().getType() == Type.OPTIONAL) {
              body.add("(%s != null ? ", property.getField());
            }
            body.add("\"%s=\" + %s", property.getName(), property.getField());
            if (property.getCodeGenerator().get().getType() == Type.OPTIONAL) {
              body.add(" : null)");
            }
            if (property != lastProperty) {
              body.add(",\n");
            } else {
              body.add(")\n");
            }
          }
          body.addLine("        + \"}\";");
        } else {
          // Use StringBuilder if no better choice is available.
          writeToStringWithBuilder(body, metadata, false);
        }
        break;
      }
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
      property.getCodeGenerator().get().addValueFieldDeclaration(code, property.getField());
    }
    if (hasRequiredProperties) {
      code.addLine("  private final %s<%s> %s;",
          EnumSet.class, metadata.getPropertyEnum(), UNSET_PROPERTIES);
    }
    // Constructor
    code.addLine("")
        .addLine("  %s(%s builder) {",
            metadata.getPartialType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().get()
          .addPartialFieldAssignment(code, property.getField().on("this"), "builder");
    }
    if (hasRequiredProperties) {
      code.addLine("    %s = %s.clone();",
          UNSET_PROPERTIES.on("this"), UNSET_PROPERTIES.on("builder"));
    }
    code.addLine("  }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().get().addAccessorAnnotations(code);
      property.getCodeGenerator().get().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      if (property.getCodeGenerator().get().getType() == Type.REQUIRED) {
        code.addLine("    if (%s.contains(%s.%s)) {",
                UNSET_PROPERTIES, metadata.getPropertyEnum(), property.getAllCapsName())
            .addLine("      throw new %s(\"%s not set\");",
                UnsupportedOperationException.class, property.getName())
            .addLine("    }");
      }
      code.add("    return ");
      property.getCodeGenerator().get().addReadValueFragment(code, property.getField());
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
      } else {
        String prefix = "    return ";
        for (Property property : metadata.getProperties()) {
          body.add(prefix);
          body.add(ObjectsExcerpts.equals(
              property.getField(),
              property.getField().on("other"),
              property.getType().getKind()));
          prefix = "\n        && ";
        }
        if (hasRequiredProperties) {
          body.add(prefix);
          body.add("%s.equals(%s, %s)",
              Objects.class,
              UNSET_PROPERTIES,
              UNSET_PROPERTIES.on("other"));
        }
        body.add(";\n");
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
      code.addLine("    return %s.hash(%s);", Objects.class, properties)
          .addLine("  }");
    }
    // toString
    if (metadata.standardMethodUnderride(StandardMethod.TO_STRING) != FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s toString() {", String.class);
      Block body = methodBody(code);
      if (metadata.getProperties().size() > 1 && !body.feature(GUAVA).isAvailable()) {
        writeToStringWithBuilder(body, metadata, true);
      } else {
        writePartialToStringWithConcatenation(body, metadata);
      }
      code.add(body)
          .addLine("  }");
    }
    code.addLine("}");
  }

  private static void addPartialToBuilderMethod(SourceBuilder code, Metadata metadata) {
    if (!metadata.getHasToBuilderMethod()) {
      return;
    }
    if (metadata.isExtensible()) {
      code.addLine("")
          .addLine("  private static class PartialBuilder%s extends %s {",
              metadata.getBuilder().declarationParameters(), metadata.getBuilder())
          .addLine("    @Override public %s build() {", metadata.getType())
          .addLine("      return buildPartial();")
          .addLine("    }")
          .addLine("  }");
    }
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public %s toBuilder() {", metadata.getBuilder());
    if (metadata.isExtensible()) {
      code.addLine("    %s builder = new PartialBuilder%s();",
              metadata.getBuilder(), metadata.getBuilder().typeParametersOrDiamondOperator());
      Block block = new Block(code);
      for (Property property : metadata.getProperties()) {
        property.getCodeGenerator().get().addSetBuilderFromPartial(block, "builder");
      }
      code.add(block)
          .addLine("    return builder;");
    } else {
      code.addLine("    throw new %s();", UnsupportedOperationException.class);
    }
    code.addLine("  }");
  }

  private static void writeToStringWithBuilder(Block code, Metadata metadata, boolean isPartial) {
    Excerpt result = code.declare(
        Excerpts.add("%s", StringBuilder.class),
        "result",
        Excerpts.add("new %s(\"%s%s{\")",
            StringBuilder.class, isPartial ? "partial " : "", metadata.getType().getSimpleName()));
    boolean noDefaults = !any(metadata.getProperties(), HAS_DEFAULT);
    Excerpt separator = null;
    if (noDefaults) {
      // We need to keep track of whether to output a separator
      separator = code.declare(Excerpts.add("String"), "separator", Excerpts.add("\"\""));
    }
    boolean seenDefault = false;
    Property first = metadata.getProperties().get(0);
    Property last = Iterables.getLast(metadata.getProperties());
    for (Property property : metadata.getProperties()) {
      boolean hadSeenDefault = seenDefault;
      switch (property.getCodeGenerator().get().getType()) {
        case HAS_DEFAULT:
          seenDefault = true;
          break;

        case OPTIONAL:
          code.addLine("if (%s != null) {", property.getField());
          break;

        case REQUIRED:
          if (isPartial) {
            code.addLine("if (!%s.contains(%s.%s)) {",
                    UNSET_PROPERTIES, metadata.getPropertyEnum(), property.getAllCapsName());
          }
          break;
      }
      if (noDefaults && property != first) {
        code.addLine("%s.append(%s);", result, separator);
      } else if (!noDefaults && hadSeenDefault) {
        code.addLine("%s.append(\", \");", result);
      }
      code.addLine("%s.append(\"%s=\").append(%s);",
          result, property.getName(), property.getField());
      if (!noDefaults && !seenDefault) {
        code.addLine("%s.append(\", \");", result);
      } else if (noDefaults && property != last) {
        code.addLine("%s = \", \";", separator);
      }
      switch (property.getCodeGenerator().get().getType()) {
        case HAS_DEFAULT:
          break;

        case OPTIONAL:
          code.addLine("}");
          break;

        case REQUIRED:
          if (isPartial) {
            code.addLine("}");
          }
          break;
      }
    }
    code.addLine("%s.append(\"}\");", result)
        .addLine("return %s.toString();", result);
  }

  private static void writePartialToStringWithConcatenation(SourceBuilder code, Metadata metadata) {
    code.add("    return \"partial %s{", metadata.getType().getSimpleName());
    switch (metadata.getProperties().size()) {
      case 0: {
        code.add("}\";\n");
        break;
      }

      case 1: {
        Property property = getOnlyElement(metadata.getProperties());
        switch (property.getCodeGenerator().get().getType()) {
          case HAS_DEFAULT:
            code.add("%s=\" + %s + \"}\";\n", property.getName(), property.getField());
            break;

          case OPTIONAL:
            code.add("\"\n")
                .addLine("        + (%1$s != null ? \"%2$s=\" + %1$s : \"\")",
                    property.getField(), property.getName())
                .addLine("        + \"}\";");
            break;

          case REQUIRED:
            code.add("\"\n")
                .addLine("        + (!%s.contains(%s.%s)",
                    UNSET_PROPERTIES, metadata.getPropertyEnum(), property.getAllCapsName())
                .addLine("            ? \"%s=\" + %s : \"\")",
                    property.getName(), property.getField())
                .addLine("        + \"}\";");
            break;
        }
        break;
      }

      default: {
        code.add("\"\n")
            .add("        + COMMA_JOINER.join(\n");
        Property lastProperty = getLast(metadata.getProperties());
        for (Property property : metadata.getProperties()) {
          code.add("            ");
          switch (property.getCodeGenerator().get().getType()) {
            case HAS_DEFAULT:
              code.add("\"%s=\" + %s", property.getName(), property.getField());
              break;

            case OPTIONAL:
              code.add("(%1$s != null ? \"%2$s=\" + %1$s : null)",
                  property.getField(), property.getName());
              break;

            case REQUIRED:
              code.add("(!%s.contains(%s.%s)",
                      UNSET_PROPERTIES, metadata.getPropertyEnum(), property.getAllCapsName())
                  .add(" ? \"%s=\" + %s : null)", property.getName(), property.getField());
              break;
          }
          if (property != lastProperty) {
            code.add(",\n");
          } else {
            code.add(")\n");
          }
        }
        code.addLine("        + \"}\";");
        break;
      }
    }
  }

  private void writeStubSource(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.",
            metadata.getType())
        .addLine(" */")
        .add(Excerpts.generated(getClass()))
        .addLine("abstract class %s {}", metadata.getGeneratedBuilder().declaration());
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
      return property.getCodeGenerator().get().getType() == Type.REQUIRED;
    }
  };

  private static final Predicate<Property> IS_OPTIONAL = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().get().getType() == Type.OPTIONAL;
    }
  };

  private static final Predicate<Property> HAS_DEFAULT = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().get().getType() == Type.HAS_DEFAULT;
    }
  };
}
