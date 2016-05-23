package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;
import static org.inferred.freebuilder.processor.util.ModelUtils.findProperty;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Function;
import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.Visibility;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

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

  public static void addGwtMetadata(TypeElement type, Metadata.Builder metadata) {
    Optional<AnnotationMirror> annotation = findAnnotationMirror(type, GwtCompatible.class);
    if (annotation.isPresent()) {
      metadata.addGeneratedBuilderAnnotations(new GwtCompatibleExcerpt());
      Optional<AnnotationValue> serializable = findProperty(annotation.get(), "serializable");
      if (serializable.isPresent() && serializable.get().getValue().equals(Boolean.TRUE)) {
        // Due to a bug in GWT's handling of nested types, we have to declare Value as package
        // scoped so Value_CustomFieldSerializer can access it.
        metadata.setValueTypeVisibility(Visibility.PACKAGE);
        metadata.addValueTypeAnnotations(new GwtCompatibleAndSerializableExcerpt());
        metadata.addNestedClasses(new CustomValueSerializer());
        metadata.addNestedClasses(new GwtWhitelist());
        QualifiedName builderName = metadata.getGeneratedBuilder().getQualifiedName();
        metadata.addVisibleNestedTypes(
            builderName.nestedType("Value_CustomFieldSerializer"),
            builderName.nestedType("GwtWhitelist"));
      }
    }
  }

  private static class GwtCompatibleExcerpt implements Excerpt {
    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("@%s", GwtCompatible.class);
    }
  }

  private static class GwtCompatibleAndSerializableExcerpt implements Excerpt {
    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("@%s(serializable = true)", GwtCompatible.class);
    }
  }

  private static final class CustomValueSerializer implements Function<Metadata, Excerpt> {
    @Override
    public Excerpt apply(final Metadata metadata) {
      return new Excerpt() {
        @Override
        public void addTo(SourceBuilder code) {
          addCustomValueSerializer(code, metadata);
        }
      };
    }
  }

  private static final class GwtWhitelist implements Function<Metadata, Excerpt> {
    @Override
    public Excerpt apply(final Metadata metadata) {
      return new Excerpt() {
        @Override
        public void addTo(SourceBuilder code) {
          addGwtWhitelistType(code, metadata);
        }
      };
    }
  }

  private static void addCustomValueSerializer(SourceBuilder code, Metadata metadata) {
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
            .addSetFromResult(code, "builder", property.getName());
      } else if (String.class.getName().equals(property.getType().toString())) {
        code.addLine("      %s %s = reader.readString();",
            property.getType(), property.getName());
        property.getCodeGenerator()
            .addSetFromResult(code, "builder", property.getName());
      } else {
        code.addLine("    try {");
        if (!property.isFullyCheckedCast()) {
          code.addLine("      @SuppressWarnings(\"unchecked\")");
        }
        code.addLine("      %1$s %2$s = (%1$s) reader.readObject();",
                property.getType(), property.getName());
        property.getCodeGenerator()
            .addSetFromResult(code, "builder", property.getName());
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
            metadata.getType().declarationParameters(),
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

  private static String withInitialCapital(Object obj) {
    String s = obj.toString();
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}
