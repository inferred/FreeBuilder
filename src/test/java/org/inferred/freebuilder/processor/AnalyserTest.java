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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import static org.inferred.freebuilder.processor.BuilderFactory.BUILDER_METHOD;
import static org.inferred.freebuilder.processor.BuilderFactory.NEW_BUILDER_METHOD;
import static org.inferred.freebuilder.processor.BuilderFactory.NO_ARGS_CONSTRUCTOR;
import static org.inferred.freebuilder.processor.Datatype.Visibility.PACKAGE;
import static org.inferred.freebuilder.processor.Datatype.Visibility.PRIVATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static java.util.stream.Collectors.toMap;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableMap;

import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;
import org.inferred.freebuilder.processor.Datatype.StandardMethod;
import org.inferred.freebuilder.processor.Datatype.UnderrideLevel;
import org.inferred.freebuilder.processor.property.DefaultProperty;
import org.inferred.freebuilder.processor.property.Property;
import org.inferred.freebuilder.processor.property.PropertyCodeGenerator;
import org.inferred.freebuilder.processor.property.PropertyCodeGenerator.Initially;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.QualifiedName;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.testing.MessagerRule;
import org.inferred.freebuilder.processor.source.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Optional;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;

/** Unit tests for {@link Analyser}. */
@RunWith(JUnit4.class)
public class AnalyserTest {

  @Rule public final ModelRule model = new ModelRule();
  @Rule public final MessagerRule messager = new MessagerRule();

  private Analyser analyser;

  @Before
  public void setup() {
    analyser = new Analyser(model.environment(), messager);
  }

  @Test
  public void emptyDataType() throws CannotGenerateCodeException {
    GeneratedType builder = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "}"));

    assertThat(builder).isEqualTo(new GeneratedStub(
        QualifiedName.of("com.example", "DataType"),
        QualifiedName.of("com.example", "DataType_Builder").withParameters()));
    messager.verifyNote("DataType", addBuilderToClassMessage("DataType_Builder"));
  }

  @Test
  public void emptyInterface() throws CannotGenerateCodeException {
    GeneratedType builder = analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "}"));

    assertThat(builder).isEqualTo(new GeneratedStub(
        QualifiedName.of("com.example", "DataType"),
        QualifiedName.of("com.example", "DataType_Builder").withParameters()));
    messager.verifyNote("DataType", addBuilderToInterfaceMessage("DataType_Builder"));
  }

  @Test
  public void nestedDataType() throws CannotGenerateCodeException {
    GeneratedType builder = analyser.analyse((TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class OuterClass {",
        "  ---> public static class DataType {",
        "  }",
        "}"));

    assertThat(builder).isEqualTo(new GeneratedStub(
        QualifiedName.of("com.example", "OuterClass", "DataType"),
        QualifiedName.of("com.example", "OuterClass_DataType_Builder").withParameters()));
    messager.verifyNote("DataType", addBuilderToClassMessage("OuterClass_DataType_Builder"));
  }

  @Test
  public void twiceNestedDataType() throws CannotGenerateCodeException {
    GeneratedType builder = analyser.analyse((TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class OuterClass {",
        "  public static class InnerClass {",
        "    ---> public static class DataType {",
        "    }",
        "  }",
        "}"));

    assertThat(builder).isEqualTo(new GeneratedStub(
        QualifiedName.of("com.example", "OuterClass", "InnerClass", "DataType"),
        QualifiedName.of("com.example", "OuterClass_InnerClass_DataType_Builder")
            .withParameters()));
    messager.verifyNote("DataType",
        addBuilderToClassMessage("OuterClass_InnerClass_DataType_Builder"));
  }

  @Test
  public void classWithNoProperties() throws CannotGenerateCodeException {
    GeneratedType builder = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "}"));

    QualifiedName dataType = QualifiedName.of("com.example", "DataType");
    QualifiedName generatedType = QualifiedName.of("com.example", "DataType_Builder");
    assertThat(builder).isEqualTo(new GeneratedBuilder(
        new Datatype.Builder()
            .setBuilder(dataType.nestedType("Builder").withParameters())
            .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
            .setBuilderSerializable(false)
            .setExtensible(true)
            .setGeneratedBuilder(generatedType.withParameters())
            .setHasToBuilderMethod(false)
            .setInterfaceType(false)
            .setPartialType(generatedType.nestedType("Partial").withParameters())
            .setPropertyEnum(generatedType.nestedType("Property").withParameters())
            .setRebuildableType(generatedType.nestedType("Rebuildable").withParameters())
            .setType(dataType.withParameters())
            .setValueType(generatedType.nestedType("Value").withParameters())
            .build(),
        ImmutableMap.of()));
  }

  @Test
  public void genericInterfaceWithNoProperties() throws CannotGenerateCodeException {
    TypeElement typeElement = model.newType(
        "package com.example;",
        "public interface DataType<K, V> {",
        "  class Builder<K, V> extends DataType_Builder<K, V> { }",
        "}");
    TypeParameterElement k = typeElement.getTypeParameters().get(0);
    TypeParameterElement v = typeElement.getTypeParameters().get(1);
    DeclaredType mirror = (DeclaredType) typeElement.asType();
    TypeVariable kVar = (TypeVariable) mirror.getTypeArguments().get(0);
    TypeVariable vVar = (TypeVariable) mirror.getTypeArguments().get(1);

    GeneratedType builder = analyser.analyse(typeElement);

    QualifiedName dataType = QualifiedName.of("com.example", "DataType");
    QualifiedName generatedType = QualifiedName.of("com.example", "DataType_Builder");
    assertThat(builder).isEqualTo(new GeneratedBuilder(
        new Datatype.Builder()
            .setBuilder(dataType.nestedType("Builder").withParameters(kVar, vVar))
            .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
            .setBuilderSerializable(false)
            .setExtensible(true)
            .setGeneratedBuilder(generatedType.withParameters(k, v))
            .setHasToBuilderMethod(false)
            .setInterfaceType(true)
            .setPartialType(generatedType.nestedType("Partial").withParameters(k, v))
            .setPropertyEnum(generatedType.nestedType("Property").withParameters())
            .setRebuildableType(generatedType.nestedType("Rebuildable").withParameters(k, v))
            .setType(dataType.withParameters(k, v))
            .setValueType(generatedType.nestedType("Value").withParameters(k, v))
            .build(),
        ImmutableMap.of()));
  }

  @Test
  public void serializableBuilderSubclass() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder ",
        "      extends DataType_Builder implements java.io.Serializable { }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getDatatype().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getDatatype().getBuilder().getQualifiedName().toString());
    assertTrue(builder.getDatatype().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicBuilderMethod() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getDatatype().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getDatatype().getBuilder().getQualifiedName().toString());
    assertThat(builder.getDatatype().isExtensible()).isTrue();
    assertThat(builder.getDatatype().getBuilderFactory().get()).isEqualTo(BUILDER_METHOD);
    assertFalse(builder.getDatatype().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicBuilderMethod_protectedConstructor()
      throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder {",
        "    protected Builder() { }",
        "  }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getDatatype().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getDatatype().getBuilder().getQualifiedName().toString());
    assertThat(builder.getDatatype().isExtensible()).isTrue();
    assertThat(builder.getDatatype().getBuilderFactory().get()).isEqualTo(BUILDER_METHOD);
    assertFalse(builder.getDatatype().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicBuilderMethod_privateConstructor()
      throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder {",
        "    private Builder() { }",
        "  }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getDatatype().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getDatatype().getBuilder().getQualifiedName().toString());
    assertThat(builder.getDatatype().isExtensible()).isFalse();
    assertThat(builder.getDatatype().getBuilderFactory().get()).isEqualTo(BUILDER_METHOD);
    assertFalse(builder.getDatatype().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicNewBuilderMethod() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder newBuilder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getDatatype().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getDatatype().getBuilder().getQualifiedName().toString());
    assertThat(builder.getDatatype().isExtensible()).isTrue();
    assertThat(builder.getDatatype().getBuilderFactory().get()).isEqualTo(NEW_BUILDER_METHOD);
    assertFalse(builder.getDatatype().isBuilderSerializable());
  }

  @Test
  public void toBuilderMethod() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "  Builder toBuilder();",
        "  class Builder extends DataType_Builder { }",
        "}"));

    assertTrue(builder.getDatatype().getHasToBuilderMethod());
    assertThat(builder.getGeneratorsByProperty()).isEmpty();
  }

  @Test
  public void toBuilderMethod_genericInterface() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType<K, V> {",
        "  Builder<K, V> toBuilder();",
        "  class Builder<K, V> extends DataType_Builder<K, V> { }",
        "}"));

    assertTrue(builder.getDatatype().getHasToBuilderMethod());
    assertThat(builder.getGeneratorsByProperty()).isEmpty();
  }

  @Test
  public void toBuilderMethod_noBuilderFactoryMethod() throws CannotGenerateCodeException {
    analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "  Builder toBuilder();",
        "  class Builder extends DataType_Builder {",
        "    public Builder(String unused) {}",
        "  }",
        "}"));

    messager.verifyError(
        "toBuilder", "No accessible no-args Builder constructor available to implement toBuilder");
  }

  @Test
  public void toBuilderMethod_privateBuilderFactoryMethod() throws CannotGenerateCodeException {
    analyser.analyse(model.newType(
        "package com.example;",
        "public abstract class DataType {",
        "  public static Builder builder() { return new Builder(); }",
        "  public abstract Builder toBuilder();",
        "  public static class Builder extends DataType_Builder {",
        "    private Builder() {}",
        "  }",
        "}"));

    messager.verifyError(
        "toBuilder", "No accessible no-args Builder constructor available to implement toBuilder");
  }

  @Test
  public void twoBeanGetters() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Property name = new Property.Builder()
        .setAllCapsName("NAME")
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(model.typeMirror(String.class))
        .setUsingBeanConvention(true)
        .build();
    Property age = new Property.Builder()
        .setAllCapsName("AGE")
        .setBoxedType(model.typeMirror(Integer.class))
        .setCapitalizedName("Age")
        .setFullyCheckedCast(true)
        .setGetterName("getAge")
        .setName("age")
        .setType(model.typeMirror(int.class))
        .setUsingBeanConvention(true)
        .build();
    assertThat(builder.getGeneratorsByProperty().keySet()).containsExactly(name, age).inOrder();
  }

  @Test
  public void twoPrefixlessGetters() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String name();",
        "  public abstract int age();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Property name = new Property.Builder()
        .setAllCapsName("NAME")
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("name")
        .setName("name")
        .setType(model.typeMirror(String.class))
        .setUsingBeanConvention(false)
        .build();
    Property age = new Property.Builder()
        .setAllCapsName("AGE")
        .setBoxedType(model.typeMirror(Integer.class))
        .setCapitalizedName("Age")
        .setFullyCheckedCast(true)
        .setGetterName("age")
        .setName("age")
        .setType(model.typeMirror(int.class))
        .setUsingBeanConvention(false)
        .build();
    assertThat(builder.getGeneratorsByProperty().keySet()).containsExactly(name, age).inOrder();
  }

  @Test
  public void complexGetterNames() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getCustomURLTemplate();",
        "  public abstract String getTop50Sites();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("customURLTemplate", "top50Sites");
    assertEquals("CUSTOM_URL_TEMPLATE", properties.get("customURLTemplate").getAllCapsName());
    assertEquals("TOP50_SITES", properties.get("top50Sites").getAllCapsName());
  }

  @Test
  public void twoGetters_interface() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  int getAge();",
        "  class Builder extends DataType_Builder {}",
        "}"));

    Property name = new Property.Builder()
        .setAllCapsName("NAME")
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(model.typeMirror(String.class))
        .setUsingBeanConvention(true)
        .build();
    Property age = new Property.Builder()
        .setAllCapsName("AGE")
        .setBoxedType(model.typeMirror(Integer.class))
        .setCapitalizedName("Age")
        .setFullyCheckedCast(true)
        .setGetterName("getAge")
        .setName("age")
        .setType(model.typeMirror(int.class))
        .setUsingBeanConvention(true)
        .build();
    assertThat(builder.getGeneratorsByProperty().keySet()).containsExactly(name, age).inOrder();
  }

  @Test
  public void ignoredEqualsAndHashCode() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @org.inferred.freebuilder.IgnoredByEquals public abstract String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Property available = new Property.Builder()
        .setAllCapsName("NAME")
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(model.typeMirror(String.class))
        .setUsingBeanConvention(true)
        .setInEqualsAndHashCode(false)
        .build();
    assertThat(builder.getGeneratorsByProperty().keySet()).containsExactly(available);
  }

  @Test
  public void ignoredToString() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @org.inferred.freebuilder.NotInToString public abstract String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Property available = new Property.Builder()
        .setAllCapsName("NAME")
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(model.typeMirror(String.class))
        .setUsingBeanConvention(true)
        .setInToString(false)
        .build();
    assertThat(builder.getGeneratorsByProperty().keySet()).containsExactly(available);
  }

  @Test
  public void ignoredEqualsAndHashCodeAndToString() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @org.inferred.freebuilder.IgnoredByEquals",
        "  @org.inferred.freebuilder.NotInToString",
        "  public abstract String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Property available = new Property.Builder()
        .setAllCapsName("NAME")
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(model.typeMirror(String.class))
        .setUsingBeanConvention(true)
        .setInEqualsAndHashCode(false)
        .setInToString(false)
        .build();
    assertThat(builder.getGeneratorsByProperty().keySet()).containsExactly(available);
  }

  @Test
  public void booleanGetter() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean isAvailable();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Property available = new Property.Builder()
        .setAllCapsName("AVAILABLE")
        .setBoxedType(model.typeMirror(Boolean.class))
        .setCapitalizedName("Available")
        .setFullyCheckedCast(true)
        .setGetterName("isAvailable")
        .setName("available")
        .setType(model.typeMirror(boolean.class))
        .setUsingBeanConvention(true)
        .build();
    assertThat(builder.getGeneratorsByProperty().keySet()).containsExactly(available);
  }

  @Test
  public void finalGetter() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public final String getName() {",
        "    return null;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).isEmpty();
  }

  @Test
  public void defaultCodeGenerator() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  class Builder extends DataType_Builder {}",
        "}"));

    PropertyCodeGenerator generator = getOnlyElement(builder.getGeneratorsByProperty().values());
    assertThat(generator).isInstanceOf(DefaultProperty.class);
  }

  @Test
  public void nonAbstractGetter() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public String getName() {",
        "    return null;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).isEmpty();
  }

  @Test
  public void nonAbstractMethodNamedIssue() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public boolean issue() {",
        "    return true;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).isEmpty();
  }

  @Test
  public void voidGetter() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract void getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).isEmpty();
    messager.verifyError("getName", "Getter methods must not be void on FreeBuilder types");
  }

  @Test
  public void nonBooleanIsMethod() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String isName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).isEmpty();
    messager.verifyError(
        "isName", "Getter methods starting with 'is' must return a boolean on FreeBuilder types");
  }

  @Test
  public void getterWithArgument() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName(boolean capitalized);",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).isEmpty();
    messager.verifyError("getName", "Getter methods cannot take parameters on FreeBuilder types");
  }

  @Test
  public void abstractMethodNamedGet() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String get();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("get");
    assertEquals("Get", properties.get("get").getCapitalizedName());
    assertEquals("get", properties.get("get").getGetterName());
    assertEquals("GET", properties.get("get").getAllCapsName());
  }

  @Test
  public void abstractMethodNamedGetter() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getter();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("getter");
    assertEquals("Getter", properties.get("getter").getCapitalizedName());
    assertEquals("getter", properties.get("getter").getGetterName());
    assertEquals("GETTER", properties.get("getter").getAllCapsName());
  }

  @Test
  public void abstractMethodNamedIssue() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String issue();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("issue");
    assertEquals("ISSUE", properties.get("issue").getAllCapsName());
    assertEquals("Issue", properties.get("issue").getCapitalizedName());
    assertEquals("issue", properties.get("issue").getGetterName());
    assertEquals("java.lang.String", properties.get("issue").getType().toString());
  }

  @Test
  public void abstractMethodWithNonAsciiName() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getürkt();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("getürkt");
    assertEquals("GETÜRKT", properties.get("getürkt").getAllCapsName());
    assertEquals("Getürkt", properties.get("getürkt").getCapitalizedName());
    assertEquals("getürkt", properties.get("getürkt").getGetterName());
    assertEquals("java.lang.String", properties.get("getürkt").getType().toString());
  }

  @Test
  public void abstractMethodNamedIs() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean is();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("is");
    assertEquals("IS", properties.get("is").getAllCapsName());
    assertEquals("Is", properties.get("is").getCapitalizedName());
    assertEquals("is", properties.get("is").getGetterName());
    assertEquals("boolean", properties.get("is").getType().toString());
  }

  @Test
  public void mixedValidAndInvalidGetters() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract void getNothing();",
        "  public abstract int getAge();",
        "  public abstract float isDoubleBarrelled();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("name", "age");
    messager.verifyError("getNothing", "Getter methods must not be void on FreeBuilder types");
    messager.verifyError(
        "isDoubleBarrelled",
        "Getter methods starting with 'is' must return a boolean on FreeBuilder types");
  }

  @Test
  public void noDefaults() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "  public static class Builder extends DataType_Builder {",
        "    public Builder() {",
        "    }",
        "  }",
        "}"));

    Map<String, PropertyCodeGenerator> generators = generatorsByName(builder);
    assertEquals(Initially.REQUIRED, generators.get("name").initialState());
    assertEquals(Initially.REQUIRED, generators.get("age").initialState());
  }

  @Test
  public void implementsInterface() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public interface IDataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "}");
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("name", "age");
  }

  @Test
  public void implementsGenericInterface() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public interface IDataType<T> {",
        "  public abstract T getProperty();",
        "}");
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType<String> {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("property");
    assertEquals("java.lang.String", properties.get("property").getType().toString());
  }

  @Test
  public void notGwtSerializable() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = false)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(builder.getDatatype().getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(builder.getDatatype().getValueTypeVisibility()).isEqualTo(PRIVATE);
    assertThat(builder.getDatatype().getValueTypeAnnotations()).isEmpty();
    assertThat(builder.getDatatype().getNestedClasses()).isEmpty();
  }

  @Test
  public void gwtSerializable() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = true)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(builder.getDatatype().getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(builder.getDatatype().getValueTypeVisibility()).isEqualTo(PACKAGE);
    assertThat(builder.getDatatype().getValueTypeAnnotations()).hasSize(1);
    assertThat(asSource(builder.getDatatype().getValueTypeAnnotations().get(0)))
        .isEqualTo("@GwtCompatible(serializable = true)");
    assertThat(builder.getDatatype().getNestedClasses()).hasSize(2);
  }

  @Test
  public void underriddenEquals() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE));
    messager.verifyError(
        "equals", "hashCode and equals must be implemented together on FreeBuilder types");
  }

  @Test
  public void underriddenHashCode() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE));
    messager.verifyError(
        "hashCode", "hashCode and equals must be implemented together on FreeBuilder types");
  }

  @Test
  public void underriddenHashCodeAndEquals() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE));
  }

  @Test
  public void underriddenToString() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.TO_STRING, UnderrideLevel.OVERRIDEABLE));
  }

  @Test
  public void underriddenTriad() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.TO_STRING, UnderrideLevel.OVERRIDEABLE));
  }

  @Test
  public void finalEquals() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL));
    messager.verifyError(
        "equals", "hashCode and equals must be implemented together on FreeBuilder types");
  }

  @Test
  public void finalHashCode() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL));
    messager.verifyError(
        "hashCode", "hashCode and equals must be implemented together on FreeBuilder types");
  }

  @Test
  public void finalHashCodeAndEquals() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL,
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL));
  }

  @Test
  public void finalToString() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.TO_STRING, UnderrideLevel.FINAL));
  }

  @Test
  public void finalTriad() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public final String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL,
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL,
        StandardMethod.TO_STRING, UnderrideLevel.FINAL));
  }

  @Test
  public void abstractEquals() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract boolean equals(Object obj);",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEmpty();
  }

  @Test
  public void abstractHashCode() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract int hashCode();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEmpty();
  }

  @Test
  public void abstractToString() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about how this is a useful toString implementation. */",
        "  @Override public abstract String toString();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getDatatype().getStandardMethodUnderrides()).isEmpty();
  }

  @Test
  public void privateNestedType() {
    try {
      analyser.analyse((TypeElement) model.newElementWithMarker(
          "package com.example;",
          "public class DataType {",
          "  ---> private static class PrivateType {",
          "  }",
          "}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError("PrivateType", "FreeBuilder types cannot be private");
  }

  @Test
  public void indirectlyPrivateNestedType() {
    try {
      analyser.analyse((TypeElement) model.newElementWithMarker(
          "package com.example;",
          "public class DataType {",
          "  private static class PrivateType {",
          "    ---> static class NestedType {",
          "    }",
          "  }",
          "}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError(
        "NestedType",
        "FreeBuilder types cannot be private, but enclosing type PrivateType is inaccessible");
  }

  @Test
  public void innerType() {
    try {
      analyser.analyse((TypeElement) model.newElementWithMarker(
          "package com.example;",
          "public class DataType {",
          "  ---> public class InnerType {",
          "  }",
          "}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError(
        "InnerType",
        "Inner classes cannot be FreeBuilder types (did you forget the static keyword?)");
  }

  @Test
  public void nonStaticBuilder() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = propertiesByName(builder);
    assertThat(properties.keySet()).containsExactly("name");
    assertThat(builder.getDatatype().isExtensible()).isFalse();
    assertThat(builder.getDatatype().getBuilderFactory()).isEqualTo(Optional.empty());
    messager.verifyError("Builder", "Builder must be static on FreeBuilder types");
  }

  @Test
  public void genericClassWithTwoProperties() throws CannotGenerateCodeException {
    TypeElement element = model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}");
    DeclaredType mirror = (DeclaredType) element.asType();
    TypeParameterElement a = element.getTypeParameters().get(0);
    TypeParameterElement b = element.getTypeParameters().get(1);
    TypeVariable aVar = (TypeVariable) mirror.getTypeArguments().get(0);
    TypeVariable bVar = (TypeVariable) mirror.getTypeArguments().get(1);

    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(element);

    QualifiedName dataType = QualifiedName.of("com.example", "DataType");
    QualifiedName generatedType = QualifiedName.of("com.example", "DataType_Builder");
    assertThat(builder.getDatatype()).isEqualTo(new Datatype.Builder()
        .setBuilder(dataType.nestedType("Builder").withParameters(aVar, bVar))
        .setBuilderFactory(BuilderFactory.NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setExtensible(true)
        .setGeneratedBuilder(generatedType.withParameters(a, b))
        .setHasToBuilderMethod(false)
        .setInterfaceType(false)
        .setPartialType(generatedType.nestedType("Partial").withParameters(a, b))
        .setPropertyEnum(generatedType.nestedType("Property").withParameters())
        .setRebuildableType(generatedType.nestedType("Rebuildable").withParameters(a, b))
        .setType(dataType.withParameters(a, b))
        .setValueType(generatedType.nestedType("Value").withParameters(a, b))
        .build());
    assertThat(builder.getGeneratorsByProperty().keySet())
        .containsExactly(
            new Property.Builder()
                .setAllCapsName("NAME")
                .setCapitalizedName("Name")
                .setFullyCheckedCast(true)  // https://github.com/inferred/FreeBuilder/issues/327
                .setGetterName("getName")
                .setName("name")
                .setType(a.asType())
                .setUsingBeanConvention(true)
                .build(),
            new Property.Builder()
                .setAllCapsName("AGE")
                .setCapitalizedName("Age")
                .setFullyCheckedCast(true)  // https://github.com/inferred/FreeBuilder/issues/327
                .setGetterName("getAge")
                .setName("age")
                .setType(b.asType())
                .setUsingBeanConvention(true)
                .build())
        .inOrder();
  }

  @Test
  public void genericTypeWithBounds() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A extends CharSequence, B extends " + Temporal.class.getName()
            + "> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}"));

    assertEquals("DataType.Builder<A, B>",
        builder.getDatatype().getBuilder().toString());
    assertEquals("DataType<A extends CharSequence, B extends Temporal>",
        SourceBuilder.forTesting().add(builder.getDatatype().getType().declaration()).toString());
  }

  @Test
  public void genericType_rebuilt() throws CannotGenerateCodeException {
    // See also https://github.com/inferred/FreeBuilder/issues/111
    model.newType(
        "package com.example;",
        "abstract class DataType_Builder<A, B> {}");
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<A, B> extends DataType_Builder<A, B> {}",
        "}"));

    assertThat(builder.getDatatype().getGeneratedBuilder().getQualifiedName())
        .isEqualTo(QualifiedName.of("com.example", "DataType_Builder"));
  }

  @Test
  public void wrongBuilderSuperclass_errorType() throws CannotGenerateCodeException {
    analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Builder extends SomeOther_Builder { }",
        "}"));

    messager.verifyError("Builder", "Builder extends the wrong type (should be DataType_Builder)");
  }

  @Test
  public void wrongBuilderSuperclass_actualType() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "class SomeOther_Builder { }");
    analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Builder extends SomeOther_Builder { }",
        "}"));

    messager.verifyError("Builder", "Builder extends the wrong type (should be DataType_Builder)");
  }

  @Test
  public void explicitPackageScopeNoArgsConstructor() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType() { }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    QualifiedName generatedType = QualifiedName.of("com.example", "DataType_Builder");
    Datatype datatype = new Datatype.Builder()
        .setBuilder(QualifiedName.of("com.example", "DataType", "Builder").withParameters())
        .setExtensible(true)
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(generatedType.withParameters())
        .setHasToBuilderMethod(false)
        .setInterfaceType(false)
        .setPartialType(generatedType.nestedType("Partial").withParameters())
        .setPropertyEnum(generatedType.nestedType("Property").withParameters())
        .setRebuildableType(generatedType.nestedType("Rebuildable").withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(generatedType.nestedType("Value").withParameters())
        .build();

    assertEquals(datatype, builder.getDatatype());
  }

  @Test
  public void multipleConstructors() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    QualifiedName generatedType = QualifiedName.of("com.example", "DataType_Builder");
    Datatype datatype = new Datatype.Builder()
        .setBuilder(QualifiedName.of("com.example", "DataType", "Builder").withParameters())
        .setExtensible(true)
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(generatedType.withParameters())
        .setHasToBuilderMethod(false)
        .setInterfaceType(false)
        .setPartialType(generatedType.nestedType("Partial").withParameters())
        .setPropertyEnum(generatedType.nestedType("Property").withParameters())
        .setRebuildableType(generatedType.nestedType("Rebuildable").withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(generatedType.nestedType("Value").withParameters())
        .build();

    assertEquals(datatype, builder.getDatatype());
  }

  @Test
  public void explicitPrivateScopeNoArgsConstructor() {
    try {
      analyser.analyse(model.newType(
          "package com.example;",
          "public class DataType {",
          "  private DataType() { }",
          "}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError(
        "<init>", "FreeBuilder types must have a package-visible no-args constructor");
  }

  @Test
  public void noNoArgsConstructor() {
    try {
      analyser.analyse(model.newType(
          "package com.example;",
          "public class DataType {",
          "  private DataType(int x) { }",
          "}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError(
        "DataType", "FreeBuilder types must have a package-visible no-args constructor");
  }

  @Test
  public void freeEnumBuilder() {
    try {
      analyser.analyse(model.newType(
          "package com.example;",
          "public enum DataType {}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError("DataType", "FreeBuilder does not support enum types");
  }

  @Test
  public void unnamedPackage() {
    try {
      analyser.analyse(model.newType("public class DataType {}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError("DataType", "FreeBuilder does not support types in unnamed packages");
  }

  @Test
  public void freeAnnotationBuilder() {
    try {
      analyser.analyse(model.newType(
          "package com.example;",
          "public @interface DataType {}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError("DataType", "FreeBuilder does not support annotation types");
  }

  @Test
  public void isFullyCheckedCast_nonGenericType() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).hasSize(1);
    Property property = getOnlyElement(builder.getGeneratorsByProperty().keySet());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_erasedType() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).hasSize(1);
    Property property = getOnlyElement(builder.getGeneratorsByProperty().keySet());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_wildcardType() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<?> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).hasSize(1);
    Property property = getOnlyElement(builder.getGeneratorsByProperty().keySet());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_genericType() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<String> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).hasSize(1);
    Property property = getOnlyElement(builder.getGeneratorsByProperty().keySet());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_lowerBoundWildcard() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Number> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).hasSize(1);
    Property property = getOnlyElement(builder.getGeneratorsByProperty().keySet());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_objectLowerBoundWildcard() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Object> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).hasSize(1);
    Property property = getOnlyElement(builder.getGeneratorsByProperty().keySet());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_oneWildcard() throws CannotGenerateCodeException {
    GeneratedBuilder builder = (GeneratedBuilder) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract java.util.Map<?, String> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getGeneratorsByProperty()).hasSize(1);
    Property property = getOnlyElement(builder.getGeneratorsByProperty().keySet());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void typeNotNamedBuilderIgnored() throws CannotGenerateCodeException {
    analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Bulider extends DataType_Builder {}",
        "}"));

    messager.verifyNote("DataType", addBuilderToInterfaceMessage("DataType_Builder"));
  }

  @Test
  public void missingGenericParametersOnBuilder() throws CannotGenerateCodeException {
    // See also https://github.com/inferred/FreeBuilder/issues/110
    GeneratedType builder = analyser.analyse(model.newType(
        "package com.example;",
        "import java.util.*;",
        "public class DataType<A> {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder).isInstanceOf(GeneratedStub.class);
    messager.verifyError("Builder", "Builder must be generic");
  }

  @Test
  public void incorrectGenericParametersOnBuilder() throws CannotGenerateCodeException {
    GeneratedType builder = analyser.analyse(model.newType(
        "package com.example;",
        "import java.util.*;",
        "public class DataType<A> {",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}"));

    assertThat(builder).isInstanceOf(GeneratedStub.class);
    messager.verifyError("Builder", "Builder has the wrong type parameters");
  }

  private static String addBuilderToClassMessage(String builder) {
    return "Add \"public static class Builder extends "
        + builder
        + " {}\" to your class to enable the FreeBuilder API";
  }

  private static String addBuilderToInterfaceMessage(String builder) {
    return "Add \"class Builder extends "
        + builder
        + " {}\" to your interface to enable the FreeBuilder API";
  }

  private static String asSource(Excerpt annotation) {
    return SourceBuilder.forTesting().add(annotation).toString().trim();
  }

  private static Map<String, Property> propertiesByName(GeneratedBuilder builder) {
    return builder.getGeneratorsByProperty()
        .keySet()
        .stream()
        .collect(toMap(Property::getName, $ -> $));
  }

  private static Map<String, PropertyCodeGenerator> generatorsByName(GeneratedBuilder builder) {
    ImmutableMap.Builder<String, PropertyCodeGenerator> result = ImmutableMap.builder();
    builder.getGeneratorsByProperty().forEach((property, generator) -> {
      result.put(property.getName(), generator);
    });
    return result.build();
  }
}
