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
import static org.inferred.freebuilder.processor.Datatype.UnderrideLevel.ABSENT;
import static org.inferred.freebuilder.processor.Datatype.UnderrideLevel.FINAL;
import static org.inferred.freebuilder.processor.ToStringGenerator.addToString;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.LazyName.addLazyDefinitions;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.Datatype.StandardMethod;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.FieldAccess;
import org.inferred.freebuilder.processor.util.ObjectsExcerpts;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;

import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * Code generation for the &#64;{@link FreeBuilder} annotation.
 */
class GeneratedBuilder extends GeneratedType {

  static final FieldAccess UNSET_PROPERTIES = new FieldAccess("_unsetProperties");

  private final Datatype datatype;
  private final Map<Property, PropertyCodeGenerator> generatorsByProperty;

  GeneratedBuilder(Datatype datatype, Map<Property, PropertyCodeGenerator> generatorsByProperty) {
    this.datatype = datatype;
    this.generatorsByProperty = generatorsByProperty;
  }

  Datatype getDatatype() {
    return datatype;
  }

  public Map<Property, PropertyCodeGenerator> getGeneratorsByProperty() {
    return generatorsByProperty;
  }

  @Override
  public QualifiedName getName() {
    return datatype.getGeneratedBuilder().getQualifiedName();
  }

  @Override
  public Set<QualifiedName> getVisibleNestedTypes() {
    return datatype.getVisibleNestedTypes();
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("datatype", datatype);
    fields.add("generatorsByProperty", generatorsByProperty);
  }

  @Override
  public void addTo(SourceBuilder code) {
    addBuilderTypeDeclaration(code);
    code.addLine(" {");
    addStaticFromMethod(code);
    if (any(generatorsByProperty.values(), IS_REQUIRED)) {
      addPropertyEnum(code);
    }

    addFieldDeclarations(code);

    addAccessors(code);
    addMergeFromValueMethod(code);
    addMergeFromBuilderMethod(code);
    addClearMethod(code);
    addBuildMethod(code);
    addBuildPartialMethod(code);

    addValueType(code);
    addPartialType(code);
    for (Excerpt nestedClass : datatype.getNestedClasses()) {
      code.add(nestedClass);
    }
    addLazyDefinitions(code);
    code.addLine("}");
  }

  private void addBuilderTypeDeclaration(SourceBuilder code) {
    code.addLine("/**")
        .addLine(" * Auto-generated superclass of %s,", datatype.getBuilder().javadocLink())
        .addLine(" * derived from the API of %s.", datatype.getType().javadocLink())
        .addLine(" */")
        .add(Excerpts.generated(Processor.class));
    for (Excerpt annotation : datatype.getGeneratedBuilderAnnotations()) {
      code.add(annotation);
    }
    code.add("abstract class %s", datatype.getGeneratedBuilder().declaration());
    if (datatype.isBuilderSerializable()) {
      code.add(" implements %s", Serializable.class);
    }
  }

  private void addStaticFromMethod(SourceBuilder code) {
    BuilderFactory builderFactory = datatype.getBuilderFactory().orNull();
    if (builderFactory == null) {
      return;
    }
    code.addLine("")
        .addLine("/**")
        .addLine(" * Creates a new builder using {@code value} as a template.")
        .addLine(" */")
        .addLine("public static %s %s from(%s value) {",
            datatype.getType().declarationParameters(),
            datatype.getBuilder(),
            datatype.getType())
        .addLine("  return %s.mergeFrom(value);",
            builderFactory.newBuilder(datatype.getBuilder(), EXPLICIT_TYPES))
        .addLine("}");
  }

  private void addFieldDeclarations(SourceBuilder code) {
    code.addLine("");
    for (PropertyCodeGenerator codeGenerator : generatorsByProperty.values()) {
      codeGenerator.addBuilderFieldDeclaration(code);
    }
    // Unset properties
    if (any(generatorsByProperty.values(), IS_REQUIRED)) {
      code.addLine("private final %s<%s> %s =",
              EnumSet.class, datatype.getPropertyEnum(), UNSET_PROPERTIES)
          .addLine("    %s.allOf(%s.class);", EnumSet.class, datatype.getPropertyEnum());
    }
  }

  private void addAccessors(SourceBuilder body) {
    for (PropertyCodeGenerator codeGenerator : generatorsByProperty.values()) {
      codeGenerator.addBuilderFieldAccessors(body);
    }
  }

  private void addBuildMethod(SourceBuilder code) {
    boolean hasRequiredProperties = any(generatorsByProperty.values(), IS_REQUIRED);
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a newly-created %s based on the contents of the {@code %s}.",
            datatype.getType().javadocLink(), datatype.getBuilder().getSimpleName());
    if (hasRequiredProperties) {
      code.addLine(" *")
          .addLine(" * @throws IllegalStateException if any field has not been set");
    }
    code.addLine(" */")
        .addLine("public %s build() {", datatype.getType());
    if (hasRequiredProperties) {
      code.add(PreconditionExcerpts.checkState(
          Excerpts.add("%s.isEmpty()", UNSET_PROPERTIES), "Not set: %s", UNSET_PROPERTIES));
    }
    code.addLine("  return %s(this);", datatype.getValueType().constructor())
        .addLine("}");
  }

  private void addMergeFromValueMethod(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets all property values using the given {@code %s} as a template.",
            datatype.getType().getQualifiedName())
        .addLine(" */")
        .addLine("public %s mergeFrom(%s value) {", datatype.getBuilder(), datatype.getType());
    Block body = methodBody(code, "value");
    for (PropertyCodeGenerator codeGenerator : generatorsByProperty.values()) {
      codeGenerator.addMergeFromValue(body, "value");
    }
    body.addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addMergeFromBuilderMethod(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Copies values from the given {@code %s}.",
            datatype.getBuilder().getSimpleName())
        .addLine(" * Does not affect any properties not set on the input.")
        .addLine(" */")
        .addLine("public %1$s mergeFrom(%1$s template) {", datatype.getBuilder());
    Block body = methodBody(code, "template");
    for (PropertyCodeGenerator codeGenerator : generatorsByProperty.values()) {
      codeGenerator.addMergeFromBuilder(body, "template");
    }
    body.addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addClearMethod(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Resets the state of this builder.")
        .addLine(" */")
        .addLine("public %s clear() {", datatype.getBuilder());
    Block body = methodBody(code);
    for (PropertyCodeGenerator codeGenerator : generatorsByProperty.values()) {
      codeGenerator.addClearField(body);
    }
    if (any(generatorsByProperty.values(), IS_REQUIRED)) {
      Optional<Excerpt> defaults = Declarations.freshBuilder(body, datatype);
      if (defaults.isPresent()) {
        body.addLine("  %s.clear();", UNSET_PROPERTIES)
            .addLine("  %s.addAll(%s);", UNSET_PROPERTIES, UNSET_PROPERTIES.on(defaults.get()));
      }
    }
    body.addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
        .addLine("}");
  }

  private void addBuildPartialMethod(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns a newly-created partial %s", datatype.getType().javadocLink())
        .addLine(" * for use in unit tests. State checking will not be performed.");
    if (any(generatorsByProperty.values(), IS_REQUIRED)) {
      code.addLine(" * Unset properties will throw an {@link %s}",
              UnsupportedOperationException.class)
          .addLine(" * when accessed via the partial object.");
    }
    if (datatype.getHasToBuilderMethod()
        && datatype.getBuilderFactory().equals(Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR))) {
      code.addLine(" *")
          .addLine(" * <p>The builder returned by a partial's {@link %s#toBuilder() toBuilder}",
              datatype.getType())
          .addLine(" * method overrides {@link %s#build() build()} to return another partial.",
              datatype.getBuilder())
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
    code.addLine("public %s buildPartial() {", datatype.getType())
        .addLine("  return %s(this);", datatype.getPartialType().constructor())
        .addLine("}");
  }

  private void addPropertyEnum(SourceBuilder code) {
    code.addLine("")
        .addLine("private enum %s {", datatype.getPropertyEnum().getSimpleName());
    for (Property property : generatorsByProperty.keySet()) {
      if (generatorsByProperty.get(property).getType() == Type.REQUIRED) {
        code.addLine("  %s(\"%s\"),", property.getAllCapsName(), property.getName());
      }
    }
    code.addLine("  ;")
        .addLine("")
        .addLine("  private final %s name;", String.class)
        .addLine("")
        .addLine("  private %s(%s name) {",
            datatype.getPropertyEnum().getSimpleName(), String.class)
        .addLine("    this.name = name;")
        .addLine("  }")
        .addLine("")
        .addLine("  @%s public %s toString() {", Override.class, String.class)
        .addLine("    return name;")
        .addLine("  }")
        .addLine("}");
  }

  private void addValueType(SourceBuilder code) {
    code.addLine("");
    for (Excerpt annotation : datatype.getValueTypeAnnotations()) {
      code.add(annotation);
    }
    code.addLine("%s static final class %s %s {",
        datatype.getValueTypeVisibility(),
        datatype.getValueType().declaration(),
        extending(datatype.getType(), datatype.isInterfaceType()));
    // Fields
    for (Property property : generatorsByProperty.keySet()) {
      generatorsByProperty.get(property).addValueFieldDeclaration(code, property.getField());
    }
    // Constructor
    code.addLine("")
        .addLine("  private %s(%s builder) {",
            datatype.getValueType().getSimpleName(),
            datatype.getGeneratedBuilder());
    Block body = methodBody(code, "builder");
    for (Property property : generatorsByProperty.keySet()) {
      generatorsByProperty.get(property)
          .addFinalFieldAssignment(body, property.getField().on("this"), "builder");
    }
    code.add(body)
        .addLine("  }");
    // Getters
    for (Property property : generatorsByProperty.keySet()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      generatorsByProperty.get(property).addAccessorAnnotations(code);
      generatorsByProperty.get(property).addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      code.add("    return ");
      generatorsByProperty.get(property).addReadValueFragment(code, property.getField());
      code.add(";\n");
      code.addLine("  }");
    }
    // toBuilder
    if (datatype.getHasToBuilderMethod()) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s toBuilder() {", datatype.getBuilder());
      BuilderFactory builderFactory = datatype.getBuilderFactory().orNull();
      if (builderFactory != null) {
        code.addLine("    return %s.mergeFrom(this);",
                builderFactory.newBuilder(datatype.getBuilder(), EXPLICIT_TYPES));
      } else {
        code.addLine("    throw new %s();", UnsupportedOperationException.class);
      }
      code.addLine("  }");
    }
    // Equals
    switch (datatype.standardMethodUnderride(StandardMethod.EQUALS)) {
      case ABSENT:
        addValueTypeEquals(code);
        break;

      case OVERRIDEABLE:
        // Partial-respecting override if a non-final user implementation exists.
        code.addLine("")
            .addLine("  @%s", Override.class)
            .addLine("  public boolean equals(Object obj) {")
            .addLine("    return (!(obj instanceof %s) && super.equals(obj));",
                datatype.getPartialType().getQualifiedName())
            .addLine("  }");
        break;

      case FINAL:
        // Cannot override if a final user implementation exists.
        break;
    }
    // Hash code
    if (datatype.standardMethodUnderride(StandardMethod.HASH_CODE) == ABSENT) {
      FieldAccessList fields = getFields(generatorsByProperty.keySet());
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public int hashCode() {")
          .addLine("    return %s.hash(%s);", Objects.class, fields)
          .addLine("  }");
    }
    // toString
    if (datatype.standardMethodUnderride(StandardMethod.TO_STRING) == ABSENT) {
      addToString(code, datatype, generatorsByProperty, false);
    }
    code.addLine("}");
  }

  private void addValueTypeEquals(SourceBuilder code) {
    // Default implementation if no user implementation exists.
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public boolean equals(Object obj) {");
    Block body = methodBody(code, "obj");
    body.addLine("    if (!(obj instanceof %s)) {", datatype.getValueType().getQualifiedName())
        .addLine("      return false;")
        .addLine("    }")
        .addLine("    %1$s other = (%1$s) obj;", datatype.getValueType().withWildcards());
    if (generatorsByProperty.isEmpty()) {
      body.addLine("    return true;");
    } else {
      String prefix = "    return ";
      for (Property property : generatorsByProperty.keySet()) {
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

  private void addPartialType(SourceBuilder code) {
    boolean hasRequiredProperties = any(generatorsByProperty.values(), IS_REQUIRED);
    code.addLine("")
        .addLine("private static final class %s %s {",
            datatype.getPartialType().declaration(),
            extending(datatype.getType(), datatype.isInterfaceType()));
    // Fields
    for (Property property : generatorsByProperty.keySet()) {
      generatorsByProperty.get(property).addValueFieldDeclaration(code, property.getField());
    }
    if (hasRequiredProperties) {
      code.addLine("  private final %s<%s> %s;",
          EnumSet.class, datatype.getPropertyEnum(), UNSET_PROPERTIES);
    }
    addPartialConstructor(code, hasRequiredProperties);
    // Getters
    for (Property property : generatorsByProperty.keySet()) {
      code.addLine("")
          .addLine("  @%s", Override.class);
      generatorsByProperty.get(property).addAccessorAnnotations(code);
      generatorsByProperty.get(property).addGetterAnnotations(code);
      code.addLine("  public %s %s() {", property.getType(), property.getGetterName());
      if (generatorsByProperty.get(property).getType() == Type.REQUIRED) {
        code.addLine("    if (%s.contains(%s.%s)) {",
                UNSET_PROPERTIES, datatype.getPropertyEnum(), property.getAllCapsName())
            .addLine("      throw new %s(\"%s not set\");",
                UnsupportedOperationException.class, property.getName())
            .addLine("    }");
      }
      code.add("    return ");
      generatorsByProperty.get(property).addReadValueFragment(code, property.getField());
      code.add(";\n");
      code.addLine("  }");
    }
    addPartialToBuilderMethod(code);
    // Equals
    if (datatype.standardMethodUnderride(StandardMethod.EQUALS) != FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public boolean equals(Object obj) {");
      Block body = methodBody(code, "obj");
      body.addLine("    if (!(obj instanceof %s)) {", datatype.getPartialType().getQualifiedName())
          .addLine("      return false;")
          .addLine("    }")
          .addLine("    %1$s other = (%1$s) obj;", datatype.getPartialType().withWildcards());
      if (generatorsByProperty.isEmpty()) {
        body.addLine("    return true;");
      } else {
        String prefix = "    return ";
        for (Property property : generatorsByProperty.keySet()) {
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
    if (datatype.standardMethodUnderride(StandardMethod.HASH_CODE) != FINAL) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public int hashCode() {");

      FieldAccessList fields = getFields(generatorsByProperty.keySet());
      if (hasRequiredProperties) {
        fields = fields.plus(UNSET_PROPERTIES);
      }
      code.addLine("    return %s.hash(%s);", Objects.class, fields)
          .addLine("  }");
    }
    // toString
    if (datatype.standardMethodUnderride(StandardMethod.TO_STRING) != FINAL) {
      addToString(code, datatype, generatorsByProperty, true);
    }
    code.addLine("}");
  }

  private void addPartialConstructor(SourceBuilder code, boolean hasRequiredProperties) {
    code.addLine("")
        .addLine("  %s(%s builder) {",
            datatype.getPartialType().getSimpleName(),
            datatype.getGeneratedBuilder());
    Block body = methodBody(code, "builder");
    for (Property property : generatorsByProperty.keySet()) {
      generatorsByProperty.get(property)
          .addPartialFieldAssignment(body, property.getField().on("this"), "builder");
    }
    if (hasRequiredProperties) {
      body.addLine("    %s = %s.clone();",
          UNSET_PROPERTIES.on("this"), UNSET_PROPERTIES.on("builder"));
    }
    code.add(body)
        .addLine("  }");
  }

  private void addPartialToBuilderMethod(SourceBuilder code) {
    if (!datatype.getHasToBuilderMethod()) {
      return;
    }
    if (datatype.isExtensible()) {
      code.addLine("")
          .addLine("  private static class PartialBuilder%s extends %s {",
              datatype.getType().declarationParameters(), datatype.getBuilder())
          .addLine("    @Override public %s build() {", datatype.getType())
          .addLine("      return buildPartial();")
          .addLine("    }")
          .addLine("  }");
    }
    code.addLine("")
        .addLine("  @%s", Override.class)
        .addLine("  public %s toBuilder() {", datatype.getBuilder());
    Block body = methodBody(code);
    Variable builder = new Variable("builder");
    if (datatype.isExtensible()) {
      code.addLine("    %s builder = new PartialBuilder%s();",
              datatype.getBuilder(), datatype.getBuilder().typeParametersOrDiamondOperator());
      for (PropertyCodeGenerator codeGenerator : generatorsByProperty.values()) {
        codeGenerator.addSetBuilderFromPartial(body, builder);
      }
      body.addLine("    return %s;", builder);
    } else {
      body.addLine("    throw new %s();", UnsupportedOperationException.class);
    }
    code.add(body)
        .addLine("  }");
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

  private static FieldAccessList getFields(Collection<Property> properties) {
    ImmutableList.Builder<FieldAccess> fieldAccesses = ImmutableList.builder();
    for (Property property : properties) {
      fieldAccesses.add(property.getField());
    }
    return new FieldAccessList(fieldAccesses.build());
  }

  private static final Predicate<PropertyCodeGenerator> IS_REQUIRED =
      generator -> generator.getType() == Type.REQUIRED;
}
