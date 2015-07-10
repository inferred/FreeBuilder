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
import static org.inferred.freebuilder.processor.Metadata.GET_CODE_GENERATOR;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.ABSENT;
import static org.inferred.freebuilder.processor.Metadata.UnderrideLevel.FINAL;
import static org.inferred.freebuilder.processor.PropertyCodeGenerator.IS_TEMPLATE_REQUIRED_IN_CLEAR;
import static org.inferred.freebuilder.processor.util.SourceBuilders.withIndent;

import java.io.Serializable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Generated;
import javax.lang.model.element.TypeElement;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.QualifiedName;

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Code generation for the &#64;{@link FreeBuilder} annotation.
 */
public class CodeGenerator {

  private static final QualifiedName CUSTOM_FIELD_SERIALIZER =
      QualifiedName.of("com.google.gwt.user.client.rpc", "CustomFieldSerializer");
  private static final QualifiedName SERIALIZATION_EXCEPTION =
      QualifiedName.of("com.google.gwt.user.client.rpc", "SerializationException");
  private static final QualifiedName SERIALIZATION_STREAM_READER =
      QualifiedName.of("com.google.gwt.user.client.rpc", "SerializationStreamReader");
  private static final QualifiedName SERIALIZATION_STREAM_WRITER =
      QualifiedName.of("com.google.gwt.user.client.rpc", "SerializationStreamWriter");

  /** Write the source code for a generated builder. */
  void writeBuilderSource(SourceBuilder code, Metadata metadata) {
    if (!metadata.hasBuilder()) {
      writeStubSource(code, metadata);
      return;
    }

    addBuilderTypeDeclaration(code, metadata);
    code.addLine(" {");
    SourceBuilder body = withIndent(code, 2);

    addConstantDeclarations(metadata, body);
    if (any(metadata.getProperties(), IS_REQUIRED)) {
      addPropertyEnum(metadata, body);
    }

    addFieldDeclarations(body, metadata);

    addAccessors(metadata, body);
    addMergeFromValueMethod(body, metadata);
    addMergeFromBuilderMethod(body, metadata);
    addClearMethod(body, metadata);
    addBuildMethod(body, metadata);
    addBuildPartialMethod(body, metadata);

    addValueType(body, metadata);
    if (metadata.isGwtSerializable()) {
      addCustomValueSerializer(body, metadata);
      addGwtWhitelistType(body, metadata);
    }
    addPartialType(body, metadata);
    code.addLine("}");
  }

  private void addBuilderTypeDeclaration(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of %s,", metadata.getBuilder().javadocLink())
        .addLine(" * derived from the API of %s.", metadata.getType().javadocLink())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName());
    if (metadata.isGwtCompatible()) {
      code.addLine("@%s", GwtCompatible.class);
    }
    code.add("abstract class %s", metadata.getGeneratedBuilder().declaration());
    if (metadata.isBuilderSerializable()) {
      code.add(" implements %s", Serializable.class);
    }
  }

  private static void addConstantDeclarations(Metadata metadata, SourceBuilder body) {
    if (metadata.getProperties().size() > 1) {
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
      code.addLine(
          "  %s.checkState(_unsetProperties.isEmpty(), \"Not set: %%s\", _unsetProperties);",
          Preconditions.class);
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
      property.getCodeGenerator().addMergeFromValue(withIndent(code, 2), "value");
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
        property.getCodeGenerator().addMergeFromBuilder(withIndent(code, 4), metadata, "template");
        code.addLine("  }");
      } else {
        property.getCodeGenerator().addMergeFromBuilder(withIndent(code, 2), metadata, "template");
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
        code.add("  %s _template = ", metadata.getGeneratedBuilder());
        metadata.getBuilderFactory().get().addNewBuilder(code, metadata.getBuilder());
        code.add(";\n");
      }
      for (PropertyCodeGenerator codeGenerator : codeGenerators) {
        if (codeGenerator.isTemplateRequiredInClear()) {
          codeGenerator.addClear(withIndent(code, 2), "_template");
        } else {
          codeGenerator.addClear(withIndent(code, 2), null);
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
        property.getCodeGenerator().addPartialClear(withIndent(code, 2));
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
        .addLine(" */")
        .addLine("@%s()", VisibleForTesting.class)
        .addLine("public %s buildPartial() {", metadata.getType())
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
    if (metadata.isGwtSerializable()) {
      // Due to a bug in GWT's handling of nested types, we have to declare Value as package scoped
      // so Value_CustomFieldSerializer can access it.
      code.addLine("@%s(serializable = true)", GwtCompatible.class);
    } else {
      code.add("private ");
    }
    code.addLine("static final class %s %s {",
        metadata.getValueType().declaration(),
        extending(metadata.getType(), metadata.isInterfaceType()));
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(withIndent(code, 2), property.getName());
    }
    // Constructor
    code.addLine("")
        .addLine("  private %s(%s builder) {",
            metadata.getValueType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addFinalFieldAssignment(withIndent(code, 4), "this." + property.getName(), "builder");
    }
    code.addLine("  }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.addLine("  @%s", nullableAnnotation);
      }
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
        } else if (code.getSourceLevel().javaUtilObjects().isPresent()) {
          String prefix = "    return ";
          for (Property property : metadata.getProperties()) {
            code.add(prefix);
            code.add("%1$s.equals(%2$s, other.%2$s)",
                code.getSourceLevel().javaUtilObjects().get(), property.getName());
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
      if (code.getSourceLevel().javaUtilObjects().isPresent()) {
        code.addLine("    return %s.hash(%s);",
            code.getSourceLevel().javaUtilObjects().get(), properties);
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

  private static void addCustomValueSerializer(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("@%s", GwtCompatible.class);
    if (metadata.getType().isParameterized()) {
      code.addLine("@%s(\"unchecked\")", SuppressWarnings.class);
    }
    code.addLine("public static class %s_CustomFieldSerializer",
            metadata.getValueType().getSimpleName())
        .addLine("    extends %s<%s> {", CUSTOM_FIELD_SERIALIZER, metadata.getValueType())
        .addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public void deserializeInstance(%s reader, %s instance) { }",
            SERIALIZATION_STREAM_READER, metadata.getValueType())
        .addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public boolean hasCustomInstantiateInstance() {")
        .addLine("    return true;")
        .addLine("  }")
        .addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public %s instantiateInstance(%s reader)",
            metadata.getValueType(), SERIALIZATION_STREAM_READER)
        .addLine("      throws %s {", SERIALIZATION_EXCEPTION)
        .addLine("    %1$s builder = new %1$s();", metadata.getBuilder());
    for (Property property : metadata.getProperties()) {
      if (property.getType().getKind().isPrimitive()) {
        code.addLine("      %s %s = reader.read%s();",
            property.getType(), property.getName(), withInitialCapital(property.getType()));
        property.getCodeGenerator()
            .addSetFromResult(withIndent(code, 6), "builder", property.getName());
      } else if (String.class.getName().equals(property.getType().toString())) {
        code.addLine("      %s %s = reader.readString();",
            property.getType(), property.getName());
        property.getCodeGenerator()
            .addSetFromResult(withIndent(code, 6), "builder", property.getName());
      } else {
        code.addLine("    try {");
        if (!property.isFullyCheckedCast()) {
          code.addLine("      @SuppressWarnings(\"unchecked\")");
        }
        code.addLine("      %1$s %2$s = (%1$s) reader.readObject();",
                property.getType(), property.getName());
        property.getCodeGenerator()
            .addSetFromResult(withIndent(code, 8), "builder", property.getName());
        code.addLine("    } catch (%s e) {", ClassCastException.class)
            .addLine("      throw new %s(", SERIALIZATION_EXCEPTION)
            .addLine("          \"Wrong type for property '%s'\", e);", property.getName())
            .addLine("    }");
      }
    }
    code.addLine("    return (%s) builder.build();", metadata.getValueType())
        .addLine("  }")
        .addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public void serializeInstance(%s writer, %s instance)",
            SERIALIZATION_STREAM_WRITER, metadata.getValueType())
        .addLine("      throws %s {", SERIALIZATION_EXCEPTION);
    for (Property property : metadata.getProperties()) {
      if (property.getType().getKind().isPrimitive()) {
        code.add("    writer.write%s(",
            withInitialCapital(property.getType()), property.getName());
      } else if (String.class.getName().equals(property.getType().toString())) {
        code.add("    writer.writeString(", property.getName());
      } else {
        code.add("    writer.writeObject(", property.getName());
      }
      property.getCodeGenerator().addReadValueFragment(code, "instance." + property.getName());
      code.add(");\n");
    }
    code.addLine("  }")
        .addLine("")
        .addLine("  private static final Value_CustomFieldSerializer INSTANCE ="
            + " new Value_CustomFieldSerializer();")
        .addLine("")
        .addLine("  public static void deserialize(%s reader, %s instance) {",
            SERIALIZATION_STREAM_READER, metadata.getValueType())
        .addLine("    INSTANCE.deserializeInstance(reader, instance);")
        .addLine("  }")
        .addLine("")
        .addLine("  public static %s instantiate(%s reader)",
            metadata.getValueType(), SERIALIZATION_STREAM_READER)
        .addLine("      throws %s {", SERIALIZATION_EXCEPTION)
        .addLine("    return INSTANCE.instantiateInstance(reader);")
        .addLine("  }")
        .addLine("")
        .addLine("  public static void serialize(%s writer, %s instance)",
            SERIALIZATION_STREAM_WRITER, metadata.getValueType())
        .addLine("      throws %s {", SERIALIZATION_EXCEPTION)
        .addLine("    INSTANCE.serializeInstance(writer, instance);")
        .addLine("  }")
        .addLine("}");
  }

  private static void addGwtWhitelistType(SourceBuilder code, Metadata metadata) {
    code.addLine("")
        .addLine("/** This class exists solely to ensure GWT whitelists all required types. */")
        .addLine("@%s(serializable = true)", GwtCompatible.class)
        .addLine("static final class GwtWhitelist%s %s {",
            metadata.getType().typeParameters(),
            extending(metadata.getType(), metadata.isInterfaceType()))
        .addLine("");
    for (Property property : metadata.getProperties()) {
      code.addLine("  %s %s;", property.getType(), property.getName());
    }
    code.addLine("")
        .addLine("  private GwtWhitelist() {")
        .addLine("    throw new %s();", UnsupportedOperationException.class)
        .addLine("   }");
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s %s() {", property.getType(), property.getGetterName())
          .addLine("    throw new %s();", UnsupportedOperationException.class)
          .addLine("  }");
    }
    code.addLine("}");
  }

  private static void addPartialType(SourceBuilder code, Metadata metadata) {
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("")
        .addLine("private static final class %s%s %s {",
            metadata.getPartialType().getSimpleName(),
            metadata.getType().typeParameters(),
            extending(metadata.getType(), metadata.isInterfaceType()));
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(withIndent(code, 2), property.getName());
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
          .addPartialFieldAssignment(withIndent(code, 4), "this." + property.getName(), "builder");
    }
    if (hasRequiredProperties) {
      code.addLine("    this._unsetProperties = builder._unsetProperties.clone();");
    }
    code.addLine("  }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      for (TypeElement nullableAnnotation : property.getNullableAnnotations()) {
        code.addLine("  @%s", nullableAnnotation);
      }
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
      } else if (code.getSourceLevel().javaUtilObjects().isPresent()) {
        String prefix = "    return ";
        for (Property property : metadata.getProperties()) {
          code.add(prefix);
          code.add("%1$s.equals(%2$s, other.%2$s)",
              code.getSourceLevel().javaUtilObjects().get(), property.getName());
          prefix = "\n        && ";
        }
        if (hasRequiredProperties) {
          code.add(prefix);
          code.add("%1$s.equals(_unsetProperties, other._unsetProperties)",
              code.getSourceLevel().javaUtilObjects().get());
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

      if (code.getSourceLevel().javaUtilObjects().isPresent()) {
        code.addLine("    return %s.hash(%s);",
            code.getSourceLevel().javaUtilObjects().get(), properties);
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
      code.addLine("  }");
    }
    code.addLine("}");
  }

  private void writeStubSource(SourceBuilder code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.",
            metadata.getType())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName())
        .addLine("abstract class %s {}", metadata.getGeneratedBuilder().declaration());
  }

  /** Returns an {@link Excerpt} of "implements/extends {@code type}{@code typeParameters}". */
  private static Excerpt extending(final ParameterizedType type, final boolean isInterface) {
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

  private static String withInitialCapital(Object obj) {
    String s = obj.toString();
    return s.substring(0, 1).toUpperCase() + s.substring(1);
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
}
