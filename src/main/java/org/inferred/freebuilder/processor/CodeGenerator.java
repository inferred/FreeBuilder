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

import com.google.common.annotations.GwtCompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.gwt.user.client.rpc.CustomFieldSerializer;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.SerializationStreamReader;
import com.google.gwt.user.client.rpc.SerializationStreamWriter;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.SourceWriter;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Objects;

import javax.annotation.Generated;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

/**
 * Code generation for the &#64;{@link FreeBuilder} annotation.
 */
public class CodeGenerator {

  /** Write the source code for a generated builder. */
  void writeBuilderSource(SourceWriter code, Metadata metadata) {
    if (metadata.getBuilder() == metadata.getGeneratedBuilder()) {
      writeStubSource(code, metadata);
      return;
    }
    boolean hasRequiredProperties = any(metadata.getProperties(), IS_REQUIRED);
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of {@link %s},", metadata.getBuilder())
        .addLine(" * derived from the API of {@link %s}.", metadata.getType())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName());
    code.add("abstract class %s", metadata.getGeneratedBuilder().getSimpleName());
    if (metadata.isBuilderSerializable()) {
      code.add(" implements %s", Serializable.class);
    }
    code.add(" {");
    // Static fields
    if (metadata.getProperties().size() > 1) {
      code.addLine("")
          .addLine("  private static final %1$s COMMA_JOINER =", Joiner.class)
          .addLine("      %s.on(\", \").skipNulls();\n", Joiner.class);
    }
    // Property enum
    if (hasRequiredProperties) {
      addPropertyEnum(metadata, code);
    }
    // Property fields
    code.addLine("");
    for (Property property : metadata.getProperties()) {
      PropertyCodeGenerator codeGenerator = property.getCodeGenerator();
      codeGenerator.addBuilderFieldDeclaration(code);
    }
    // Unset properties
    if (hasRequiredProperties) {
      code.addLine("  private final %1$s<%2$s> _unsetProperties = %1$s.allOf(%2$s.class);",
          EnumSet.class, metadata.getPropertyEnum());
    }
    // Setters and getters
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addBuilderFieldAccessors(code, metadata);
    }
    // Value type
    String inheritsFrom = getInheritanceKeyword(metadata.getType());
    code.addLine("");
    if (metadata.isGwtSerializable()) {
      // Due to a bug in GWT's handling of nested types, we have to declare Value as package scoped
      // so Value_CustomFieldSerializer can access it.
      code.addLine("  @%s(serializable = true)", GwtCompatible.class)
          .addLine("  static final class %s %s %s {",
              metadata.getValueType().getSimpleName(),
              inheritsFrom,
              metadata.getType());
    } else {
      code.addLine("  private static final class %s %s %s {",
          metadata.getValueType().getSimpleName(),
          inheritsFrom,
          metadata.getType());
    }
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    // Constructor
    code.addLine("")
        .addLine("    private %s(%s builder) {",
            metadata.getValueType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addFinalFieldAssignment(code, "this." + property.getName(), "builder");
    }
    code.addLine("    }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public %s %s() {", property.getType(), property.getGetterName());
      code.add("      return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("    }");
    }
    // Equals
    if (!metadata.getUnderriddenMethods().contains(StandardMethod.EQUALS)) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public boolean equals(Object obj) {")
          .addLine("      if (!(obj instanceof %s)) {", metadata.getValueType())
          .addLine("        return false;")
          .addLine("      }")
          .addLine("      %1$s other = (%1$s) obj;", metadata.getValueType());
      for (Property property : metadata.getProperties()) {
        switch (property.getType().getKind()) {
          case FLOAT:
          case DOUBLE:
            code.addLine("      if (%s.doubleToLongBits(%s)",
                Double.class, property.getName())
                .addLine("          != %s.doubleToLongBits(other.%s)) {",
                    Double.class, property.getName());
            break;

          default:
            if (property.getType().getKind().isPrimitive()) {
              code.addLine("      if (%1$s != other.%1$s) {", property.getName());
            } else {
              code.addLine("      if (%1$s != other.%1$s", property.getName())
              .addLine("          && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                  property.getName());
            }
        }
        code.addLine("        return false;")
            .addLine("      }");
      }
      code.addLine("      return true;")
          .addLine("    }");
    }
    // Hash code
    if (!metadata.getUnderriddenMethods().contains(StandardMethod.HASH_CODE)) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public int hashCode() {")
          .addLine("      // Return the same result as passing all the fields")
          .addLine("      // into Arrays.hashCode() in an Object[].")
          .addLine("      int result = 1;");
      for (Property property : metadata.getProperties()) {
        code.addLine("      result *= 31;");
        if (property.getType().getKind().isPrimitive()) {
          code.addLine("      result += ((%s) %s).hashCode();",
              property.getBoxedType(), property.getName());

        } else {
          code.addLine("      result += ((%1$s == null) ? 0 : %1$s.hashCode());",
              property.getName());
        }
      }
      code.addLine("      return result;")
          .addLine("    }");
    }
    // toString
    if (!metadata.getUnderriddenMethods().contains(StandardMethod.TO_STRING)) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public String toString() {")
          .add("      return \"%s{", metadata.getType().getSimpleName());
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
                .add("          + COMMA_JOINER.join(\n");
            Property lastProperty = getLast(metadata.getProperties());
            for (Property property : metadata.getProperties()) {
              code.add("              ");
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
            code.addLine("          + \"}\";\n");
          } else {
            code.add("\"\n");
            Property lastProperty = getLast(metadata.getProperties());
            for (Property property : metadata.getProperties()) {
              code.add("          + \"%1$s=\" + %1$s", property.getName());
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
      code.addLine("    }");
    }
    code.addLine("  }");
    if (metadata.isGwtSerializable()) {
      addCustomValueSerializer(metadata, code);
    }
    // build()
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Returns a newly-created {@link %s}", metadata.getType())
        .addLine("   * based on the contents of the {@code %s}.",
            metadata.getBuilder().getSimpleName());
    if (hasRequiredProperties) {
      code.addLine("   *")
          .addLine("    @throws IllegalStateException if any field has not been set");
    }
    code.addLine("   */")
        .addLine("  public %s build() {", metadata.getType());
    if (hasRequiredProperties) {
      code.addLine("    if (!_unsetProperties.isEmpty()) {")
          .addLine("      throw new %s(\"Not set: \" + _unsetProperties);",
              IllegalStateException.class)
          .addLine("    }");
    }
    code.addLine("    return new %s(this);", metadata.getValueType())
        .addLine("  }");
    // mergeFrom(Value)
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Sets all property values using the given")
        .addLine("   * {@code %s} as a template.", metadata.getType())
        .addLine("   */")
        .addLine("  public %s mergeFrom(%s value) {",
            metadata.getBuilder(), metadata.getType());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addMergeFromValue(code, "value");
    }
    code.addLine("    return (%s) this;", metadata.getBuilder());
    code.addLine("  }");
    // mergeFrom(Builder)
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Copies values from the given {@code %s}.", metadata.getBuilder());
    if (hasRequiredProperties) {
      code.addLine("   * Does not affect any properties not set on the input.");
    }
    code.addLine("   */")
        .addLine("  public %1$s mergeFrom(%1$s template) {", metadata.getBuilder());
    if (hasRequiredProperties) {
      code.addLine("    // Upcast to access the private _unsetProperties field.")
          .addLine("    // Otherwise, oddly, we get an access violation.")
          .addLine("    %s<%s> _templateUnset = ((%s) template)._unsetProperties;",
              EnumSet.class,
              metadata.getPropertyEnum(),
              metadata.getGeneratedBuilder());
    }
    for (Property property : metadata.getProperties()) {
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    if (!_templateUnset.contains(%s.%s)) {",
            metadata.getPropertyEnum(), property.getAllCapsName());
      }
      property.getCodeGenerator().addMergeFromBuilder(code, metadata, "template");
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    }");
      }
    }
    code.addLine("    return (%s) this;", metadata.getBuilder());
    code.addLine("  }");
    // clear()
    if (metadata.getBuilderFactory().isPresent()) {
      code.addLine("")
          .addLine("  /**")
          .addLine("   * Resets the state of this builder.")
          .addLine("   */")
          .addLine("  public %s clear() {", metadata.getBuilder());
      if (!metadata.getProperties().isEmpty()) {
        code.add("    %s template = ", metadata.getGeneratedBuilder());
        metadata.getBuilderFactory().get().addNewBuilder(code, metadata.getBuilder());
        code.add(";\n");
        for (Property property : metadata.getProperties()) {
          property.getCodeGenerator().addClear(code, "template");
        }
        if (hasRequiredProperties) {
          code.addLine("    _unsetProperties.clear();")
              .addLine("    _unsetProperties.addAll(template._unsetProperties);",
                  metadata.getGeneratedBuilder());
        }
      }
      code.addLine("    return (%s) this;", metadata.getBuilder())
          .addLine("  }");
    } else {
      code.addLine("")
          .addLine("  /**")
          .addLine("   * Ensures a subsequent mergeFrom call will make a clone of its input.")
          .addLine("   *")
          .addLine("   * <p>The exact implementation of this method is not guaranteed to remain")
          .addLine("   * stable; it should always be followed directly by a mergeFrom call.")
          .addLine("   */")
          .addLine("  public %s clear() {", metadata.getBuilder());
      for (Property property : metadata.getProperties()) {
        property.getCodeGenerator().addPartialClear(code);
      }
      code.addLine("    return (%s) this;", metadata.getBuilder())
          .addLine("  }");
    }
    // GWT whitelist type
    if (metadata.isGwtSerializable()) {
      code.addLine("")
          .addLine("  /** This class exists solely to ensure GWT whitelists all required types. */")
          .addLine("  @%s(serializable = true)", GwtCompatible.class)
          .addLine("  static final class GwtWhitelist %s %s {",
              inheritsFrom, metadata.getType())
          .addLine("");
      for (Property property : metadata.getProperties()) {
        code.addLine("    %s %s;", property.getType(), property.getName());
      }
      code.addLine("")
          .addLine("    private GwtWhitelist() {")
          .addLine("      throw new %s();", UnsupportedOperationException.class)
          .addLine("    }");
      for (Property property : metadata.getProperties()) {
        code.addLine("")
            .addLine("    @%s", Override.class)
            .addLine("    public %s %s() {", property.getType(), property.getGetterName());
        code.addLine("      throw new %s();", UnsupportedOperationException.class)
            .addLine("    }");
      }
      code.addLine("  }");
    }
    // Partial value type
    code.addLine("")
        .addLine("  private static final class %s %s %s {",
            metadata.getPartialType().getSimpleName(),
            inheritsFrom,
            metadata.getType());
    // Fields
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator().addValueFieldDeclaration(code, property.getName());
    }
    if (hasRequiredProperties) {
      code.addLine("    private final %s<%s> _unsetProperties;",
          EnumSet.class, metadata.getPropertyEnum());
    }
    // Constructor
    code.addLine("")
        .addLine("    %s(%s builder) {",
            metadata.getPartialType().getSimpleName(),
            metadata.getGeneratedBuilder());
    for (Property property : metadata.getProperties()) {
      property.getCodeGenerator()
          .addPartialFieldAssignment(code, "this." + property.getName(), "builder");
    }
    if (hasRequiredProperties) {
      code.addLine("      this._unsetProperties = builder._unsetProperties.clone();");
    }
    code.addLine("    }");
    // Getters
    for (Property property : metadata.getProperties()) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public %s %s() {", property.getType(), property.getGetterName());
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("      if (_unsetProperties.contains(%s.%s)) {",
                metadata.getPropertyEnum(), property.getAllCapsName())
            .addLine("        throw new %s(\"%s not set\");",
                UnsupportedOperationException.class, property.getName())
            .addLine("      }");
      }
      code.add("      return ");
      property.getCodeGenerator().addReadValueFragment(code, property.getName());
      code.add(";\n");
      code.addLine("    }");
    }
    // Equals
    if (!metadata.getUnderriddenMethods().contains(StandardMethod.EQUALS)) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public boolean equals(Object obj) {")
          .addLine("      if (!(obj instanceof %s)) {", metadata.getPartialType())
          .addLine("        return false;")
          .addLine("      }")
          .addLine("      %1$s other = (%1$s) obj;", metadata.getPartialType());
      for (Property property : metadata.getProperties()) {
        switch (property.getType().getKind()) {
          case FLOAT:
          case DOUBLE:
            code.addLine("      if (%s.doubleToLongBits(%s)",
                Double.class, property.getName())
                .addLine("          != %s.doubleToLongBits(other.%s)) {",
                    Double.class, property.getName());
            break;

          default:
            if (property.getType().getKind().isPrimitive()) {
              code.addLine("      if (%1$s != other.%1$s) {", property.getName());
            } else {
              code.addLine("      if (%1$s != other.%1$s", property.getName())
              .addLine("          && (%1$s == null || !%1$s.equals(other.%1$s))) {",
                  property.getName());
            }
        }
        code.addLine("        return false;")
            .addLine("      }");
      }
      if (hasRequiredProperties) {
        code.addLine("      return _unsetProperties.equals(other._unsetProperties);");
      } else {
        code.addLine("      return true;");
      }
      code.addLine("    }");
    }
    // Hash code
    if (!metadata.getUnderriddenMethods().contains(StandardMethod.HASH_CODE)) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public int hashCode() {")
          .addLine("      int result = 1;");
      for (Property property : metadata.getProperties()) {
        code.addLine("      result *= 31;");
        if (property.getType().getKind().isPrimitive()) {
          code.addLine("      result += ((%s) %s).hashCode();",
              property.getBoxedType(), property.getName());

        } else {
          code.addLine("      result += ((%1$s == null) ? 0 : %1$s.hashCode());",
              property.getName());
        }
      }
      if (hasRequiredProperties) {
        code.addLine("      result *= 31;")
            .addLine("      result += _unsetProperties.hashCode();");
      }
      code.addLine("      return result;")
          .addLine("    }");
    }
    // toString
    if (!metadata.getUnderriddenMethods().contains(StandardMethod.TO_STRING)) {
      code.addLine("")
          .addLine("    @%s", Override.class)
          .addLine("    public String toString() {");
      code.add("      return \"partial %s{", metadata.getType().getSimpleName());
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
                  .addLine("          + (%1$s != null ? \"%1$s=\" + %1$s : \"\")",
                      property.getName())
                  .addLine("          + \"}\";");
              break;

            case REQUIRED:
              code.add("\"\n")
                  .addLine("          + (!_unsetProperties.contains(%s.%s)",
                      metadata.getPropertyEnum(), property.getAllCapsName())
                  .addLine("              ? \"%1$s=\" + %1$s : \"\")", property.getName())
                  .addLine("          + \"}\";");
              break;
          }
          break;
        }

        default: {
          code.add("\"\n")
              .add("          + COMMA_JOINER.join(\n");
          Property lastProperty = getLast(metadata.getProperties());
          for (Property property : metadata.getProperties()) {
            code.add("              ");
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
                    .add("                  ? \"%1$s=\" + %1$s : null)", property.getName());
                break;
            }
            if (property != lastProperty) {
              code.add(",\n");
            } else {
              code.add(")\n");
            }
          }
          code.addLine("          + \"}\";\n");
          break;
        }
      }
      code.addLine("    }");
    }
    code.addLine("  }");
    // buildPartial()
    code.addLine("")
        .addLine("  /**")
        .addLine("   * Returns a newly-created partial {@link %s}", metadata.getType())
        .addLine("   * based on the contents of the {@code %s}.",
            metadata.getBuilder().getSimpleName())
        .addLine("   * State checking will not be performed.");
    if (hasRequiredProperties) {
      code.addLine("   * Unset properties will throw an {@link %s}",
              UnsupportedOperationException.class)
          .addLine("   * when accessed via the partial object.");
    }
    code.addLine("   *")
        .addLine("   * <p>Partials should only ever be used in tests.")
        .addLine("   */")
        .addLine("  @%s()", VisibleForTesting.class)
        .addLine("  public %s buildPartial() {", metadata.getType())
        .addLine("    return new %s(this);", metadata.getPartialType())
        .addLine("  }")
        .addLine("}");
  }

  private void addPropertyEnum(Metadata metadata, SourceWriter code) {
    code.addLine("")
        .addLine("  private enum %s {", metadata.getPropertyEnum().getSimpleName());
    for (Property property : metadata.getProperties()) {
      if (property.getCodeGenerator().getType() == Type.REQUIRED) {
        code.addLine("    %s(\"%s\"),", property.getAllCapsName(), property.getName());
      }
    }
    code.addLine("    ;")
        .addLine("")
        .addLine("    private final String name;")
        .addLine("")
        .addLine("    private %s(String name) {", metadata.getPropertyEnum().getSimpleName())
        .addLine("      this.name = name;")
        .addLine("    }")
        .addLine("")
        .addLine("    @%s public String toString() {", Override.class)
        .addLine("      return name;")
        .addLine("    }")
        .addLine("  }");
  }

  private void addCustomValueSerializer(Metadata metadata, SourceWriter code) {
    code.addLine("")
        .addLine("  @%s", GwtCompatible.class)
        .addLine("  public static class %s_CustomFieldSerializer",
            metadata.getValueType().getSimpleName())
        .addLine("      extends %s<%s> {",
            CustomFieldSerializer.class, metadata.getValueType())
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public void deserializeInstance(%s reader, %s instance) { }",
            SerializationStreamReader.class, metadata.getValueType())
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public boolean hasCustomInstantiateInstance() {")
        .addLine("      return true;")
        .addLine("    }")
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public %s instantiateInstance(%s reader)",
            metadata.getValueType(), SerializationStreamReader.class)
        .addLine("        throws %s {", SerializationException.class)
        .addLine("      %1$s builder = new %1$s();", metadata.getBuilder());
    for (Property property : metadata.getProperties()) {
      if (property.getType().getKind().isPrimitive()) {
        code.addLine("        %s %s = reader.read%s();",
            property.getType(), property.getName(), withInitialCapital(property.getType()));
        property.getCodeGenerator().addSetFromResult(code, "builder", property.getName());
      } else if (String.class.getName().equals(property.getType().toString())) {
        code.addLine("        %s %s = reader.readString();",
            property.getType(), property.getName());
        property.getCodeGenerator().addSetFromResult(code, "builder", property.getName());
      } else {
        code.addLine("      try {");
        if (!property.isFullyCheckedCast()) {
          code.addLine("        @SuppressWarnings(\"unchecked\")");
        }
        code.addLine("        %1$s %2$s = (%1$s) reader.readObject();",
                property.getType(), property.getName());
        property.getCodeGenerator().addSetFromResult(code, "builder", property.getName());
        code.addLine("      } catch (%s e) {", ClassCastException.class)
            .addLine("        throw new %s(", SerializationException.class)
            .addLine("            \"Wrong type for property '%s'\", e);", property.getName())
            .addLine("      }");
      }
    }
    code.addLine("      return (%s) builder.build();", metadata.getValueType())
        .addLine("    }")
        .addLine("")
        .addLine("    @%s", Override.class)
        .addLine("    public void serializeInstance(%s writer, %s instance)",
            SerializationStreamWriter.class, metadata.getValueType())
        .addLine("        throws %s {", SerializationException.class);
    for (Property property : metadata.getProperties()) {
      if (property.getType().getKind().isPrimitive()) {
        code.add("      writer.write%s(",
            withInitialCapital(property.getType()), property.getName());
      } else if (String.class.getName().equals(property.getType().toString())) {
        code.add("      writer.writeString(", property.getName());
      } else {
        code.add("      writer.writeObject(", property.getName());
      }
      property.getCodeGenerator().addReadValueFragment(code, "instance." + property.getName());
      code.add(");\n");
    }
    code.addLine("    }")
        .addLine("")
        .addLine("    private static final Value_CustomFieldSerializer INSTANCE ="
            + " new Value_CustomFieldSerializer();")
        .addLine("")
        .addLine("    public static void deserialize(%s reader, %s instance) {",
            SerializationStreamReader.class, metadata.getValueType())
        .addLine("      INSTANCE.deserializeInstance(reader, instance);")
        .addLine("    }")
        .addLine("")
        .addLine("    public static %s instantiate(%s reader)",
            metadata.getValueType(), SerializationStreamReader.class)
        .addLine("        throws %s {", SerializationException.class)
        .addLine("      return INSTANCE.instantiateInstance(reader);")
        .addLine("    }")
        .addLine("")
        .addLine("    public static void serialize(%s writer, %s instance)",
            SerializationStreamWriter.class, metadata.getValueType())
        .addLine("        throws %s {", SerializationException.class)
        .addLine("      INSTANCE.serializeInstance(writer, instance);")
        .addLine("    }")
        .addLine("  }");
  }

  private void writeStubSource(SourceWriter code, Metadata metadata) {
    code.addLine("/**")
        .addLine(" * Placeholder. Create {@code %s.Builder} and subclass this type.",
            metadata.getType())
        .addLine(" */")
        .addLine("@%s(\"%s\")", Generated.class, this.getClass().getName())
        .addLine("abstract class %s {}", metadata.getGeneratedBuilder().getSimpleName());
  }

  /** Returns the correct keyword to use to inherit from the given type: implements, or extends. */
  private String getInheritanceKeyword(TypeElement type) {
    if ((type.getSuperclass().getKind() == TypeKind.NONE)
        && (!Objects.equals(type.getQualifiedName().toString(), Object.class.getName()))) {
      return "implements";
    } else {
      return "extends";
    }
  }

  private static String withInitialCapital(Object obj) {
    String s = obj.toString();
    return s.substring(0, 1).toUpperCase() + s.substring(1);
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
