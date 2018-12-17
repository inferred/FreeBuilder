package org.inferred.freebuilder.processor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import org.inferred.freebuilder.processor.util.*;

import javax.lang.model.type.TypeMirror;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.inferred.freebuilder.processor.BuilderMethods.*;
import static org.inferred.freebuilder.processor.util.Block.methodBody;

/**
 * This property class handles the primitive optional fields, including
 * {@link OptionalDouble}, {@link OptionalLong}, and {@link OptionalInt}.
 */
public class PrimitiveOptionalProperty extends PropertyCodeGenerator {
    @VisibleForTesting
    enum OptionalType {
        INT(OptionalInt.class, "int"),
        LONG(OptionalLong.class, "long"),
        DOUBLE(OptionalDouble.class, "double");

        private final QualifiedName cls;
        private final String empty = "empty";
        private final String ofNullable = "of";
        private final String primitiveType;
        private final String unaryType;
        private final String unaryMethod;

        OptionalType(Class<?> type, String primitiveType) {
            this.cls = QualifiedName.of(type);
            this.primitiveType = primitiveType;

            String upperCamelName = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, primitiveType);
            this.unaryType = upperCamelName + "UnaryOperator";
            this.unaryMethod = "applyAs" + upperCamelName;
        }

        public static OptionalType lookup(TypeMirror elementType) {
            String type = elementType.toString();

            for (OptionalType op : values()) {
                if (type.contains(op.cls.getSimpleName())) {
                    return op;
                }
            }

            throw new IllegalStateException("Not a supported type: " + type);
        }

        protected void applyMapper(SourceBuilder code, Metadata metadata, Metadata.Property property) {
            code.addLine("  if(%s().isPresent()) {", getter(property));
            code.addLine("    return %s(mapper.%s(%s().get()));", setter(property), unaryMethod, getter(property));
            code.addLine("  }");
            code.addLine("  return (%s) this;", metadata.getBuilder());
        }

        protected void invokeIfPresent(SourceBuilder code, String value, String method) {
            code.addLine("%s.ifPresent(this::%s);", value, method);
        }
    }

    @VisibleForTesting
    PrimitiveOptionalProperty(Metadata metadata, Metadata.Property property) {
        super(metadata, property);
        this.elementType = property.getType();
        this.optional = OptionalType.lookup(this.elementType);
    }

    private final OptionalType optional;
    private final TypeMirror elementType;

    @Override
    public Type getType() {
        return Type.OPTIONAL;
    }

    @Override
    public void addValueFieldDeclaration(SourceBuilder code, FieldAccess finalField) {
        code.addLine("// Store a nullable object instead of an Optional. Escape analysis then")
                .addLine("// allows the JVM to optimize away the Optional objects created by our")
                .addLine("// getter method.")
                .addLine("private final %s %s;", elementType, finalField);
    }

    @Override
    public void addBuilderFieldDeclaration(SourceBuilder code) {
        code.addLine("// Store a nullable object instead of an Optional. Escape analysis then")
                .addLine("// allows the JVM to optimize away the Optional objects created by and")
                .addLine("// passed to our API.")
                .addLine("private %s %s = %s.%s();", elementType, property.getField(), elementType, optional.empty);
    }

    @Override
    public void addBuilderFieldAccessors(SourceBuilder code) {
        addSetter(code, metadata);
        addOptionalSetter(code, metadata);
        addMapper(code, metadata);
        addClear(code, metadata);
        addGetter(code, metadata);
    }

    private void addSetter(SourceBuilder code, Metadata metadata) {
        code.addLine("")
                .addLine("/**")
                .addLine(" * Sets the value to be returned by %s.",
                        metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
                .addLine(" *")
                .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName());

        code.addLine(" */")
                .addLine("public %s %s(%s %s) {",
                        metadata.getBuilder(),
                        setter(property),
                        optional.primitiveType,
                        property.getName());
        Block body = methodBody(code, property.getName());
        body.addLine("  %s = %s.%s(%s);", property.getField(), elementType, optional.ofNullable, property.getName());

        body.addLine("  return (%s) this;", metadata.getBuilder());
        code.add(body)
                .addLine("}");
    }

    private void addOptionalSetter(SourceBuilder code, Metadata metadata) {
        code.addLine("")
                .addLine("/**")
                .addLine(" * Sets the value to be returned by %s.",
                        metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
                .addLine(" *")
                .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
                .addLine(" */");
        addAccessorAnnotations(code);
        code.addLine("public %s %s(%s %s) {",
                metadata.getBuilder(),
                setter(property),
                optional.cls,
                property.getName())
                .add(methodBody(code, property.getName())
                        .addLine("  if (%s.isPresent()) {", property.getName())
                        .addLine("    return %s(%s.get());", setter(property), property.getName())
                        .addLine("  } else {")
                        .addLine("    return %s();", clearMethod(property))
                        .addLine("  }"))
                .addLine("}");
    }

    private void addMapper(SourceBuilder code, Metadata metadata) {
        code.addLine("")
                .addLine("/**")
                .addLine(" * If the value to be returned by %s is present,",
                        metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
                .addLine(" * replaces it by applying {@code mapper} to it and using the result.")
                .addLine(" *")
                .addLine(" * <p>If the result is null, clears the value.")
                .addLine(" *")
                .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
                .addLine(" * @throws NullPointerException if {@code mapper} is null")
                .addLine(" */")
                .addLine("public %s %s(%s mapper) {",
                        metadata.getBuilder(),
                        mapper(property),
                        optional.unaryType);
        optional.applyMapper(code, metadata, property);
        code.addLine("}");
    }

    private void addClear(SourceBuilder code, Metadata metadata) {
        code.addLine("")
                .addLine("/**")
                .addLine(" * Sets the value to be returned by %s",
                        metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
                .addLine(" * to {@link %1$s#%2$s() Optional.%2$s()}.", optional.cls, optional.empty)
                .addLine(" *")
                .addLine(" * @return this {@code %s} object", metadata.getBuilder().getSimpleName())
                .addLine(" */")
                .addLine("public %s %s() {", metadata.getBuilder(), clearMethod(property))
                .addLine("  %s = %s.%s();", property.getField(), elementType, optional.empty)
                .addLine("  return (%s) this;", metadata.getBuilder())
                .addLine("}");
    }

    private void addGetter(SourceBuilder code, Metadata metadata) {
        code.addLine("")
                .addLine("/**")
                .addLine(" * Returns the value that will be returned by %s.",
                        metadata.getType().javadocNoArgMethodLink(property.getGetterName()))
                .addLine(" */")
                .addLine("public %s %s() {", property.getType(), getter(property));
        code.add("  return %s.", optional.cls);
        code.add("%s(%s);\n", optional.ofNullable, property.getField())
                .addLine("}");
    }

    @Override
    public void addFinalFieldAssignment(SourceBuilder code, Excerpt finalField, String builder) {
        code.addLine("%s = %s;", finalField, property.getField().on(builder));
    }

    @Override
    public void addMergeFromValue(Block code, String value) {
        String propertyValue = value + "." + property.getGetterName() + "()";
        optional.invokeIfPresent(code, propertyValue, setter(property));
    }

    @Override
    public void addMergeFromBuilder(Block code, String builder) {
        String propertyValue = builder + "." + getter(property) + "()";
        optional.invokeIfPresent(code, propertyValue, setter(property));
    }

    @Override
    public void addSetBuilderFromPartial(Block code, String builder) {
        code.addLine("%s.%s(%s);", builder, nullableSetter(property), property.getField());
    }

    @Override
    public void addReadValueFragment(SourceBuilder code, Excerpt finalField) {
        code.add("%s", finalField);
    }

    @Override
    public void addSetFromResult(SourceBuilder code, Excerpt builder, Excerpt variable) {
        code.addLine("%s.%s(%s);", builder, setter(property), variable);
    }

    @Override
    public void addClearField(Block code) {
        Optional<Excerpt> defaults = Declarations.freshBuilder(code, metadata);
        if (defaults.isPresent()) {
            code.addLine("%s = %s;", property.getField(), property.getField().on(defaults.get()));
        } else {
            code.addLine("%s = null;", property.getField());
        }
    }
}
