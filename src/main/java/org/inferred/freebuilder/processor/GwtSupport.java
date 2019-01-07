package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;
import static org.inferred.freebuilder.processor.util.ModelUtils.findProperty;

import com.google.common.annotations.GwtCompatible;

import org.inferred.freebuilder.processor.Datatype.Visibility;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.TypeMirrorExcerpt;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

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

  public static Datatype.Builder gwtMetadata(
      TypeElement type,
      Datatype datatype,
      Map<Property, PropertyCodeGenerator> generatorsByProperty) {
    Datatype.Builder extraMetadata = new Datatype.Builder();
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
        extraMetadata.addNestedClasses(new CustomValueSerializer(datatype, generatorsByProperty));
        extraMetadata.addNestedClasses(new GwtWhitelist(datatype, generatorsByProperty.keySet()));
        QualifiedName builderName = datatype.getGeneratedBuilder().getQualifiedName();
        extraMetadata.addVisibleNestedTypes(
            builderName.nestedType("Value_CustomFieldSerializer"),
            builderName.nestedType("GwtWhitelist"));
      }
    }
    return extraMetadata;
  }

  private static final class CustomValueSerializer extends Excerpt {

    private final Datatype datatype;
    private final Map<Property, PropertyCodeGenerator> generatorsByProperty;

    private CustomValueSerializer(
        Datatype datatype,
        Map<Property, PropertyCodeGenerator> generatorsByProperty) {
      this.datatype = datatype;
      this.generatorsByProperty = generatorsByProperty;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("@%s", GwtCompatible.class);
      if (datatype.getType().isParameterized()) {
        code.addLine("@%s(\"unchecked\")", SuppressWarnings.class);
      }
      code.addLine("public static class Value_CustomFieldSerializer")
          .addLine("    extends %s<%s> {", CUSTOM_FIELD_SERIALIZER, datatype.getValueType())
          .addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public void deserializeInstance(%s reader, %s instance) { }",
              SERIALIZATION_STREAM_READER, datatype.getValueType())
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
              SERIALIZATION_STREAM_READER, datatype.getValueType())
          .addLine("    INSTANCE.deserializeInstance(reader, instance);")
          .addLine("  }")
          .addLine("")
          .addLine("  public static %s instantiate(%s reader)",
              datatype.getValueType(), SERIALIZATION_STREAM_READER)
          .addLine("      throws %s {", SERIALIZATION_EXCEPTION)
          .addLine("    return INSTANCE.instantiateInstance(reader);")
          .addLine("  }")
          .addLine("")
          .addLine("  public static void serialize(%s writer, %s instance)",
              SERIALIZATION_STREAM_WRITER, datatype.getValueType())
          .addLine("      throws %s {", SERIALIZATION_EXCEPTION)
          .addLine("    INSTANCE.serializeInstance(writer, instance);")
          .addLine("  }")
          .addLine("}");
    }

    private void addInstantiateInstance(SourceBuilder code) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public %s instantiateInstance(%s reader)",
              datatype.getValueType(), SERIALIZATION_STREAM_READER)
          .addLine("      throws %s {", SERIALIZATION_EXCEPTION);
      Block body = Block.methodBody(code, "reader");
      Excerpt builder = body.declare(
          datatype.getBuilder(), "builder", Excerpts.add("new %s()", datatype.getBuilder()));
      for (Property property : generatorsByProperty.keySet()) {
        TypeMirrorExcerpt propertyType = new TypeMirrorExcerpt(property.getType());
        if (property.getType().getKind().isPrimitive()) {
          Excerpt value = body.declare(
              propertyType,
              property.getName(),
              Excerpts.add("reader.read%s()", withInitialCapital(property.getType())));
          generatorsByProperty.get(property).addSetFromResult(body, builder, value);
        } else if (String.class.getName().equals(property.getType().toString())) {
          Excerpt value = body.declare(
              propertyType,
              property.getName(),
              Excerpts.add("reader.readString()"));
          generatorsByProperty.get(property).addSetFromResult(body, builder, value);
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
          generatorsByProperty.get(property).addSetFromResult(tryBlock, builder, value);
          body.add(tryBlock)
              .addLine("    } catch (%s e) {", ClassCastException.class)
              .addLine("      throw new %s(", SERIALIZATION_EXCEPTION)
              .addLine("          \"Wrong type for property '%s'\", e);", property.getName())
              .addLine("    }");
        }
      }
      body.addLine("    return (%s) %s.build();", datatype.getValueType(), builder);
      code.add(body)
          .addLine("  }");
    }

    private void addSerializeInstance(SourceBuilder code) {
      code.addLine("")
          .addLine("  @%s", Override.class)
          .addLine("  public void serializeInstance(%s writer, %s instance)",
              SERIALIZATION_STREAM_WRITER, datatype.getValueType())
          .addLine("      throws %s {", SERIALIZATION_EXCEPTION);
      for (Property property : generatorsByProperty.keySet()) {
        if (property.getType().getKind().isPrimitive()) {
          code.add("    writer.write%s(", withInitialCapital(property.getType()));
        } else if (String.class.getName().equals(property.getType().toString())) {
          code.add("    writer.writeString(");
        } else {
          code.add("    writer.writeObject(");
        }
        generatorsByProperty.get(property)
            .addReadValueFragment(code, property.getField().on("instance"));
        code.add(");\n");
      }
      code.addLine("  }");
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("datatype", datatype);
      fields.add("generatorsByProperty", generatorsByProperty);
    }
  }

  private static final class GwtWhitelist extends Excerpt {

    private final Datatype datatype;
    private final Collection<Property> properties;

    private GwtWhitelist(Datatype datatype, Collection<Property> properties) {
      this.datatype = datatype;
      this.properties = properties;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("/** This class exists solely to ensure GWT whitelists all required types. */")
          .addLine("@%s(serializable = true)", GwtCompatible.class)
          .addLine("static final class GwtWhitelist%s %s %s {",
              datatype.getType().declarationParameters(),
              datatype.isInterfaceType() ? "implements " : "extends ",
              datatype.getType())
          .addLine("");
      for (Property property : properties) {
        code.addLine("  %s %s;", property.getType(), property.getField());
      }
      code.addLine("")
          .addLine("  private GwtWhitelist() {")
          .addLine("    throw new %s();", UnsupportedOperationException.class)
          .addLine("   }");
      for (Property property : properties) {
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
      fields.add("datatype", datatype);
      fields.add("generatorsByProperty", properties);
    }
  }

  private static String withInitialCapital(Object obj) {
    String s = obj.toString();
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}
