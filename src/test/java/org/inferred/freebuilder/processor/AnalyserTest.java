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
import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.truth.Truth.assertThat;
import static org.inferred.freebuilder.processor.BuilderFactory.BUILDER_METHOD;
import static org.inferred.freebuilder.processor.BuilderFactory.NEW_BUILDER_METHOD;
import static org.inferred.freebuilder.processor.BuilderFactory.NO_ARGS_CONSTRUCTOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import javax.annotation.Generated;
import javax.annotation.Nullable;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.Metadata.UnderrideLevel;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.ImpliedClass;
import org.inferred.freebuilder.processor.util.testing.FakeMessager;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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

    PackageElement pkg = model.elementUtils().getPackageOf(dataType);
    ImpliedClass expectedBuilder =
        new ImpliedClass(pkg, "DataType_Builder", dataType, model.elementUtils());
    Metadata expectedMetadata = new Metadata.Builder(model.elementUtils())
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(true)
        .setGeneratedBuilder(expectedBuilder)
        .setGwtCompatible(false)
        .setGwtSerializable(false)
        .setPartialType(expectedBuilder.createNestedClass("Partial"))
        .setPropertyEnum(expectedBuilder.createNestedClass("Property"))
        .setType(dataType)
        .setValueType(expectedBuilder.createNestedClass("Value"))
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"public static class Builder extends DataType_Builder {}\" to your class "
                + "to enable the @FreeBuilder API"));
  }

  @Test
  public void emptyInterface() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    PackageElement pkg = model.elementUtils().getPackageOf(dataType);
    ImpliedClass expectedBuilder =
        new ImpliedClass(pkg, "DataType_Builder", dataType, model.elementUtils());
    Metadata expectedMetadata = new Metadata.Builder(model.elementUtils())
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(true)
        .setGeneratedBuilder(expectedBuilder)
        .setGwtCompatible(false)
        .setGwtSerializable(false)
        .setPartialType(expectedBuilder.createNestedClass("Partial"))
        .setPropertyEnum(expectedBuilder.createNestedClass("Property"))
        .setType(dataType)
        .setValueType(expectedBuilder.createNestedClass("Value"))
        .build();

    assertEquals(expectedMetadata, metadata);
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[NOTE] Add \"class Builder extends DataType_Builder {}\" to your interface "
                + "to enable the @FreeBuilder API"));
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
    assertEquals("com.example", dataType.getPackage().getQualifiedName().toString());
    assertEquals("com.example.OuterClass_DataType_Builder",
        dataType.getGeneratedBuilder().getQualifiedName().toString());
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
    assertEquals("com.example", dataType.getPackage().getQualifiedName().toString());
    assertEquals("com.example.OuterClass_InnerClass_DataType_Builder",
        dataType.getGeneratedBuilder().getQualifiedName().toString());
  }

  @Test
  public void builderSubclass() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "}"));
    assertEquals("com.example.DataType_Builder",
        dataType.getGeneratedBuilder().getQualifiedName().toString());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.getBuilderFactory()).hasValue(NO_ARGS_CONSTRUCTOR);
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
    assertEquals("com.example.DataType_Builder",
        dataType.getGeneratedBuilder().getQualifiedName().toString());
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
    assertEquals("com.example.DataType_Builder",
        dataType.getGeneratedBuilder().getQualifiedName().toString());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.getBuilderFactory()).hasValue(BUILDER_METHOD);
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
    assertEquals("com.example.DataType_Builder",
        dataType.getGeneratedBuilder().getQualifiedName().toString());
    assertEquals("com.example.DataType.Builder",
        dataType.getBuilder().getQualifiedName().toString());
    assertThat(dataType.getBuilderFactory()).hasValue(NEW_BUILDER_METHOD);
    assertFalse(dataType.isBuilderSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void twoGetters() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "}"));
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals(model.typeMirror(int.class), properties.get("age").getType());
    assertEquals(model.typeMirror(Integer.class), properties.get("age").getBoxedType());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
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
        "}"));
    Map<String, Property> properties = uniqueIndex(metadata.getProperties(), GET_NAME);
    assertEquals(
        DefaultPropertyFactory.CodeGenerator.class,
        properties.get("name").getCodeGenerator().getClass());
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
        .containsExactly("[ERROR] Getter methods must not be void on @FreeBuilder types");
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
        "[ERROR] Getter methods starting with 'is' must return a boolean on @FreeBuilder types");
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
        .containsExactly("[ERROR] Getter methods cannot take parameters on @FreeBuilder types");
  }

  @Test
  public void abstractButNotGetter() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String name();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("name");
    assertThat(messager.getMessagesByElement().get("name"))
        .containsExactly("[ERROR] Only getter methods (starting with 'get' or 'is') may be declared"
            + " abstract on @FreeBuilder types");
  }

  @Test
  public void abstractMethodNamedGet() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String get();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("get");
    assertThat(messager.getMessagesByElement().get("get"))
        .containsExactly("[ERROR] Only getter methods (starting with 'get' or 'is') may be declared"
            + " abstract on @FreeBuilder types");
  }

  @Test
  public void abstractMethodNamedGetter() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getter();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getter");
    assertThat(messager.getMessagesByElement().get("getter"))
        .containsExactly("[ERROR] Getter methods cannot have a lowercase character immediately"
            + " after the 'get' prefix on @FreeBuilder types (did you mean 'getTer'?)");
  }

  @Test
  public void abstractMethodNamedIssue() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String issue();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("issue");
    assertThat(messager.getMessagesByElement().get("issue"))
        .containsExactly("[ERROR] Getter methods cannot have a lowercase character immediately"
            + " after the 'is' prefix on @FreeBuilder types (did you mean 'isSue'?)");
  }

  @Test
  public void abstractMethodNamedGetürkt() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getürkt();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("getürkt");
    assertThat(messager.getMessagesByElement().get("getürkt"))
        .containsExactly("[ERROR] Getter methods cannot have a lowercase character immediately"
            + " after the 'get' prefix on @FreeBuilder types (did you mean 'getÜrkt'?)");
  }

  @Test
  public void abstractMethodNamedIs() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean is();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    assertThat(dataType.getProperties()).isEmpty();
    assertThat(messager.getMessagesByElement().keys()).containsExactly("is");
    assertThat(messager.getMessagesByElement().get("is"))
        .containsExactly("[ERROR] Only getter methods (starting with 'get' or 'is') may be declared"
            + " abstract on @FreeBuilder types");
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
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
        .containsExactly("[ERROR] Getter methods must not be void on @FreeBuilder types");
    assertThat(messager.getMessagesByElement().get("isDoubleBarrelled"))
        .containsExactly("[ERROR] Getter methods starting with 'is' must return a boolean"
            + " on @FreeBuilder types");
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
    assertEquals(Type.REQUIRED, properties.get("name").getCodeGenerator().getType());
    assertEquals(Type.REQUIRED, properties.get("age").getCodeGenerator().getType());
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
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
    assertTrue(dataType.isGwtCompatible());
    assertFalse(dataType.isGwtSerializable());
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
    assertTrue(dataType.isGwtCompatible());
    assertTrue(dataType.isGwtSerializable());
    assertThat(messager.getMessagesByElement().keys()).isEmpty();
  }

  @Test
  public void nullable() throws CannotGenerateCodeException {
    Metadata dataType = analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract @" + Nullable.class.getName() + " String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name");
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(messager.getMessagesByElement().keys())
        .containsExactly("getName@Nullable");
    assertThat(messager.getMessagesByElement().get("getName@Nullable")).containsExactly(
        "[ERROR] Nullable properties not supported on @FreeBuilder types (b/16057590)");
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
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.HASH_CODE, UnderrideLevel.ABSENT,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("equals", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @FreeBuilder types"));
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
        StandardMethod.EQUALS, UnderrideLevel.ABSENT,
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("hashCode", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @FreeBuilder types"));
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
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
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
        StandardMethod.EQUALS, UnderrideLevel.ABSENT,
        StandardMethod.HASH_CODE, UnderrideLevel.ABSENT,
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
        StandardMethod.EQUALS, UnderrideLevel.FINAL,
        StandardMethod.HASH_CODE, UnderrideLevel.ABSENT,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("equals", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @FreeBuilder types"));
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
        StandardMethod.EQUALS, UnderrideLevel.ABSENT,
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("hashCode", ImmutableList.of(
            "[ERROR] hashCode and equals must be implemented together on @FreeBuilder types"));
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
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
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
        StandardMethod.EQUALS, UnderrideLevel.ABSENT,
        StandardMethod.HASH_CODE, UnderrideLevel.ABSENT,
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

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.ABSENT,
        StandardMethod.HASH_CODE, UnderrideLevel.ABSENT,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
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

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.ABSENT,
        StandardMethod.HASH_CODE, UnderrideLevel.ABSENT,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
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

    assertThat(metadata.getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.ABSENT,
        StandardMethod.HASH_CODE, UnderrideLevel.ABSENT,
        StandardMethod.TO_STRING, UnderrideLevel.ABSENT));
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
            "[ERROR] @FreeBuilder types cannot be private"));
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
            "[ERROR] @FreeBuilder types cannot be private, "
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
            "[ERROR] Inner classes cannot be @FreeBuilder types "
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
    Map<String, Property> properties = uniqueIndex(dataType.getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name");
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(dataType.getBuilderFactory()).isAbsent();
    assertThat(messager.getMessagesByElement().asMap()).containsEntry(
        "Builder", ImmutableList.of("[ERROR] Builder must be static on @FreeBuilder types"));
  }

  @Test
  public void genericType() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType<T> {}");

    try {
      analyser.analyse(dataType);
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    assertThat(messager.getMessagesByElement().asMap())
        .containsEntry("DataType", ImmutableList.of(
            "[ERROR] Generic @FreeBuilder types not yet supported (b/17278322)"));
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

    PackageElement pkg = model.elementUtils().getPackageOf(dataType);
    ImpliedClass expectedBuilder =
        new ImpliedClass(pkg, "DataType_Builder", dataType, model.elementUtils());
    Metadata expectedMetadata = new Metadata.Builder(model.elementUtils())
        .setBuilder(model.typeElement("com.example.DataType.Builder"))
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(expectedBuilder)
        .setGwtCompatible(false)
        .setGwtSerializable(false)
        .setPartialType(expectedBuilder.createNestedClass("Partial"))
        .setPropertyEnum(expectedBuilder.createNestedClass("Property"))
        .setType(dataType)
        .setValueType(expectedBuilder.createNestedClass("Value"))
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

    PackageElement pkg = model.elementUtils().getPackageOf(dataType);
    ImpliedClass expectedBuilder =
        new ImpliedClass(pkg, "DataType_Builder", dataType, model.elementUtils());
    Metadata expectedMetadata = new Metadata.Builder(model.elementUtils())
        .setBuilder(model.typeElement("com.example.DataType.Builder"))
        .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(expectedBuilder)
        .setGwtCompatible(false)
        .setGwtSerializable(false)
        .setPartialType(expectedBuilder.createNestedClass("Partial"))
        .setPropertyEnum(expectedBuilder.createNestedClass("Property"))
        .setType(dataType)
        .setValueType(expectedBuilder.createNestedClass("Value"))
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
            "[ERROR] @FreeBuilder types must have a package-visible no-args constructor"));
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
            "[ERROR] @FreeBuilder types must have a package-visible no-args constructor"));
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
            "[ERROR] @FreeBuilder does not support enum types"));
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
            "[ERROR] @FreeBuilder does not support annotation types"));
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
                + "to enable the @FreeBuilder API"));
  }

  private static final Function<Property, String> GET_NAME = new Function<Property, String>() {
    @Override
    public String apply(Property propery) {
      return propery.getName();
    }
  };
}
