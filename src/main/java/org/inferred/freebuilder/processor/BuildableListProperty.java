package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.BuildableType.maybeBuilder;
import static org.inferred.freebuilder.processor.BuildableType.PartialToBuilderMethod.TO_BUILDER_AND_MERGE;
import static org.inferred.freebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;
import static org.inferred.freebuilder.processor.BuilderMethods.addMethod;
import static org.inferred.freebuilder.processor.Util.erasesToAnyOf;
import static org.inferred.freebuilder.processor.Util.upperBound;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.Variable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
      if (type == null
          || !erasesToAnyOf(type, Collection.class, List.class, ImmutableList.class)) {
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

      return Optional.of(new BuildableListProperty(
          config.getDatatype(),
          config.getProperty(),
          element));
    }
  }

  private final BuildableType element;

  private BuildableListProperty(Datatype datatype, Property property, BuildableType element) {
    super(datatype, property);
    this.element = element;
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private final %1$s<%2$s> %3$s = new %1$s<>();",
        ArrayList.class, element.builderType(), property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addValueInstanceAdd(code);
    addBuilderAdd(code);
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
            datatype.getBuilder(), addMethod(property), element.type());
    Block body = methodBody(code, "element");
    if (element.partialToBuilder() == TO_BUILDER_AND_MERGE) {
      body.addLine("  %s.add(element.toBuilder());", property.getField());
    } else {
      body.addLine("  %s.add(%s.mergeFrom(element));",
          property.getField(),
          element.builderFactory().newBuilder(element.builderType(), EXPLICIT_TYPES));
    }
    body.addLine("  return (%s) this;", datatype.getBuilder());
    code.add(body)
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
        .addLine(" * <p>The copied builder's {@link %s build()} method will not be called until",
            element.builderType().javadocNoArgMethodLink("build"))
        .addLine(" * this object's {@link %s build() method} is, so if the builder's state is not",
            datatype.getBuilder().javadocNoArgMethodLink("build"))
        .addLine(" * legal, you will not get failures until then.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code builder} is null")
        .addLine(" */")
        .addLine("public %s %s(%s builder) {",
            datatype.getBuilder(), addMethod(property), element.builderType())
        .add(methodBody(code, "builder")
          .addLine("  %s.add(%s.mergeFrom(builder));",
              property.getField(),
              element.builderFactory().newBuilder(element.builderType(), EXPLICIT_TYPES))
          .addLine("  return (%s) this;", datatype.getBuilder()))
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    Variable fieldBuilder = new Variable(property.getName() + "Builder");
    Variable fieldElement = new Variable("element");
    code.addLine("%s<%s> %s = %s.builder();",
            ImmutableList.Builder.class,
            element.type(),
            fieldBuilder,
            ImmutableList.class)
        .addLine("for (%s %s : %s) {",
            element.builderType(), fieldElement, property.getField().on(builder))
        .addLine("  %s.add(%s.build());", fieldBuilder, fieldElement)
        .addLine("}")
        .addLine("%s = %s.build();", finalField, fieldBuilder);
  }

  @Override
  public void addMergeFromValue(Block code, String value) {
    // TODO
  }

  @Override
  public void addMergeFromBuilder(Block code, String builder) {
    // TODO
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    // TODO
  }

  @Override
  public void addClearField(Block code) {
    // TODO
  }
}