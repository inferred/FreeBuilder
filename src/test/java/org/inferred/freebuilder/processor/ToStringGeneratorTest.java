package org.inferred.freebuilder.processor;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.internal.stubbing.defaultanswers.ReturnsSmartNulls;

@RunWith(JUnit4.class)
public class ToStringGeneratorTest {

  @Test
  public void noProperties() {
    Metadata metadata = datatype("Person").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{}\";\n"
        + "}\n");
  }

  @Test
  public void defaultProperty() {
    Metadata metadata = datatype("Person").withDefault("name").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{name=\" + name + \"}\";\n"
        + "}\n");
  }

  @Test
  public void requiredProperty() {
    Metadata metadata = datatype("Person").withRequired("name").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.NAME)) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void optionalProperty() {
    Metadata metadata = datatype("Person").withOptional("name").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void twoDefaults() {
    Metadata metadata = datatype("Person").withDefault("name").withDefault("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
  }

  @Test
  public void twoRequired() {
    Metadata metadata = datatype("Person").withRequired("name").withRequired("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.NAME)) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.AGE)) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void twoOptional() {
    Metadata metadata = datatype("Person").withOptional("name").withOptional("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (age != null) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (age != null) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void defaultThenRequired() {
    Metadata metadata = datatype("Person").withDefault("name").withRequired("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{name=\").append(name);\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.AGE)) {\n"
        + "    result.append(\", age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void requiredThenDefault() {
    Metadata metadata = datatype("Person").withRequired("name").withDefault("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.NAME)) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  return result.append(\"age=\").append(age).append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void defaultThenOptional() {
    Metadata metadata = datatype("Person").withDefault("name").withOptional("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{name=\").append(name);\n"
        + "  if (age != null) {\n"
        + "    result.append(\", age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{name=\").append(name);\n"
        + "  if (age != null) {\n"
        + "    result.append(\", age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void optionalThenDefault() {
    Metadata metadata = datatype("Person").withOptional("name").withDefault("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  return result.append(\"age=\").append(age).append(\"}\").toString();\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  return result.append(\"age=\").append(age).append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void requiredThenOptional() {
    Metadata metadata = datatype("Person").withRequired("name").withOptional("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{name=\").append(name);\n"
        + "  if (age != null) {\n"
        + "    result.append(\", age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.NAME)) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (age != null) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void optionalThenRequired() {
    Metadata metadata = datatype("Person").withOptional("name").withRequired("age").build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  return result.append(\"age=\").append(age).append(\"}\").toString();\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.AGE)) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void threeDefaults() {
    Metadata metadata = datatype("Person")
        .withDefault("name")
        .withDefault("age")
        .withDefault("shoeSize")
        .build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \", shoeSize=\" + shoeSize"
            + " + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{name=\" + name + \", age=\" + age + \", shoeSize=\" + shoeSize"
            + " + \"}\";\n"
        + "}\n");
  }

  @Test
  public void threeRequired() {
    Metadata metadata = datatype("Person")
        .withRequired("name")
        .withRequired("age")
        .withRequired("shoeSize")
        .build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \", shoeSize=\" + shoeSize"
            + " + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.NAME)) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.AGE)) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.SHOE_SIZE)) {\n"
        + "    result.append(separator).append(\"shoeSize=\").append(shoeSize);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void threeOptional() {
    Metadata metadata = datatype("Person")
        .withOptional("name")
        .withOptional("age")
        .withOptional("shoeSize")
        .build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (age != null) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (shoeSize != null) {\n"
        + "    result.append(separator).append(\"shoeSize=\").append(shoeSize);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  String separator = \"\";\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (age != null) {\n"
        + "    result.append(separator).append(\"age=\").append(age);\n"
        + "    separator = \", \";\n"
        + "  }\n"
        + "  if (shoeSize != null) {\n"
        + "    result.append(separator).append(\"shoeSize=\").append(shoeSize);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void requiredDefaultRequired() {
    Metadata metadata = datatype("Person")
        .withRequired("name")
        .withDefault("age")
        .withRequired("shoeSize")
        .build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.NAME)) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  result.append(\"age=\").append(age);\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.SHOE_SIZE)) {\n"
        + "    result.append(\", shoeSize=\").append(shoeSize);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void defaultDefaultRequired() {
    Metadata metadata = datatype("Person")
        .withDefault("name")
        .withDefault("age")
        .withRequired("shoeSize")
        .build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{name=\").append(name)"
            + ".append(\", age=\").append(age);\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.SHOE_SIZE)) {\n"
        + "    result.append(\", shoeSize=\").append(shoeSize);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void defaultRequiredDefault() {
    Metadata metadata = datatype("Person")
        .withDefault("name")
        .withRequired("age")
        .withDefault("shoeSize")
        .build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{name=\").append(name);\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.AGE)) {\n"
        + "    result.append(\", age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\", shoeSize=\").append(shoeSize).append(\"}\").toString();\n"
        + "}\n");
  }

  @Test
  public void requiredDefaultDefault() {
    Metadata metadata = datatype("Person")
        .withRequired("name")
        .withDefault("age")
        .withDefault("shoeSize")
        .build();

    assertThat(valueToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(partialToString(metadata)).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"partial Person{\");\n"
        + "  if (!_unsetProperties.contains(Person_Builder.Property.NAME)) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  return result.append(\"age=\").append(age).append(\", shoeSize=\").append(shoeSize)"
            + ".append(\"}\").toString();\n"
        + "}\n");
  }

  private static String valueToString(Metadata metadata) {
    SourceBuilder sourceBuilder = SourceStringBuilder.simple();
    ToStringGenerator.addToString(sourceBuilder, metadata, false);
    return sourceBuilder.toString();
  }

  private static String partialToString(Metadata metadata) {
    SourceBuilder sourceBuilder = SourceStringBuilder.simple();
    ToStringGenerator.addToString(sourceBuilder, metadata, true);
    return sourceBuilder.toString();
  }

  private static PartialMetadataBuilder datatype(String typename) {
    return new PartialMetadataBuilder(typename);
  }

  private static class PartialMetadataBuilder {
    private final Metadata.Builder builder;

    PartialMetadataBuilder(String typename) {
      builder = new Metadata.Builder()
          .setType(QualifiedName.of("com.example", typename).withParameters())
          .setPropertyEnum(
              QualifiedName.of("com.example", typename + "_Builder", "Property").withParameters());
    }

    PartialMetadataBuilder withRequired(String name) {
      return with(Type.REQUIRED, name);
    }

    PartialMetadataBuilder withOptional(String name) {
      return with(Type.OPTIONAL, name);
    }

    PartialMetadataBuilder withDefault(String name) {
      return with(Type.HAS_DEFAULT, name);
    }

    private PartialMetadataBuilder with(PropertyCodeGenerator.Type type, String name) {
      PropertyCodeGenerator mock = mock(PropertyCodeGenerator.class, new ReturnsSmartNulls());
      when(mock.getType()).thenReturn(type);
      builder.addProperties(new Property.Builder()
          .setName(name)
          .setAllCapsName(name.replaceAll("([A-Z])", "_$1").toUpperCase())
          .setCodeGenerator(mock)
          .buildPartial());
      return this;
    }

    Metadata build() {
      return builder.buildPartial();
    }
  }
}
