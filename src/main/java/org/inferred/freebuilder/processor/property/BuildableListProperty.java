package org.inferred.freebuilder.processor.property;

import static org.inferred.freebuilder.processor.BuildableType.maybeBuilder;
import static org.inferred.freebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;
import static org.inferred.freebuilder.processor.BuilderMethods.addAllBuildersOfMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.addAllMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.addMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getBuildersMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.mutator;
import static org.inferred.freebuilder.processor.model.ModelUtils.erasesToAnyOf;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.model.ModelUtils.needsSafeVarargs;
import static org.inferred.freebuilder.processor.model.ModelUtils.overrides;
import static org.inferred.freebuilder.processor.model.ModelUtils.upperBound;
import static org.inferred.freebuilder.processor.source.feature.GuavaLibrary.GUAVA;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.BuildableType;
import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.Declarations;
import org.inferred.freebuilder.processor.excerpt.BuildableList;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.Type;
import org.inferred.freebuilder.processor.source.Variable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.BaseStream;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * {@link PropertyCodeGenerator} providing fluent methods for {@link List} properties
 * containing {@link BuildableType} instances.
 */
class BuildableListProperty extends PropertyCodeGenerator {

  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<BuildableListProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orElse(null);
      if (!erasesToAnyOf(type, Collection.class, List.class, ImmutableList.class)) {
        return Optional.empty();
      }
      if (disablingGetterExists(config)) {
        return Optional.empty();
      }

      TypeMirror rawElementType = upperBound(config.getElements(), type.getTypeArguments().get(0));
      DeclaredType elementType = maybeDeclared(rawElementType).orElse(null);
      if (elementType == null) {
        return Optional.empty();
      }

      DeclaredType elementBuilder =
          maybeBuilder(elementType, config.getElements(), config.getTypes()).orElse(null);
      if (elementBuilder == null) {
        return Optional.empty();
      }

      BuildableType element = BuildableType
          .create(elementType, elementBuilder, config.getElements(), config.getTypes());
      boolean needsSafeVarargs = needsSafeVarargs(rawElementType);
      boolean overridesValueInstanceVarargsAddMethod =
          hasValueInstanceVarargsAddMethodOverride(config, rawElementType);
      boolean overridesBuilderVarargsAddMethod =
          hasBuilderVarargsAddMethodOverride(config, element.builderType());

      return Optional.of(new BuildableListProperty(
          config.getDatatype(),
          config.getProperty(),
          needsSafeVarargs,
          overridesValueInstanceVarargsAddMethod,
          overridesBuilderVarargsAddMethod,
          element));
    }

    private static boolean disablingGetterExists(Config config) {
      String getterName = config.getProperty().getGetterName();
      return overrides(config.getBuilder(), config.getTypes(), getterName);
    }

    private static boolean hasValueInstanceVarargsAddMethodOverride(
        Config config, TypeMirror elementType) {
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          addMethod(config.getProperty()),
          config.getTypes().getArrayType(elementType));
    }

    private static boolean hasBuilderVarargsAddMethodOverride(Config config, Type builderType) {
      TypeMirror rawBuilderType = config.getElements()
          .getTypeElement(builderType.getQualifiedName().toString())
          .asType();
      return overrides(
          config.getBuilder(),
          config.getTypes(),
          addMethod(config.getProperty()),
          config.getTypes().getArrayType(rawBuilderType));
    }
  }

  private final boolean needsSafeVarargs;
  private final boolean overridesValueInstanceVarargsAddMethod;
  private final boolean overridesBuilderVarargsAddMethod;
  private final BuildableType element;

  private BuildableListProperty(
      Datatype datatype,
      Property property,
      boolean needsSafeVarargs,
      boolean overridesValueInstanceVarargsAddMethod,
      boolean overridesBuilderVarargsAddMethod,
      BuildableType element) {
    super(datatype, property);
    this.needsSafeVarargs = needsSafeVarargs;
    this.overridesValueInstanceVarargsAddMethod = overridesValueInstanceVarargsAddMethod;
    this.overridesBuilderVarargsAddMethod = overridesBuilderVarargsAddMethod;
    this.element = element;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private final %1$s %2$s = new %1$s();",
        BuildableList.of(element), property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addValueInstanceAdd(code);
    addBuilderAdd(code);
    addValueInstanceVarargsAdd(code);
    addBuilderVarargsAdd(code);
    addSpliteratorValueInstanceAddAll(code);
    addSpliteratorBuilderAddAll(code);
    addIterableValueInstanceAddAll(code);
    addIterableBuilderAddAll(code);
    addStreamValueInstanceAddAll(code);
    addStreamBuilderAddAll(code);
    addMutate(code);
    addClear(code);
    addGetter(code);
  }

  private void addValueInstanceAdd(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds {@code element} to the list to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>The element <em>may</em> be converted to/from a builder in this process;")
        .addLine(" * you should not rely on the instance you provide being (or not being) present")
        .addLine(" * in the final list.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code element} is null")
        .addLine(" */")
        .addLine("public %s %s(%s element) {",
            datatype.getBuilder(), addMethod(property), element.type())
        .addLine("  %s.addValue(element);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addBuilderAdd(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Adds the value built by {@code builder} to the list to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>Only a copy of the builder will be stored; any changes made to it after")
        .addLine(" * returning from this method will not affect the value stored in the list.")
        .addLine(" *")
        .addLine(" * <p>The copied builder's %s will not be called until",
            element.builderType().javadocNoArgMethodLink("build").withText("build method"))
        .addLine(" * this object's %s is, so if the builder's state is not",
            datatype.getBuilder().javadocNoArgMethodLink("build").withText("build method"))
        .addLine(" * legal, you will not get failures until then.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code builder} is null")
        .addLine(" */")
        .addLine("public %s %s(%s builder) {",
            datatype.getBuilder(), addMethod(property), element.builderType())
        .addLine("  %s.add(%s.mergeFrom(builder));",
            property.getField(),
            element.builderFactory().newBuilder(element.builderType(), EXPLICIT_TYPES))
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addJavadocForAddingMultipleValues(SourceBuilder code) {
    code.addLine("/**")
        .addLine(" * Adds each element of {@code elements} to the list to be returned from")
        .addLine(" * %s.", datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>Each element <em>may</em> be converted to/from a builder in this process;")
        .addLine(" * you should not rely on any of the instances you provide being (or not being)")
        .addLine(" * present in the final list.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code elements} is null or contains a")
        .addLine(" *     null element")
        .addLine(" */");
  }

  private void addJavadocForAddingMultipleBuilders(SourceBuilder code) {
    code.addLine("/**")
        .addLine(" * Adds the values built by each element of {@code elementBuilders} to")
        .addLine(" * the list to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>Only copies of the builders will be stored; any changes made to them after")
        .addLine(" * returning from this method will not affect the values stored in the list.")
        .addLine(" *")
        .addLine(" * <p>The copied builders' %s will not be called until",
            element.builderType().javadocNoArgMethodLink("build").withText("build methods"))
        .addLine(" * this object's %s is, so if any builder's state is not",
            datatype.getBuilder().javadocNoArgMethodLink("build").withText("build method"))
        .addLine(" * legal, you will not get failures until then.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code elementBuilders} is null or contains a")
        .addLine(" *     null element")
        .addLine(" */");
  }

  private void addValueInstanceVarargsAdd(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleValues(code);
    addSafeVarargsForPublicMethod(code, overridesValueInstanceVarargsAddMethod);
    code.add("%s %s(%s... elements) {%n",
            datatype.getBuilder(), addMethod(property), element.type())
        .addLine("  return %s(%s.asList(elements));", addAllMethod(property), Arrays.class)
        .addLine("}");
  }

  private void addBuilderVarargsAdd(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleBuilders(code);
    addSafeVarargsForPublicMethod(code, overridesBuilderVarargsAddMethod);
    code.add("%s %s(%s... elementBuilders) {%n",
            datatype.getBuilder(), addMethod(property), element.builderType())
        .addLine("  return %s(%s.asList(elementBuilders));",
            addAllBuildersOfMethod(property), Arrays.class)
        .addLine("}");
  }

  private void addSafeVarargsForPublicMethod(SourceBuilder code, boolean isOverridden) {
    if (needsSafeVarargs) {
      if (!isOverridden) {
        code.addLine("@%s", SafeVarargs.class)
            .addLine("@%s({\"varargs\"})", SuppressWarnings.class);
      } else {
        code.addLine("@%s({\"unchecked\", \"varargs\"})", SuppressWarnings.class);
      }
    }
    code.add("public ");
    if (needsSafeVarargs && !isOverridden) {
      code.add("final ");
    }
  }

  private void addSpliteratorValueInstanceAddAll(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleValues(code);
    code.addLine("public %s %s(%s<? extends %s> elements) {",
            datatype.getBuilder(), addAllMethod(property), Spliterator.class, element.type())
        .addLine("  if ((elements.characteristics() & %s.SIZED) != 0) {", Spliterator.class);
    Variable newSize = new Variable("newSize");
    code.addLine("    long %s = elements.estimateSize() + %s.size();", newSize, property.getField())
        .addLine("    if (%s <= Integer.MAX_VALUE) {", newSize)
        .addLine("      %s.ensureCapacity((int) %s);", property.getField(), newSize)
        .addLine("    }")
        .addLine("  }")
        .addLine("  elements.forEachRemaining(this::%s);", addMethod(property))
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addSpliteratorBuilderAddAll(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleBuilders(code);
    Variable newSize = new Variable("newSize");
    code.addLine("public %s %s(%s<? extends %s> elementBuilders) {",
            datatype.getBuilder(),
            addAllBuildersOfMethod(property),
            Spliterator.class,
            element.builderType())
        .addLine("  if ((elementBuilders.characteristics() & %s.SIZED) != 0) {", Spliterator.class)
        .addLine("    long %s = elementBuilders.estimateSize() + %s.size();",
            newSize, property.getField())
        .addLine("    if (%s <= Integer.MAX_VALUE) {", newSize)
        .addLine("      %s.ensureCapacity((int) %s);", property.getField(), newSize)
        .addLine("    }")
        .addLine("  }")
        .addLine("  elementBuilders.forEachRemaining(this::%s);", addMethod(property))
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addIterableValueInstanceAddAll(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleValues(code);
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s<? extends %s> elements) {",
            datatype.getBuilder(), addAllMethod(property), Iterable.class, element.type());
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("  %s.addAllValues(elements);", property.getField())
          .addLine("  return (%s) this;", datatype.getBuilder());
    } else {
      code.addLine("  return %s(elements.spliterator());", addAllMethod(property));
    }
    code.addLine("}");
  }

  private void addIterableBuilderAddAll(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleBuilders(code);
    code.addLine("public %s %s(%s<? extends %s> elementBuilders) {",
            datatype.getBuilder(),
            addAllBuildersOfMethod(property),
            Iterable.class,
            element.builderType())
        .addLine("  return %s(elementBuilders.spliterator());", addAllBuildersOfMethod(property))
        .addLine("}");
  }

  private void addStreamValueInstanceAddAll(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleValues(code);
    code.addLine("public %s %s(%s<? extends %s, ?> elements) {",
            datatype.getBuilder(), addAllMethod(property), BaseStream.class, element.type())
        .addLine("  return %s(elements.spliterator());", addAllMethod(property))
        .addLine("}");
  }

  private void addStreamBuilderAddAll(SourceBuilder code) {
    code.addLine("");
    addJavadocForAddingMultipleBuilders(code);
    code.addLine("public %s %s(%s<? extends %s, ?> elementBuilders) {",
            datatype.getBuilder(),
            addAllBuildersOfMethod(property),
            BaseStream.class,
            element.builderType())
        .addLine("  return %s(elementBuilders.spliterator());", addAllBuildersOfMethod(property))
        .addLine("}");
  }

  private void addMutate(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Applies {@code mutator} to a list of builders for the elements of the list")
        .addLine(" * that will be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * <p>This method mutates the list in-place. {@code mutator} is a void")
        .addLine(" * consumer, so any value returned from a lambda will be ignored. Take care")
        .addLine(" * not to call pure functions, like %s.",
            Type.from(Collection.class).javadocNoArgMethodLink("stream"))
        .addLine(" *")
        .addLine(" * @return this {@code Builder} object")
        .addLine(" * @throws NullPointerException if {@code mutator} is null")
        .addLine(" */")
        .addLine("public %s %s(%s<? super %s<%s>> mutator) {",
            datatype.getBuilder(),
            mutator(property),
            Consumer.class,
            List.class,
            element.builderType())
        .addLine("  mutator.accept(%s);", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Clears the list to be returned from %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", datatype.getBuilder(), clearMethod(property))
        .addLine("  %s.clear();", property.getField())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns an unmodifiable list of mutable builders for the elements of the")
        .addLine(" * list that will be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * Changes to this builder will be reflected in the view, and changes to")
        .addLine(" * the element builders in the view will affect this builder.")
        .addLine(" */")
        .addLine("public %s<%s> %s() {",
            List.class, element.builderType(), getBuildersMethod(property))
        .addLine("  return %s.unmodifiableList(%s);", Collections.class, property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    addFieldAssignment(code, finalField, builder, "build");
  }

  @Override
  public void addPartialFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    addFieldAssignment(code, finalField, builder, "buildPartial");
  }

  private void addFieldAssignment(
      SourceBuilder code,
      Excerpt finalField,
      String builder,
      String buildMethod) {
    code.addLine("%s = %s.%s();", finalField, property.getField().on(builder), buildMethod);
  }

  @Override
  public void addAssignToBuilder(SourceBuilder code, Variable builder) {
    code.addLine("%s.%s(%s);", builder, addAllMethod(property), property.getField());
  }

  @Override
  public void addMergeFromValue(SourceBuilder code, String value) {
    code.addLine("%s(%s.%s());", addAllMethod(property), value, property.getGetterName());
  }

  @Override
  public void addMergeFromBuilder(SourceBuilder code, String builder) {
    Excerpt base = Declarations.upcastToGeneratedBuilder(code, datatype, builder);
    code.addLine("%s(%s);", addAllBuildersOfMethod(property), property.getField().on(base));
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, addAllMethod(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    code.addLine("%s();", clearMethod(property));
  }
}
