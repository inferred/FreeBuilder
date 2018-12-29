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
import static java.util.stream.Collectors.toMap;
import static org.inferred.freebuilder.processor.BuilderFactory.BUILDER_METHOD;
import static org.inferred.freebuilder.processor.BuilderFactory.NEW_BUILDER_METHOD;
import static org.inferred.freebuilder.processor.BuilderFactory.NO_ARGS_CONSTRUCTOR;
import static org.inferred.freebuilder.processor.Metadata.Visibility.PACKAGE;
import static org.inferred.freebuilder.processor.Metadata.Visibility.PRIVATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.annotations.GwtCompatible;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.Metadata.UnderrideLevel;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.inferred.freebuilder.processor.util.testing.FakeMessager;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.temporal.Temporal;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Generated;
import javax.lang.model.element.TypeElement;

/** Unit tests for {@link Analyser}. */
@RunWith(JUnit4.class)
public class AnalyserTest {

  @Rule public final ModelRule model = new ModelRule();
  private final FakeMessager messager = new FakeMessager();

  private Analyser analyser;

  @Before
  public void setup() {
    analyser = new Analyser(
        model.elementUtils(),
        messager,
        MethodIntrospector.instance(model.environment()),
        model.typeUtils());
  }

  @Test
  public void emptyDataType() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setExtensible(false)
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(true)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setHasToBuilderMethod(false)
        .setInterfaceType(false)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"public static class Builder extends DataType_Builder {}\" to your class "
                + "to enable the FreeBuilder API"));
  }

  @Test
  public void emptyInterface() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setExtensible(false)
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(true)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setHasToBuilderMethod(false)
        .setInterfaceType(true)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"class Builder extends DataType_Builder {}\" to your interface "
                + "to enable the FreeBuilder API"));
  }

  @Test
  public void nestedDataType() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse((TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class OuterClass {",
        "  ---> public static class DataType {",
        "  }",
        "}"));
    assertEquals("com.example.OuterClass.DataType",
        dataType.getType().getQualifiedName().toString());
    assertEquals(QualifiedName.of("com.example", "OuterClass_DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
  }

  @Test
  public void twiceNestedDataType() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse((TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class OuterClass {",
        "  public static class InnerClass {",
        "    ---> public static class DataType {",
        "    }",
        "  }",
        "}"));
    assertEquals("com.example.OuterClass.InnerClass.DataType",
        dataType.getType().getQualifiedName().toString());
    assertEquals(
        QualifiedName.of("com.example", "OuterClass_InnerClass_DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
  }

  @Test
  public void builderSubclass() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.isExtensible()).isTrue();
    assertThat(dataType.getBuilderFactory().get()).isEqualTo(NO_ARGS_CONSTRUCTOR);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void serializableBuilderSubclass() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder ",
        "      extends DataType_Builder implements java.io.Serializable { }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertTrue(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void builderSubclass_publicBuilderMethod() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.isExtensible()).isTrue();
    assertThat(dataType.getBuilderFactory().get()).isEqualTo(BUILDER_METHOD);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void builderSubclass_publicBuilderMethod_protectedConstructor()
      throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder {",
        "    protected Builder() { }",
        "  }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.isExtensible()).isTrue();
    assertThat(dataType.getBuilderFactory().get()).isEqualTo(BUILDER_METHOD);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void builderSubclass_publicBuilderMethod_privateConstructor()
      throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder {",
        "    private Builder() { }",
        "  }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.isExtensible()).isFalse();
    assertThat(dataType.getBuilderFactory().get()).isEqualTo(BUILDER_METHOD);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void builderSubclass_publicNewBuilderMethod() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder newBuilder() { return new Builder(); }",
        "}"));
    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        dataType.getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.isExtensible()).isTrue();
    assertThat(dataType.getBuilderFactory().get()).isEqualTo(NEW_BUILDER_METHOD);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void toBuilderMethod() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "  Builder toBuilder();",
        "  class Builder extends DataType_Builder { }",
        "}"));
    assertTrue(dataType.getHasToBuilderMethod());
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void toBuilderMethod_genericInterface() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType<K, V> {",
        "  Builder<K, V> toBuilder();",
        "  class Builder<K, V> extends DataType_Builder<K, V> { }",
        "}"));
    assertTrue(dataType.getHasToBuilderMethod());
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
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
    assertThat(messager.getMessagesByElement().keySet()).containsExactly("toBuilder");
    assertThat(messager.getMessagesByElement().get("toBuilder"))
        .containsExactly("[ERROR] No accessible no-args Builder constructor available to "
            + "implement toBuilder");
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
    assertThat(messager.getMessagesByElement().keySet()).containsExactly("toBuilder");
    assertThat(messager.getMessagesByElement().get("toBuilder"))
        .containsExactly("[ERROR] No accessible no-args Builder constructor available to "
            + "implement toBuilder");
  }

  @Test
  public void twoBeanGetters() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name", "age");

    Property age = properties.get("age");
    assertEquals(model.typeMirror(int.class), age.getType());
    assertThat(age.getBoxedType().get()).isEqualTo(model.typeMirror(Integer.class));
    assertEquals("AGE", age.getAllCapsName());
    assertEquals("Age", age.getCapitalizedName());
    assertEquals("getAge", age.getGetterName());
    assertTrue(age.isUsingBeanConvention());

    Property name = properties.get("name");
    assertEquals("java.lang.String", name.getType().toString());
    assertThat(name.getBoxedType()).isEqualTo(Optional.empty());
    assertEquals("NAME", name.getAllCapsName());
    assertEquals("Name", name.getCapitalizedName());
    assertEquals("getName", name.getGetterName());
    assertTrue(name.isUsingBeanConvention());
  }

  @Test
  public void twoPrefixlessGetters() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String name();",
        "  public abstract int age();",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name", "age");

    Property age = properties.get("age");
    assertEquals(model.typeMirror(int.class), age.getType());
    assertThat(age.getBoxedType().get()).isEqualTo(model.typeMirror(Integer.class));
    assertEquals("AGE", age.getAllCapsName());
    assertEquals("Age", age.getCapitalizedName());
    assertEquals("age", age.getGetterName());
    assertFalse(age.isUsingBeanConvention());

    Property name = properties.get("name");
    assertEquals("java.lang.String", name.getType().toString());
    assertThat(name.getBoxedType()).isEqualTo(Optional.empty());
    assertEquals("NAME", name.getAllCapsName());
    assertEquals("Name", name.getCapitalizedName());
    assertEquals("name", name.getGetterName());
    assertFalse(name.isUsingBeanConvention());
  }

  @Test
  public void complexGetterNames() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getCustomURLTemplate();",
        "  public abstract String getTop50Sites();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("customURLTemplate", "top50Sites");
    assertEquals("CUSTOM_URL_TEMPLATE", properties.get("customURLTemplate").getAllCapsName());
    assertEquals("TOP50_SITES", properties.get("top50Sites").getAllCapsName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void twoGetters_interface() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  int getAge();",
        "  class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals(model.typeMirror(int.class), properties.get("age").getType());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  public void booleanGetter() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean isAvailable();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("available");
    assertEquals(model.typeMirror(boolean.class), properties.get("available").getType());
    assertEquals("Available", properties.get("available").getCapitalizedName());
    assertEquals("isAvailable", properties.get("available").getGetterName());
    assertEquals("AVAILABLE", properties.get("available").getAllCapsName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void finalGetter() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public final String getName() {",
        "    return null;",
        "  }",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
  }

  @Test
  public void defaultCodeGenerator() throws CannotGenerateCodeException {
    Metadata metadata = analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = metadata.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertEquals(DefaultProperty.class, properties.get("name").getCodeGenerator().get().getClass());
  }

  @Test
  public void nonAbstractGetter() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public String getName() {",
        "    return null;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void nonAbstractMethodNamedIssue() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public boolean issue() {",
        "    return true;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void voidGetter() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract void getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getName");
    assertThat(messager.getMessagesByElement().get("getName"))
        .containsExactly("[ERROR] Getter methods must not be void on FreeBuilder types");
  }

  @Test
  public void nonBooleanIsMethod() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String isName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("isName");
    assertThat(messager.getMessagesByElement().get("isName")).containsExactly(
        "[ERROR] Getter methods starting with 'is' must return a boolean on FreeBuilder types");
  }

  @Test
  public void getterWithArgument() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName(boolean capitalized);",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getName");
    assertThat(messager.getMessagesByElement().get("getName"))
        .containsExactly("[ERROR] Getter methods cannot take parameters on FreeBuilder types");
  }

  @Test
  public void abstractMethodNamedGet() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String get();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("get");
    assertEquals("Get", properties.get("get").getCapitalizedName());
    assertEquals("get", properties.get("get").getGetterName());
    assertEquals("GET", properties.get("get").getAllCapsName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void abstractMethodNamedGetter() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getter();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("getter");
    assertEquals("Getter", properties.get("getter").getCapitalizedName());
    assertEquals("getter", properties.get("getter").getGetterName());
    assertEquals("GETTER", properties.get("getter").getAllCapsName());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void abstractMethodNamedIssue() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String issue();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("issue");
    assertEquals("ISSUE", properties.get("issue").getAllCapsName());
    assertEquals("Issue", properties.get("issue").getCapitalizedName());
    assertEquals("issue", properties.get("issue").getGetterName());
    assertEquals("java.lang.String", properties.get("issue").getType().toString());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void abstractMethodWithNonAsciiName() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getürkt();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("getürkt");
    assertEquals("GETÜRKT", properties.get("getürkt").getAllCapsName());
    assertEquals("Getürkt", properties.get("getürkt").getCapitalizedName());
    assertEquals("getürkt", properties.get("getürkt").getGetterName());
    assertEquals("java.lang.String", properties.get("getürkt").getType().toString());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void abstractMethodNamedIs() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean is();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("is");
    assertEquals("IS", properties.get("is").getAllCapsName());
    assertEquals("Is", properties.get("is").getCapitalizedName());
    assertEquals("is", properties.get("is").getGetterName());
    assertEquals("boolean", properties.get("is").getType().toString());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void mixedValidAndInvalidGetters() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract void getNothing();",
        "  public abstract int getAge();",
        "  public abstract float isDoubleBarrelled();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(messager.getMessagesByElement().keys())
        .containsExactly("getNothing", "isDoubleBarrelled");
    assertThat(messager.getMessagesByElement().get("getNothing"))
        .containsExactly("[ERROR] Getter methods must not be void on FreeBuilder types");
    assertThat(messager.getMessagesByElement().get("isDoubleBarrelled"))
        .containsExactly("[ERROR] Getter methods starting with 'is' must return a boolean"
            + " on FreeBuilder types");
  }

  @Test
  public void noDefaults() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "  public static class Builder extends DataType_Builder {",
        "    public Builder() {",
        "    }",
        "  }",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertEquals(Type.REQUIRED, properties.get("name").getCodeGenerator().get().getType());
    assertEquals(Type.REQUIRED, properties.get("age").getCodeGenerator().get().getType());
  }

  @Test
  public void implementsInterface() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public interface IDataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "}");
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name", "age");
  }

  @Test
  public void implementsGenericInterface() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public interface IDataType<T> {",
        "  public abstract T getProperty();",
        "}");
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType<String> {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("property");
    assertEquals("java.lang.String", properties.get("property").getType().toString());
  }

  @Test
  public void notGwtSerializable() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = false)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(dataType.getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(dataType.getValueTypeVisibility()).isEqualTo(PRIVATE);
    assertThat(dataType.getValueTypeAnnotations()).isEmpty();
    assertThat(dataType.getNestedClasses()).isEmpty();
    assertThat(dataType.getVisibleNestedTypes()).containsNoneOf(
        QualifiedName.of("com.example", "DataType", "Value_CustomFieldSerializer"),
        QualifiedName.of("com.example", "DataType", "GwtWhitelist"));
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void gwtSerializable() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = true)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(dataType.getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(dataType.getValueTypeVisibility()).isEqualTo(PACKAGE);
    assertThat(dataType.getValueTypeAnnotations()).hasSize(1);
    assertThat(asSource(dataType.getValueTypeAnnotations().get(0)))
        .isEqualTo("@GwtCompatible(serializable = true)");
    assertThat(dataType.getNestedClasses()).hasSize(2);
    assertThat(dataType.getVisibleNestedTypes()).containsAllOf(
        QualifiedName.of("com.example", "DataType_Builder", "Value_CustomFieldSerializer"),
        QualifiedName.of("com.example", "DataType_Builder", "GwtWhitelist"));
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void underriddenEquals() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("equals", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on FreeBuilder types"));
  }

  @Test
  public void underriddenHashCode() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("hashCode", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on FreeBuilder types"));
  }

  @Test
  public void underriddenHashCodeAndEquals() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void underriddenToString() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.TO_STRING, UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void underriddenTriad() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
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
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.TO_STRING, UnderrideLevel.OVERRIDEABLE));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void finalEquals() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("equals", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on FreeBuilder types"));
  }

  @Test
  public void finalHashCode() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("hashCode", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on FreeBuilder types"));
  }

  @Test
  public void finalHashCodeAndEquals() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL,
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void finalToString() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.TO_STRING, UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void finalTriad() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
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
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL,
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL,
        StandardMethod.TO_STRING, UnderrideLevel.FINAL));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void abstractEquals() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract boolean equals(Object obj);",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEmpty();
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void abstractHashCode() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract int hashCode();",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEmpty();
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void abstractToString() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about how this is a useful toString implementation. */",
        "  @Override public abstract String toString();",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getStandardMethodUnderrides()).isEmpty();
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
  }

  @Test
  public void privateNestedType() {
    TypeElement privateType = (TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> private static class PrivateType {",
        "  }",
        "}");

    try {
      analyser.analyse(privateType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("PrivateType", ImmutableList.of(
            "[ERROR] FreeBuilder types cannot be private"));
  }

  @Test
  public void indirectlyPrivateNestedType() {
    TypeElement nestedType = (TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  private static class PrivateType {",
        "    ---> static class NestedType {",
        "    }",
        "  }",
        "}");

    try {
      analyser.analyse(nestedType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("NestedType", ImmutableList.of(
            "[ERROR] FreeBuilder types cannot be private, "
                + "but enclosing type PrivateType is inaccessible"));
  }

  @Test
  public void innerType() {
    TypeElement innerType = (TypeElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> public class InnerType {",
        "  }",
        "}");

    try {
      analyser.analyse(innerType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("InnerType", ImmutableList.of(
            "[ERROR] Inner classes cannot be FreeBuilder types "
                + "(did you forget the static keyword?)"));
  }

  @Test
  public void nonStaticBuilder() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name");
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertThat(properties.get("name").getBoxedType()).isEqualTo(Optional.empty());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(dataType.isExtensible()).isFalse();
    assertThat(dataType.getBuilderFactory()).isEqualTo(Optional.empty());
    assertThat(messager.getMessagesByElement().asMap()).containsEntry(
        "Builder", ImmutableList.of("[ERROR] Builder must be static on FreeBuilder types"));
  }

  @Test
  public void genericType() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}"));
    assertEquals("com.example.DataType.Builder<A, B>", dataType.getBuilder().toString());
    assertThat(dataType.isExtensible()).isTrue();
    assertEquals(Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR), dataType.getBuilderFactory());
    assertEquals("com.example.DataType_Builder<A, B>", dataType.getGeneratedBuilder().toString());
    assertEquals("com.example.DataType_Builder.Partial<A, B>",
        dataType.getPartialType().toString());
    assertEquals("com.example.DataType_Builder.Property", dataType.getPropertyEnum().toString());
    assertEquals("com.example.DataType<A, B>", dataType.getType().toString());
    assertEquals("com.example.DataType_Builder.Value<A, B>", dataType.getValueType().toString());
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("B", properties.get("age").getType().toString());
    assertThat(properties.get("age").getBoxedType()).isEqualTo(Optional.empty());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("A", properties.get("name").getType().toString());
    assertThat(properties.get("name").getBoxedType()).isEqualTo(Optional.empty());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
  }

  @Test
  public void genericTypeWithBounds() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A extends CharSequence, B extends " + Temporal.class.getName()
            + "> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}"));
    assertEquals("com.example.DataType.Builder<A, B>", dataType.getBuilder().toString());
    assertEquals("DataType<A extends CharSequence, B extends Temporal>",
        SourceStringBuilder.simple().add(dataType.getType().declaration()).toString());
  }

  /** @see <a href="https://github.com/google/FreeBuilder/issues/111">Issue 111</a> */
  @Test
  public void genericType_rebuilt() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "abstract class DataType_Builder<A, B> {}");
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<A, B> extends DataType_Builder<A, B> {}",
        "}"));
    assertThat(messager.getMessagesByElement().asMap()).isEmpty();
    assertEquals("com.example.DataType.Builder<A, B>", dataType.getBuilder().toString());
    assertThat(dataType.isExtensible()).isTrue();
    assertEquals(Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR), dataType.getBuilderFactory());
    assertEquals("com.example.DataType_Builder<A, B>", dataType.getGeneratedBuilder().toString());
    assertEquals("com.example.DataType_Builder.Partial<A, B>",
        dataType.getPartialType().toString());
    assertEquals("com.example.DataType_Builder.Property", dataType.getPropertyEnum().toString());
    assertEquals("com.example.DataType<A, B>", dataType.getType().toString());
    assertEquals("com.example.DataType_Builder.Value<A, B>", dataType.getValueType().toString());
    Map<String, Property> properties = dataType.getProperties().stream()
        .collect(toMap(Property::getName, $ -> $));
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("B", properties.get("age").getType().toString());
    assertThat(properties.get("age").getBoxedType()).isEqualTo(Optional.empty());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("A", properties.get("name").getType().toString());
    assertThat(properties.get("name").getBoxedType()).isEqualTo(Optional.empty());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
  }

  @Test
  public void wrongBuilderSuperclass_errorType() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Builder extends SomeOther_Builder { }",
        "}");

    analyser.analyse(dataType);

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("Builder", ImmutableList.of(
            "[ERROR] Builder extends the wrong type (should be DataType_Builder)"));
  }

  @Test
  public void wrongBuilderSuperclass_actualType() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "@" + Generated.class.getCanonicalName() + "(\"FreeBuilder FTW!\")",
        "class SomeOther_Builder { }");
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Builder extends SomeOther_Builder { }",
        "}");

    analyser.analyse(dataType);

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("Builder", ImmutableList.of(
            "[ERROR] Builder extends the wrong type (should be DataType_Builder)"));
  }

  @Test
  public void explicitPackageScopeNoArgsConstructor() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType() { }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    TypeElement concreteBuilder = model.typeElement("com.example.DataType.Builder");
    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setBuilder(QualifiedName.of("com.example", "DataType", "Builder").withParameters())
        .setExtensible(true)
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setHasToBuilderMethod(false)
        .setInterfaceType(false)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(QualifiedName.of(concreteBuilder))
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void multipleConstructors() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    TypeElement concreteBuilder = model.typeElement("com.example.DataType.Builder");
    QualifiedName expectedBuilder = QualifiedName.of("com.example", "DataType_Builder");
    QualifiedName partialType = expectedBuilder.nestedType("Partial");
    QualifiedName propertyType = expectedBuilder.nestedType("Property");
    QualifiedName valueType = expectedBuilder.nestedType("Value");
    Metadata expectedMetadata = new Metadata.Builder()
        .setBuilder(QualifiedName.of("com.example", "DataType", "Builder").withParameters())
        .setExtensible(true)
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(expectedBuilder.withParameters())
        .setHasToBuilderMethod(false)
        .setInterfaceType(false)
        .setPartialType(partialType.withParameters())
        .setPropertyEnum(propertyType.withParameters())
        .setType(QualifiedName.of("com.example", "DataType").withParameters())
        .setValueType(valueType.withParameters())
        .addVisibleNestedTypes(QualifiedName.of(concreteBuilder))
        .addVisibleNestedTypes(partialType)
        .addVisibleNestedTypes(propertyType)
        .addVisibleNestedTypes(valueType)
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void explicitPrivateScopeNoArgsConstructor() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  private DataType() { }",
        "}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("<init>", ImmutableList.of(
            "[ERROR] FreeBuilder types must have a package-visible no-args constructor"));
  }

  @Test
  public void noNoArgsConstructor() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  private DataType(int x) { }",
        "}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] FreeBuilder types must have a package-visible no-args constructor"));
  }

  @Test
  public void freeEnumBuilder() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public enum DataType {}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] FreeBuilder does not support enum types"));
  }

  @Test
  public void unnamedPackage() {
    TypeElement dataType = model.newType(
        "public class DataType {}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] FreeBuilder does not support types in unnamed packages"));
  }

  @Test
  public void freeAnnotationBuilder() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public @interface DataType {}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] FreeBuilder does not support annotation types"));
  }

  @Test
  public void isFullyCheckedCast_nonGenericType() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_erasedType() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_wildcardType() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<?> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_genericType() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<String> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_lowerBoundWildcard() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Number> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_objectLowerBoundWildcard() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Object> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_oneWildcard() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract java.util.Map<?, String> getProperty();",
        "}"));
    assertThat(dataType.getProperties()).hasSize(1);
    Property property = getOnlyElement(dataType.getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void typeNotNamedBuilderIgnored() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  class Bulider extends DataType_Builder {}",
        "}");

    analyser.analyse(dataType);

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"class Builder extends DataType_Builder {}\" to your interface "
                + "to enable the FreeBuilder API"));
  }

  @Test
  public void valueTypeNestedClassesAddedToVisibleList() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "  public interface Objects {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getVisibleNestedTypes()).containsExactly(
        QualifiedName.of("com.example", "DataType", "Builder"),
        QualifiedName.of("com.example", "DataType", "Objects"),
        QualifiedName.of("com.example", "DataType_Builder", "Partial"),
        QualifiedName.of("com.example", "DataType_Builder", "Property"),
        QualifiedName.of("com.example", "DataType_Builder", "Value"));
  }

  @Test
  public void valueTypeSuperclassesNestedClassesAddedToVisibleList()
      throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public class SuperType {",
        "  public interface Objects {}",
        "}");
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType extends SuperType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    assertThat(metadata.getVisibleNestedTypes()).containsExactly(
        QualifiedName.of("com.example", "SuperType", "Objects"),
        QualifiedName.of("com.example", "DataType", "Builder"),
        QualifiedName.of("com.example", "DataType_Builder", "Partial"),
        QualifiedName.of("com.example", "DataType_Builder", "Property"),
        QualifiedName.of("com.example", "DataType_Builder", "Value"));
  }

  @Test
  public void missingGenericParametersOnBuilder() throws CannotGenerateCodeException {
    // See also https://github.com/inferred/FreeBuilder/issues/110
    TypeElement dataType = model.newType(
        "package com.example;",
        "import java.util.*;",
        "public class DataType<A> {",
        "  public static class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);
    assertThat(metadata.getOptionalBuilder()).isEqualTo(Optional.empty());
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("Builder", ImmutableList.of("[ERROR] Builder must be generic"));
  }

  @Test
  public void incorrectGenericParametersOnBuilder() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "import java.util.*;",
        "public class DataType<A> {",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);
    assertThat(metadata.getOptionalBuilder()).isEqualTo(Optional.empty());
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("Builder", ImmutableList.of(
            "[ERROR] Builder has the wrong type parameters"));
  }

  private static String asSource(Excerpt annotation) {
    return SourceStringBuilder.simple().add(annotation).toString().trim();
  }
}
