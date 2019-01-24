package org.inferred.freebuilder.processor.property;

import static com.google.common.primitives.Primitives.wrap;

import static org.inferred.freebuilder.processor.BuilderMethods.clearMethod;
import static org.inferred.freebuilder.processor.BuilderMethods.getter;
import static org.inferred.freebuilder.processor.BuilderMethods.mapper;
import static org.inferred.freebuilder.processor.BuilderMethods.setter;
import static org.inferred.freebuilder.processor.model.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.model.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.model.ModelUtils.override;
import static org.inferred.freebuilder.processor.source.FunctionalType.functionalTypesAcceptedByMethod;
import static org.inferred.freebuilder.processor.source.FunctionalType.isAssignable;
import static org.inferred.freebuilder.processor.source.FunctionalType.primitiveUnaryOperator;
import static org.inferred.freebuilder.processor.source.FunctionalType.unaryOperator;

import com.google.common.annotations.VisibleForTesting;

import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.Declarations;
import org.inferred.freebuilder.processor.model.MethodIntrospector;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.FieldAccess;
import org.inferred.freebuilder.processor.source.FunctionalType;
import org.inferred.freebuilder.processor.source.QualifiedName;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.TypeClass;
import org.inferred.freebuilder.processor.source.Variable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic.Kind;

/**
 * This property class handles the primitive optional fields, including
 * {@link OptionalDouble}, {@link OptionalLong}, and {@link OptionalInt}.
 */
public class PrimitiveOptionalProperty extends PropertyCodeGenerator {
  static class Factory implements PropertyCodeGenerator.Factory {

    @Override
    public Optional<PrimitiveOptionalProperty> create(Config config) {
      DeclaredType type = maybeDeclared(config.getProperty().getType()).orElse(null);
      if (type == null || !type.getTypeArguments().isEmpty()) {
        return Optional.empty();
      }

      QualifiedName typename = QualifiedName.of(asElement(type));
      OptionalType optionalType = Arrays.stream(OptionalType.values())
          .filter(candidate -> candidate.type.getQualifiedName().equals(typename))
          .findAny()
          .orElse(null);
      if (optionalType == null) {
        return Optional.empty();
      }

      checkForInfiniteLoop(config, optionalType);

      // Implement mapper methods for primitive, boxed and/or optional unary operators
      PrimitiveType primitiveType = config.getTypes().getPrimitiveType(optionalType.primitiveKind);
      FunctionalType primitiveOperator = primitiveUnaryOperator(primitiveType);
      FunctionalType optionalOperator = unaryOperator(type);
      List<FunctionalType> declaredOperators = functionalTypesAcceptedByMethod(
          config.getBuilder(),
          mapper(config.getProperty()),
          config.getElements(),
          config.getTypes());
      Optional<FunctionalType> declaredPrimitiveOperator = declaredOperators.stream()
          .filter(t -> isAssignable(t, primitiveOperator, config.getTypes()))
          .findAny();
      Optional<FunctionalType> declaredOptionalOperator = declaredOperators.stream()
          .filter(t -> isAssignable(t, optionalOperator, config.getTypes()))
          .findAny();
      if (!declaredPrimitiveOperator.isPresent() && !declaredOptionalOperator.isPresent()) {
        declaredPrimitiveOperator = Optional.of(primitiveOperator);
      }

      return Optional.of(new PrimitiveOptionalProperty(
          config.getDatatype(),
          config.getProperty(),
          optionalType,
          declaredPrimitiveOperator,
          declaredOptionalOperator));
    }

    private static void checkForInfiniteLoop(Config config, OptionalType optional) {
      ExecutableElement override = override(
          config.getBuilder(),
          config.getTypes(),
          setter(config.getProperty()),
          config.getTypes().getPrimitiveType(optional.primitiveKind)).orElse(null);
      if (override == null) {
        return;
      }
      MethodIntrospector.instance(config.getEnvironment())
          .visitAllOwnMethodInvocations(override, (methodName, logger) -> {
            if (setter(config.getProperty()).contentEquals(methodName)) {
              logger.logMessage(Kind.ERROR, "Infinite recursive loop detected");
            }
          });
    }
  }

  @VisibleForTesting
  enum OptionalType {
    INT(OptionalInt.class, int.class, TypeKind.INT, "getAsInt"),
    LONG(OptionalLong.class, long.class, TypeKind.LONG, "getAsLong"),
    DOUBLE(OptionalDouble.class, double.class, TypeKind.DOUBLE, "getAsDouble");

    private final TypeClass type;
    private final Class<?> primitiveType;
    private final TypeKind primitiveKind;
    private final String getter;

    OptionalType(
        Class<?> optionalType,
        Class<?> primitiveType,
        TypeKind primitiveKind,
        String getter) {
      this.type = TypeClass.fromNonGeneric(optionalType);
      this.primitiveType = primitiveType;
      this.primitiveKind = primitiveKind;
      this.getter = getter;
    }
  }

  private final OptionalType optional;
  private final Optional<FunctionalType> primitiveOperator;
  private final Optional<FunctionalType> optionalOperator;

  @VisibleForTesting
  PrimitiveOptionalProperty(
      Datatype datatype,
      Property property,
      OptionalType optional,
      Optional<FunctionalType> primitiveOperator,
      Optional<FunctionalType> optionalOperator) {
    super(datatype, property);
    this.optional = optional;
    this.primitiveOperator = primitiveOperator;
    this.optionalOperator = optionalOperator;
  }

  @Override
  public Initially initialState() {
    return Initially.OPTIONAL;
  }

  @Override
  public void addValueFieldDeclaration(SourceBuilder code, FieldAccess finalField) {
    code.addLine("private final %s %s;", optional.type, finalField);
  }

  @Override
  public void addBuilderFieldDeclaration(SourceBuilder code) {
    code.addLine("private %1$s %2$s = %1$s.empty();", optional.type, property.getField());
  }

  @Override
  public void addBuilderFieldAccessors(SourceBuilder code) {
    addSetter(code);
    addOptionalSetter(code);
    primitiveOperator.ifPresent(operator -> addPrimitiveMapper(code, operator));
    optionalOperator.ifPresent(operator -> addOptionalMapper(code, operator));
    addClear(code);
    addGetter(code);
  }

  private void addSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s(%s %s) {",
            datatype.getBuilder(), setter(property), optional.primitiveType, property.getName())
        .addLine("  %s = %s.of(%s);", property.getField(), optional.type, property.getName())
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addOptionalSetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws %s if {@code %s} is null",
            NullPointerException.class, property.getName())
        .addLine(" */");
    addAccessorAnnotations(code);
    code.addLine("public %s %s(%s %s) {",
            datatype.getBuilder(), setter(property), optional.type, property.getName())
        .addLine("  if (%s.isPresent()) {", property.getName())
        .addLine("    return %s(%s.%s());", setter(property), property.getName(), optional.getter)
        .addLine("  } else {")
        .addLine("    return %s();", clearMethod(property))
        .addLine("  }")
        .addLine("}");
  }

  private void addPrimitiveMapper(SourceBuilder code, FunctionalType mapperType) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * If the value to be returned by %s is present,",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * replaces it by applying {@code mapper} to it and using the result.");
    if (mapperType.canReturnNull()) {
      code.addLine(" *")
          .addLine(" * <p>If the result is null, clears the value.");
    }
    code.addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mapper} is null")
        .addLine(" */")
        .addLine("public %s %s(%s mapper) {",
            datatype.getBuilder(), mapper(property), mapperType.getFunctionalInterface())
        .addLine("  %s.requireNonNull(mapper);", Objects.class);
    Variable value = new Variable("value");
    if (mapperType.canReturnNull()) {
      Variable result = new Variable("result");
      code.addLine("  %s.ifPresent(%s -> {", property.getField(), value)
          .addLine("    %s %s = mapper.%s(%s);",
              wrap(optional.primitiveType), result, mapperType.getMethodName(), value)
          .addLine("    if (%s != null) {", result)
          .addLine("      %s(%s);", setter(property), result)
          .addLine("    } else {")
          .addLine("      %s();", clearMethod(property))
          .addLine("    }")
          .addLine("  });");
    } else {
      code.addLine("  %1$s.ifPresent(%2$s -> %3$s(mapper.%4$s(%2$s)));",
              property.getField(), value, setter(property), mapperType.getMethodName());
    }
    code.addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addOptionalMapper(SourceBuilder code, FunctionalType mapperType) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Replaces the value to be returned by %s",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" * by applying {@code mapper} to it and using the result.")
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" * @throws NullPointerException if {@code mapper} is null or returns null")
        .addLine(" */")
        .addLine("public %s %s(%s mapper) {",
            datatype.getBuilder(), mapper(property), mapperType.getFunctionalInterface())
        .addLine("  return %s(mapper.%s(%s()));",
            setter(property), mapperType.getMethodName(), getter(property))
        .addLine("}");
  }

  private void addClear(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Sets the value to be returned by %s to %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()),
            optional.type.javadocNoArgMethodLink("empty"))
        .addLine(" *")
        .addLine(" * @return this {@code %s} object", datatype.getBuilder().getSimpleName())
        .addLine(" */")
        .addLine("public %s %s() {", datatype.getBuilder(), clearMethod(property))
        .addLine("  %s = %s.empty();", property.getField(), optional.type)
        .addLine("  return (%s) this;", datatype.getBuilder())
        .addLine("}");
  }

  private void addGetter(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * Returns the value that will be returned by %s.",
            datatype.getType().javadocNoArgMethodLink(property.getGetterName()))
        .addLine(" */")
        .addLine("public %s %s() {", property.getType(), getter(property))
        .addLine("  return %s;", property.getField())
        .addLine("}");
  }

  @Override
  public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
    code.addLine("%s = %s;", finalField, property.getField().on(builder));
  }

  @Override
  public void addMergeFromValue(SourceBuilder code, String value) {
    code.addLine("%s.%s().ifPresent(this::%s);", value, property.getGetterName(), setter(property));
  }

  @Override
  public void addMergeFromBuilder(SourceBuilder code, String builder) {
    code.addLine("%s.%s().ifPresent(this::%s);", builder, getter(property), setter(property));
  }

  @Override
  public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
    code.addLine("%s.%s(%s);", builder, setter(property), variable);
  }

  @Override
  public void addClearField(SourceBuilder code) {
    Optional<Variable> defaults = Declarations.freshBuilder(code, datatype);
    if (defaults.isPresent()) {
      code.addLine("%s = %s;", property.getField(), property.getField().on(defaults.get()));
    } else {
      code.addLine("%s = %s.empty();", property.getField(), optional.type);
    }
  }

  @Override
  public void addToStringCondition(SourceBuilder code) {
    code.add("%s.isPresent()", property.getField());
  }

  @Override
  public void addToStringValue(SourceBuilder code) {
    code.add("%s.%s()", property.getField(), optional.getter);
  }
}
