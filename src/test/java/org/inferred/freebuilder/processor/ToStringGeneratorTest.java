package org.inferred.freebuilder.processor;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.inferred.freebuilder.processor.property.Property;
import org.inferred.freebuilder.processor.property.PropertyCodeGenerator;
import org.inferred.freebuilder.processor.property.PropertyCodeGenerator.Initially;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.internal.stubbing.defaultanswers.ReturnsSmartNulls;

import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class ToStringGeneratorTest {

  @Test
  public void noProperties() {
    ToStringBuilder builder = builderFor("Person");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{}\";\n"
        + "}\n");
  }

  @Test
  public void defaultProperty() {
    ToStringBuilder builder = builderFor("Person").withDefault("name");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{name=\" + name + \"}\";\n"
        + "}\n");
  }

  @Test
  public void requiredProperty() {
    ToStringBuilder builder = builderFor("Person").withRequired("name");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withOptional("name");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withDefault("name").withDefault("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
  }

  @Test
  public void twoRequired() {
    ToStringBuilder builder = builderFor("Person").withRequired("name").withRequired("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withOptional("name").withOptional("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
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
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withDefault("name").withRequired("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withRequired("name").withDefault("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withDefault("name").withOptional("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{name=\").append(name);\n"
        + "  if (age != null) {\n"
        + "    result.append(\", age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withOptional("name").withDefault("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  return result.append(\"age=\").append(age).append(\"}\").toString();\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withRequired("name").withOptional("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{name=\").append(name);\n"
        + "  if (age != null) {\n"
        + "    result.append(\", age=\").append(age);\n"
        + "  }\n"
        + "  return result.append(\"}\").toString();\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person").withOptional("name").withRequired("age");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  StringBuilder result = new StringBuilder(\"Person{\");\n"
        + "  if (name != null) {\n"
        + "    result.append(\"name=\").append(name).append(\", \");\n"
        + "  }\n"
        + "  return result.append(\"age=\").append(age).append(\"}\").toString();\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person")
        .withDefault("name")
        .withDefault("age")
        .withDefault("shoeSize");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \", shoeSize=\" + shoeSize"
            + " + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"partial Person{name=\" + name + \", age=\" + age + \", shoeSize=\" + shoeSize"
            + " + \"}\";\n"
        + "}\n");
  }

  @Test
  public void threeRequired() {
    ToStringBuilder builder = builderFor("Person")
        .withRequired("name")
        .withRequired("age")
        .withRequired("shoeSize");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \", shoeSize=\" + shoeSize"
            + " + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person")
        .withOptional("name")
        .withOptional("age")
        .withOptional("shoeSize");

    assertThat(builder.valueToString()).isEqualTo("\n"
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
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person")
        .withRequired("name")
        .withDefault("age")
        .withRequired("shoeSize");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person")
        .withDefault("name")
        .withDefault("age")
        .withRequired("shoeSize");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person")
        .withDefault("name")
        .withRequired("age")
        .withDefault("shoeSize");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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
    ToStringBuilder builder = builderFor("Person")
        .withRequired("name")
        .withDefault("age")
        .withDefault("shoeSize");

    assertThat(builder.valueToString()).isEqualTo("\n"
        + "@Override\n"
        + "public String toString() {\n"
        + "  return \"Person{name=\" + name + \", age=\" + age + \","
            + " shoeSize=\" + shoeSize + \"}\";\n"
        + "}\n");
    assertThat(builder.partialToString()).isEqualTo("\n"
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

  private static ToStringBuilder builderFor(String typename) {
    return new ToStringBuilder(typename);
  }

  private static class ToStringBuilder {

    private final Datatype datatype;
    private final Map<Property, PropertyCodeGenerator> generatorsByProperty = new LinkedHashMap<>();

    ToStringBuilder(String typename) {
      datatype = new Datatype.Builder()
          .setType(QualifiedName.of("com.example", typename).withParameters())
          .setPropertyEnum(
              QualifiedName.of("com.example", typename + "_Builder", "Property").withParameters())
          .buildPartial();
    }

    ToStringBuilder withRequired(String name) {
      return with(Initially.REQUIRED, name);
    }

    ToStringBuilder withOptional(String name) {
      return with(Initially.OPTIONAL, name);
    }

    ToStringBuilder withDefault(String name) {
      return with(Initially.HAS_DEFAULT, name);
    }

    String valueToString() {
      SourceBuilder code = SourceBuilder.forTesting();
      ToStringGenerator.addToString(code, datatype, generatorsByProperty, false);
      return code.toString();
    }

    String partialToString() {
      SourceBuilder code = SourceBuilder.forTesting();
      ToStringGenerator.addToString(code, datatype, generatorsByProperty, true);
      return code.toString();
    }

    private ToStringBuilder with(PropertyCodeGenerator.Initially initially, String name) {
      Property property = new Property.Builder()
          .setName(name)
          .setAllCapsName(name.replaceAll("([A-Z])", "_$1").toUpperCase())
          .buildPartial();
      PropertyCodeGenerator generator = mock(PropertyCodeGenerator.class, new ReturnsSmartNulls());
      when(generator.initialState()).thenReturn(initially);
      if (generator.initialState() == Initially.OPTIONAL) {
        doAnswer(invocation -> {
          SourceBuilder code = invocation.getArgumentAt(0, SourceBuilder.class);
          code.add("%s != null", name);
          return null;
        }).when(generator).addToStringCondition(any());
      } else {
        doThrow(IllegalStateException.class).when(generator).addToStringCondition(any());
      }
      doAnswer(invocation -> {
        SourceBuilder code = invocation.getArgumentAt(0, SourceBuilder.class);
        code.add(name);
        return null;
      }).when(generator).addToStringValue(any());
      generatorsByProperty.put(property, generator);
      return this;
    }
  }
}
