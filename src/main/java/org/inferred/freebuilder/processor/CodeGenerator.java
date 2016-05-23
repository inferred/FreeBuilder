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
import static org.inferred.freebuilder.processor.PropertyCodeGenerator.IS_TEMPLATE_REQUIRED_IN_CLEAR;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.BuilderFactory.TypeInference;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Generated;

/**
 * Code generation for the &#64;{@link FreeBuilder} annotation.
 */
public class CodeGenerator {

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
    addStaticMethods(code, metadata);
    code.addLine("}");
  }

  private void addBuilderTypeDeclaration(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of %s,", metadata.getBuilder().javadocLink())
        .addLine(" * derived from the API of %s.", metadata.getType().javadocLink())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName());
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
      PropertyCodeGenerator codeGenerator = property.getCodeGenerator();
      codeGenerator.addBuilderFieldDeclaration(code);
    }
    // Unset properties
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      code.addLine("private final %s<%s> _unsetProperties =",
              EnumSet.class, metadata.getPropertyEnum())
          .addLine("    %s.allOf(%s.class);", EnumSet.class, metadata.getPropertyEnum());
    }
  }

  private static void addAccessors(Metadata metadata, SourceBuilder body) {
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addBuilderFieldAccessors(body, metadata);
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
          "_unsetProperties.isEmpty()", "Not set: %s", "_unsetProperties"));
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
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addMergeFromValue(code, "value");
    }
    code.add("  return (%s) this;\n", metadata.getBuilder());
    code.addLine("}");
  }

  private static void addMergeFromBuilderMethod(SourceBuilder code, Metadata metadata) {
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("")
        .addLine("/**")
        .addLine(" * Copies values from the given {@code %s}.",
            metadata.getBuilder().getSimpleName());
    if (hasRequiredProperties) {
      code.addLine(" * Does not affect any properties not set on the input.");
    }
    code.addLine(" */")
        .addLine("public %1$s mergeFrom(%1$s template) {", metadata.getBuilder());
    if (hasRequiredProperties) {
      code.addLine("  // Upcast to access the private _unsetProperties field.")
          .addLine("  // Otherwise, oddly, we get an access violation.")
          .addLine("  %s<%s> _templateUnset = ((%s) template)._unsetProperties;",
              EnumSet.class,
              metadata.getPropertyEnum(),
              metadata.getGeneratedBuilder());
    }
    for (Property property : metadata.getProperties()) {
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("  if (!_templateUnset.contains(%s.%s)) {",
            metadata.getPropertyEnum(), property.getAllCapsName());
        property.getCodeGenerator().addMergeFromBuilder(code, metadata, "template");
        code.addLine("  }");
      } else {
        property.getCodeGenerator().addMergeFromBuilder(code, metadata, "template");
      }
    }
    code.addLine("  return (%s) this;", metadata.getBuilder());
    code.addLine("}");
  }

  private static void addClearMethod(SourceBuilder code, Metadata metadata) {
    if (metadata.getBuilderFactory().isPresent()) {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Resets the state of this builder.")
          .addLine(" */")
          .addLine("public %s clear() {", metadata.getBuilder());
      List<PropertyCodeGenerator> codeGenerators =
          Lists.transform(metadata.getProperties(), GET_CODE_GENERATOR);
      if (Iterables.any(codeGenerators, IS_TEMPLATE_REQUIRED_IN_CLEAR)) {
        code.addLine("  %s _template = %s;",
            metadata.getGeneratedBuilder(),
            metadata.getBuilderFactory().get().newBuilder(metadata.getBuilder(), TypeInference.INFERRED_TYPES));
      }
      for (PropertyCodeGenerator codeGenerator : codeGenerators) {
        if (codeGenerator.isTemplateRequiredInClear()) {
          codeGenerator.addClear(code, "_template");
        } else {
          codeGenerator.addClear(code, null);
        }
      }
      if (any(metadata.getProperties(), IS_REQUIRED)) {
        code.addLine("  _unsetProperties.clear();")
            .addLine("  _unsetProperties.addAll(_template._unsetProperties);",
                metadata.getGeneratedBuilder());
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    } else {
      code.addLine("")
          .addLine("/**")
          .addLine(" * Ensures a subsequent mergeFrom call will make a clone of its input.")
          .addLine(" *")
          .addLine(" * <p>The exact implementation of this method is not guaranteed to remain")
          .addLine(" * stable; it should always be followed directly by a mergeFrom call.")
          .addLine(" */")
          .addLine("public %s clear() {", metadata.getBuilder());
      for (Property property : metadata.getProperties()) {
        property.getCodeGenerator().addPartialClear(code);
      }
      code.addLine("  return (%s) this;", metadata.getBuilder())
          .addLine("}");
    }
  }

  private static void addBuildPartialMethod(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a newly-created partial %s", metadata.getType().javadocLink())
        .addLine(" * based on the contents of the {@code %s}.",
            metadata.getBuilder().getSimpleName())
        .addLine(" * State checking will not be performed.");
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      code.addLine(" * Unset properties will throw an {@link %s}",
              UnsupportedOperationException.class)
          .addLine(" * when accessed via the partial object.");
    }
    code.addLine(" *")
        .addLine(" * <p>Partials should only ever be used in tests.")
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
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    // Constructor
    code.addLine("")
        .addLine("  private %s(%s builder) {",
            metadata.getValueType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addFinalFieldAssignment(code, "this." + property.getName(), "builder");
    }
    code.addLine("  }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      code.add("    return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("  }");
    }
    // Equals
    switch (metadata.standardMethodUnderride(StandardMethod.EQUALS)) {
      case ABSENT:
        // Default implementation if no user implementation exists.
        code.addLine("")
            .addLine("  @%s", Override.class)
            .addLine("  public boolean equals(Object obj) {")
            .addLine("    if (!(obj instanceof %s)) {", metadata.getValueType().getQualifiedName())
            .addLine("      return false;")
            .addLine("    }")
            .addLine("    %1$s other = (%1$s) obj;", metadata.getValueType().withWildcards());
        if (metadata.getProperties().isEmpty()) {
          code.addLine("    return true;");
        } else if (code.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
          String prefix = "    return ";
          for (Property property : metadata.getProperties()) {
            code.add(prefix);
            code.add("%1$s.equals(%2$s, other.%2$s)",
                code.feature(SOURCE_LEVEL).javaUtilObjects().get(), property.getName());
            prefix = "\n        && ";
          }
          code.add(";\n");
        } else {
          for (Property property : metadata.getProperties()) {
            switch (property.getType().getKind()) {
              case FLOAT:
              case DOUBLE:
                code.addLine("    if (%s.doubleToLongBits(%s)", Double.class, property.getName())
                    .addLine("        != %s.doubleToLongBits(other.%s)) {",
                        Double.class, property.getName());
                break;

              default:
                if (property.getType().getKind().isPrimitive()) {
                  code.addLine("    if (%1$s != other.%1$s) {", property.getName());
                } else if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
                  code.addLine("    if (%1$s != other.%1$s", property.getName())
                  .addLine("        && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                      property.getName());
                } else {
                  code.addLine("    if (!%1$s.equals(other.%1$s)) {", property.getName());
                }
            }
            code.addLine("      return false;")
                .addLine("    }");
          }
          code.addLine("    return true;");
        }
        code.addLine("  }");
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
      String properties = Joiner.on(", ").join(getNames(metadata.getProperties()));
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
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s toString() {", String.class)
          .add("    return \"%s{", metadata.getType().getSimpleName());
      switch (metadata.getProperties().size()) {
        case 0: {
          code.add("}\";\n");
          break;
        }

        case 1: {
          Property property = getOnlyElement(metadata.getProperties());
          if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
            code.add("\" + (%1$s != null ? \"%1$s=\" + %1$s : \"\") + \"}\";\n",
                property.getName());
          } else {
            code.add("%1$s=\" + %1$s + \"}\";\n", property.getName());
          }
          break;
        }

        default: {
          // If one or more of the properties are optional, use COMMA_JOINER for readability.
          // Otherwise, use string concatenation for performance.
          if (any(metadata.getProperties(), IS_OPTIONAL)) {
            code.add("\"\n")
                .add("        + COMMA_JOINER.join(\n");
            Property lastProperty = getLast(metadata.getProperties());
            for (Property property : metadata.getProperties()) {
              code.add("            ");
              if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
                code.add("(%s != null ? ", property.getName());
              }
              code.add("\"%1$s=\" + %1$s", property.getName());
              if (property.getCodeGenerator().getType() == Type.OPTIONAL) {
                code.add(" : null)");
              }
              if (property != lastProperty) {
                code.add(",\n");
              } else {
                code.add(")\n");
              }
            }
            code.addLine("        + \"}\";");
          } else {
            code.add("\"\n");
            Property lastProperty = getLast(metadata.getProperties());
            for (Property property : metadata.getProperties()) {
              code.add("        + \"%1$s=\" + %1$s", property.getName());
              if (property != lastProperty) {
                code.add(" + \", \"\n");
              } else {
                code.add(" + \"}\";\n");
              }
            }
          }
          break;
        }
      }
      code.addLine("  }");
    }
    code.addLine("}");
  }

  private static void addPartialType(SourceBuilder code, Metadata metadata) {
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("")
        .addLine("private static final class %s %s {",
            metadata.getPartialType().declaration(),
            extending(metadata.getType(), metadata.isInterfaceType()));
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    if (hasRequiredProperties) {
      code.addLine("  private final %s<%s> _unsetProperties;",
          EnumSet.class, metadata.getPropertyEnum());
    }
    // Constructor
    code.addLine("")
        .addLine("  %s(%s builder) {",
            metadata.getPartialType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addPartialFieldAssignment(code, "this." + property.getName(), "builder");
    }
    if (hasRequiredProperties) {
      code.addLine("    this._unsetProperties = builder._unsetProperties.clone();");
    }
    code.addLine("  }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      property.getCodeGenerator().addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    if (_unsetProperties.contains(%s.%s)) {",
                metadata.getPropertyEnum(), property.getAllCapsName())
            .addLine("      throw new %s(\"%s not set\");",
                UnsupportedOperationException.class, property.getName())
            .addLine("    }");
      }
      code.add("    return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("  }");
    }
    // Equals
    if (metadata.standardMethodUnderride(StandardMethod.EQUALS) != FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public boolean equals(Object obj) {")
          .addLine("    if (!(obj instanceof %s)) {", metadata.getPartialType().getQualifiedName())
          .addLine("      return false;")
          .addLine("    }")
          .addLine("    %1$s other = (%1$s) obj;", metadata.getPartialType().withWildcards());
      if (metadata.getProperties().isEmpty()) {
        code.addLine("    return true;");
      } else if (code.feature(SOURCE_LEVEL).javaUtilObjects().isPresent()) {
        String prefix = "    return ";
        for (Property property : metadata.getProperties()) {
          code.add(prefix);
          code.add("%1$s.equals(%2$s, other.%2$s)",
              code.feature(SOURCE_LEVEL).javaUtilObjects().get(), property.getName());
          prefix = "\n        && ";
        }
        if (hasRequiredProperties) {
          code.add(prefix);
          code.add("%1$s.equals(_unsetProperties, other._unsetProperties)",
              code.feature(SOURCE_LEVEL).javaUtilObjects().get());
        }
        code.add(";\n");
      } else {
        for (Property property : metadata.getProperties()) {
          switch (property.getType().getKind()) {
            case FLOAT:
            case DOUBLE:
              code.addLine("    if (%s.doubleToLongBits(%s)", Double.class, property.getName())
                  .addLine("        != %s.doubleToLongBits(other.%s)) {",
                      Double.class, property.getName());
              break;

            default:
              if (property.getType().getKind().isPrimitive()) {
                code.addLine("    if (%1$s != other.%1$s) {", property.getName());
              } else if (property.getCodeGenerator().getType() == Type.HAS_DEFAULT) {
                code.addLine("    if (!%1$s.equals(other.%1$s)) {", property.getName());
              } else {
                code.addLine("    if (%1$s != other.%1$s", property.getName())
                    .addLine("        && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                        property.getName());
              }
          }
          code.addLine("      return false;")
              .addLine("    }");
          }
        if (hasRequiredProperties) {
          code.addLine("    return _unsetProperties.equals(other._unsetProperties);");
        } else {
          code.addLine("    return true;");
        }
      }
      code.addLine("  }");
    }
    // Hash code
    if (metadata.standardMethodUnderride(StandardMethod.HASH_CODE) != FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public int hashCode() {");

      List<String> namesList = getNames(metadata.getProperties());
      if (hasRequiredProperties) {
        namesList =
            ImmutableList.<String>builder().addAll(namesList).add("_unsetProperties").build();
      }
      String properties = Joiner.on(", ").join(namesList);

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
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s toString() {", String.class);
      if (metadata.getProperties().size() > 1 && !code.feature(GUAVA).isAvailable()) {
        writePartialToStringWithBuilder(code, metadata);
      } else {
        writePartialToStringWithConcatenation(code, metadata);
      }
      code.addLine("  }");
    }
    code.addLine("}");
  }

  private static void writePartialToStringWithBuilder(SourceBuilder code, Metadata metadata) {
    code.addLine("%1$s result = new %1$s(\"partial %2$s{\");",
        StringBuilder.class, metadata.getType().getSimpleName());
    boolean noDefaults = !any(metadata.getProperties(), HAS_DEFAULT);
    if (noDefaults) {
      // We need to keep track of whether to output a separator
      code.addLine("String separator = \"\";");
    }
    boolean seenDefault = false;
    Property first = metadata.getProperties().get(0);
    Property last = Iterables.getLast(metadata.getProperties());
    for (Property property : metadata.getProperties()) {
      boolean hadSeenDefault = seenDefault;
      switch (property.getCodeGenerator().getType()) {
        case HAS_DEFAULT:
          seenDefault = true;
          break;

        case OPTIONAL:
          code.addLine("if (%s != null) {", property.getName());
          break;

        case REQUIRED:
          code.addLine("if (!_unsetProperties.contains(%s.%s)) {",
                  metadata.getPropertyEnum(), property.getAllCapsName());
          break;
      }
      if (noDefaults && property != first) {
        code.addLine("result.append(separator);");
      } else if (!noDefaults && hadSeenDefault) {
        code.addLine("result.append(\", \");");
      }
      code.addLine("result.append(\"%1$s=\").append(%1$s);", property.getName());
      if (!noDefaults && !seenDefault) {
        code.addLine("result.append(\", \");");
      } else if (noDefaults && property != last) {
        code.addLine("separator = \", \";");
      }
      switch (property.getCodeGenerator().getType()) {
        case HAS_DEFAULT:
          break;

        case OPTIONAL:
        case REQUIRED:
          code.addLine("}");
          break;
      }
    }
    code.addLine("result.append(\"}\");")
        .addLine("return result.toString();");
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
        switch (property.getCodeGenerator().getType()) {
          case HAS_DEFAULT:
            code.add("%1$s=\" + %1$s + \"}\";\n", property.getName());
            break;

          case OPTIONAL:
            code.add("\"\n")
                .addLine("        + (%1$s != null ? \"%1$s=\" + %1$s : \"\")",
                    property.getName())
                .addLine("        + \"}\";");
            break;

          case REQUIRED:
            code.add("\"\n")
                .addLine("        + (!_unsetProperties.contains(%s.%s)",
                    metadata.getPropertyEnum(), property.getAllCapsName())
                .addLine("            ? \"%1$s=\" + %1$s : \"\")", property.getName())
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
          switch (property.getCodeGenerator().getType()) {
            case HAS_DEFAULT:
              code.add("\"%1$s=\" + %1$s", property.getName());
              break;

            case OPTIONAL:
              code.add("(%1$s != null ? \"%1$s=\" + %1$s : null)", property.getName());
              break;

            case REQUIRED:
              code.add("(!_unsetProperties.contains(%s.%s)\n",
                      metadata.getPropertyEnum(), property.getAllCapsName())
                  .add("                ? \"%1$s=\" + %1$s : null)", property.getName());
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

  private static void addStaticMethods(SourceBuilder code, Metadata metadata) {
    SortedSet<Excerpt> staticMethods = new TreeSet<Excerpt>();
    for (Property property : metadata.getProperties()) {
      staticMethods.addAll(property.getCodeGenerator().getStaticExcerpts());
    }
    for (Excerpt staticMethod : staticMethods) {
      code.add(staticMethod);
    }
  }

  private void writeStubSource(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.",
            metadata.getType())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName())
        .addLine("abstract class %s {}", metadata.getGeneratedBuilder().declaration());
  }

  /** Returns an {@link Excerpt} of "implements/extends {@code type}". */
  private static Excerpt extending(final Object type, final boolean isInterface) {
    return new Excerpt() {
      @Override public void addTo(SourceBuilder source) {
        if (isInterface) {
          source.add("implements %s", type);
        } else {
          source.add("extends %s", type);
        }
      }
    };
  }

  private static ImmutableList<String> getNames(Iterable<Property> properties) {
    ImmutableList.Builder<String> result = ImmutableList.builder();
    for (Property property : properties) {
      result.add(property.getName());
    }
    return result.build();
  }

  private static final Predicate<Property> IS_REQUIRED = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().getType() == Type.REQUIRED;
    }
  };

  private static final Predicate<Property> IS_OPTIONAL = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().getType() == Type.OPTIONAL;
    }
  };

  private static final Predicate<Property> HAS_DEFAULT = new Predicate<Property>() {
    @Override public boolean apply(Property property) {
      return property.getCodeGenerator().getType() == Type.HAS_DEFAULT;
    }
  };
}
