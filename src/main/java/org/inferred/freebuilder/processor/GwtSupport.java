package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;
import static org.inferred.freebuilder.processor.util.ModelUtils.findProperty;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Function;
import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.Visibility;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.TypeMirrorExcerpt;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;

class GwtSupport {

  private static final QualifiedName CUSTOM_FIELD_SERIALIZER =
      QualifiedName.of("com.google.gwt.user.client.rpc", "CustomFieldSerializer");
  private static final QualifiedName SERIALIZATION_EXCEPTION =
      QualifiedName.of("com.google.gwt.user.client.rpc", "SerializationException");
  private static final QualifiedName SERIALIZATION_STREAM_READER =
      QualifiedName.of("com.google.gwt.user.client.rpc", "SerializationStreamReader");
  private static final QualifiedName SERIALIZATION_STREAM_WRITER =
      QualifiedName.of("com.google.gwt.user.client.rpc", "SerializationStreamWriter");

  public static Metadata.Builder gwtMetadata(TypeElement type, Metadata metadata) {
    Metadata.Builder extraMetadata = new Metadata.Builder();
    Optional<AnnotationMirror> annotation = findAnnotationMirror(type, GwtCompatible.class);
    if (annotation.isPresent()) {
      extraMetadata.addGeneratedBuilderAnnotations(Excerpts.add("@%s%n", GwtCompatible.class));
      Optional<AnnotationValue> serializable = findProperty(annotation.get(), "serializable");
      if (serializable.isPresent() && serializable.get().getValue().equals(Boolean.TRUE)) {
        // Due to a bug in GWT's handling of nested types, we have to declare Value as package
        // scoped so Value_CustomFieldSerializer can access it.
        extraMetadata.setValueTypeVisibility(Visibility.PACKAGE);
        extraMetadata.addValueTypeAnnotations(Excerpts.add(
            "@%s(serializable = true)%n", GwtCompatible.class));
        extraMetadata.addNestedClasses(new CustomValueSerializer());
        extraMetadata.addNestedClasses(new GwtWhitelist());
        QualifiedName builderName = metadata.getGeneratedBuilder().getQualifiedName();
        extraMetadata.addVisibleNestedTypes(
            builderName.nestedType("Value_CustomFieldSerializer"),
            builderName.nestedType("GwtWhitelist"));
      }
    }
    return extraMetadata;
  }

  private static final class CustomValueSerializer implements Function<Metadata, Excerpt> {
    @Override
    public Excerpt apply(final Metadata metadata) {
      return new CustomValueSerializerExcerpt(metadata);
    }
  }

  private static final class CustomValueSerializerExcerpt extends Excerpt {
    private final Metadata metadata;

    private CustomValueSerializerExcerpt(Metadata metadata) {
      this.metadata = metadata;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("@%s", GwtCompatible.class);
      if (metadata.getType().isParameterized()) {
        code.addLine("@%s(\"unchecked\")", SuppressWarnings.class);
      }
      code.addLine("public static class Value_CustomFieldSerializer")
          .addLine("    extends %s<%s> {", CUSTOM_FIELD_SERIALIZER, metadata.getValueType())
          .addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public void deserializeInstance(%s reader, %s instance) { }",
              SERIALIZATION_STREAM_READER, metadata.getValueType())
          .addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public boolean hasCustomInstantiateInstance() {")
          .addLine("    return true;")
          .addLine("  }");
      addInstantiateInstance(code);
      addSerializeInstance(code);
      code.addLine("")
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

    private void addInstantiateInstance(SourceBuilder code) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s instantiateInstance(%s reader)",
              metadata.getValueType(), SERIALIZATION_STREAM_READER)
          .addLine("      throws %s {", SERIALIZATION_EXCEPTION);
      Block body = Block.methodBody(code, "reader");
      Excerpt builder = body.declare(
          metadata.getBuilder(), "builder", Excerpts.add("new %s()", metadata.getBuilder()));
      for (Property property : metadata.getProperties()) {
        TypeMirrorExcerpt propertyType = new TypeMirrorExcerpt(property.getType());
        if (property.getType().getKind().isPrimitive()) {
          Excerpt value = body.declare(
              propertyType,
              property.getName(),
              Excerpts.add("reader.read%s()", withInitialCapital(property.getType())));
          property.getCodeGenerator()
              .addSetFromResult(body, builder, value);
        } else if (String.class.getName().equals(property.getType().toString())) {
          Excerpt value = body.declare(
              propertyType,
              property.getName(),
              Excerpts.add("reader.readString()"));
          property.getCodeGenerator()
              .addSetFromResult(body, builder, value);
        } else {
          body.addLine("    try {");
          Block tryBlock = body.innerBlock();
          Excerpt typeAndPreamble;
          if (!property.isFullyCheckedCast()) {
            typeAndPreamble = Excerpts.add("@SuppressWarnings(\"unchecked\") %s", propertyType);
          } else {
            typeAndPreamble = propertyType;
          }
          Excerpt value = tryBlock.declare(
              typeAndPreamble,
              property.getName(),
              Excerpts.add("(%s) reader.readObject()", propertyType));
          property.getCodeGenerator()
              .addSetFromResult(tryBlock, builder, value);
          body.add(tryBlock)
              .addLine("    } catch (%s e) {", ClassCastException.class)
              .addLine("      throw new %s(", SERIALIZATION_EXCEPTION)
              .addLine("          \"Wrong type for property '%s'\", e);", property.getName())
              .addLine("    }");
        }
      }
      body.addLine("    return (%s) %s.build();", metadata.getValueType(), builder);
      code.add(body)
          .addLine("  }");
    }

    private void addSerializeInstance(SourceBuilder code) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public void serializeInstance(%s writer, %s instance)",
              SERIALIZATION_STREAM_WRITER, metadata.getValueType())
          .addLine("      throws %s {", SERIALIZATION_EXCEPTION);
      for (Property property : metadata.getProperties()) {
        if (property.getType().getKind().isPrimitive()) {
          code.add("    writer.write%s(", withInitialCapital(property.getType()));
        } else if (String.class.getName().equals(property.getType().toString())) {
          code.add("    writer.writeString(");
        } else {
          code.add("    writer.writeObject(");
        }
        property.getCodeGenerator().addReadValueFragment(code, property.getField().on("instance"));
        code.add(");\n");
      }
      code.addLine("  }");
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("metadata", metadata);
    }
  }

  private static final class GwtWhitelist implements Function<Metadata, Excerpt> {
    @Override
    public Excerpt apply(final Metadata metadata) {
      return new GwtWhitelistExcerpt(metadata);
    }
  }

  private static final class GwtWhitelistExcerpt extends Excerpt {
    private final Metadata metadata;

    private GwtWhitelistExcerpt(Metadata metadata) {
      this.metadata = metadata;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("/** This class exists solely to ensure GWT whitelists all required types. */")
          .addLine("@%s(serializable = true)", GwtCompatible.class)
          .addLine("static final class GwtWhitelist%s %s %s {",
              metadata.getType().declarationParameters(),
              metadata.isInterfaceType() ? "implements " : "extends ",
              metadata.getType())
          .addLine("");
      for (Property property : metadata.getProperties()) {
        code.addLine("  %s %s;", property.getType(), property.getField());
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

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("metadata", metadata);
    }
  }

  private static String withInitialCapital(Object obj) {
    String s = obj.toString();
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}
