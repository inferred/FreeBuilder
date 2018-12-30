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
import static org.inferred.freebuilder.processor.Metadata.Visibility.PACKAGE;
import static org.inferred.freebuilder.processor.Metadata.Visibility.PRIVATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.annotations.GwtCompatible;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.Metadata.StandardMethod;
import org.inferred.freebuilder.processor.Metadata.UnderrideLevel;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Type;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.inferred.freebuilder.processor.util.testing.MessagerRule;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.temporal.Temporal;
import java.util.Map;

import javax.annotation.Generated;
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
    analyser = new Analyser(
        model.elementUtils(),
        messager,
        MethodIntrospector.instance(model.environment()),
        model.typeUtils());
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
    assertThat(builder).isEqualTo(new CodeGenerator(
        new Metadata.Builder()
            .setBuilder(dataType.nestedType("Builder").withParameters())
            .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
            .setBuilderSerializable(false)
            .setExtensible(true)
            .setGeneratedBuilder(generatedType.withParameters())
            .setHasToBuilderMethod(false)
            .setInterfaceType(false)
            .setPartialType(generatedType.nestedType("Partial").withParameters())
            .setPropertyEnum(generatedType.nestedType("Property").withParameters())
            .setType(dataType.withParameters())
            .setValueType(generatedType.nestedType("Value").withParameters())
            .addVisibleNestedTypes(
                dataType.nestedType("Builder"),
                generatedType.nestedType("Partial"),
                generatedType.nestedType("Property"),
                generatedType.nestedType("Value"))
            .build()));
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
    assertThat(builder).isEqualTo(new CodeGenerator(
        new Metadata.Builder()
            .setBuilder(dataType.nestedType("Builder").withParameters(kVar, vVar))
            .setBuilderFactory(NO_ARGS_CONSTRUCTOR)
            .setBuilderSerializable(false)
            .setExtensible(true)
            .setGeneratedBuilder(generatedType.withParameters(k, v))
            .setHasToBuilderMethod(false)
            .setInterfaceType(true)
            .setPartialType(generatedType.nestedType("Partial").withParameters(k, v))
            .setPropertyEnum(generatedType.nestedType("Property").withParameters())
            .setType(dataType.withParameters(k, v))
            .setValueType(generatedType.nestedType("Value").withParameters(k, v))
            .addVisibleNestedTypes(
                dataType.nestedType("Builder"),
                generatedType.nestedType("Partial"),
                generatedType.nestedType("Property"),
                generatedType.nestedType("Value"))
            .build()));
  }

  @Test
  public void serializableBuilderSubclass() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder ",
        "      extends DataType_Builder implements java.io.Serializable { }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getMetadata().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getMetadata().getBuilder().getQualifiedName().toString());
    assertTrue(builder.getMetadata().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicBuilderMethod() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getMetadata().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getMetadata().getBuilder().getQualifiedName().toString());
    assertThat(builder.getMetadata().isExtensible()).isTrue();
    assertThat(builder.getMetadata().getBuilderFactory()).hasValue(BUILDER_METHOD);
    assertFalse(builder.getMetadata().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicBuilderMethod_protectedConstructor()
      throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder {",
        "    protected Builder() { }",
        "  }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getMetadata().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getMetadata().getBuilder().getQualifiedName().toString());
    assertThat(builder.getMetadata().isExtensible()).isTrue();
    assertThat(builder.getMetadata().getBuilderFactory()).hasValue(BUILDER_METHOD);
    assertFalse(builder.getMetadata().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicBuilderMethod_privateConstructor()
      throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder {",
        "    private Builder() { }",
        "  }",
        "  public static Builder builder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getMetadata().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getMetadata().getBuilder().getQualifiedName().toString());
    assertThat(builder.getMetadata().isExtensible()).isFalse();
    assertThat(builder.getMetadata().getBuilderFactory()).hasValue(BUILDER_METHOD);
    assertFalse(builder.getMetadata().isBuilderSerializable());
  }

  @Test
  public void builderSubclass_publicNewBuilderMethod() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public static class Builder extends DataType_Builder { }",
        "  public static Builder newBuilder() { return new Builder(); }",
        "}"));

    assertEquals(QualifiedName.of("com.example", "DataType_Builder").withParameters(),
        builder.getMetadata().getGeneratedBuilder());
    assertEquals("com.example.DataType.Builder",
        builder.getMetadata().getBuilder().getQualifiedName().toString());
    assertThat(builder.getMetadata().isExtensible()).isTrue();
    assertThat(builder.getMetadata().getBuilderFactory()).hasValue(NEW_BUILDER_METHOD);
    assertFalse(builder.getMetadata().isBuilderSerializable());
  }

  @Test
  public void toBuilderMethod() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType {",
        "  Builder toBuilder();",
        "  class Builder extends DataType_Builder { }",
        "}"));

    assertTrue(builder.getMetadata().getHasToBuilderMethod());
    assertThat(builder.getMetadata().getProperties()).isEmpty();
  }

  @Test
  public void toBuilderMethod_genericInterface() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public interface DataType<K, V> {",
        "  Builder<K, V> toBuilder();",
        "  class Builder<K, V> extends DataType_Builder<K, V> { }",
        "}"));

    assertTrue(builder.getMetadata().getHasToBuilderMethod());
    assertThat(builder.getMetadata().getProperties()).isEmpty();
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
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");

    Property age = properties.get("age");
    assertEquals(model.typeMirror(int.class), age.getType());
    assertEquals(model.typeMirror(Integer.class), age.getBoxedType());
    assertEquals("AGE", age.getAllCapsName());
    assertEquals("Age", age.getCapitalizedName());
    assertEquals("getAge", age.getGetterName());
    assertTrue(age.isUsingBeanConvention());

    Property name = properties.get("name");
    assertEquals("java.lang.String", name.getType().toString());
    assertNull(name.getBoxedType());
    assertEquals("NAME", name.getAllCapsName());
    assertEquals("Name", name.getCapitalizedName());
    assertEquals("getName", name.getGetterName());
    assertTrue(name.isUsingBeanConvention());
  }

  @Test
  public void twoPrefixlessGetters() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String name();",
        "  public abstract int age();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");

    Property age = properties.get("age");
    assertEquals(model.typeMirror(int.class), age.getType());
    assertEquals(model.typeMirror(Integer.class), age.getBoxedType());
    assertEquals("AGE", age.getAllCapsName());
    assertEquals("Age", age.getCapitalizedName());
    assertEquals("age", age.getGetterName());
    assertFalse(age.isUsingBeanConvention());

    Property name = properties.get("name");
    assertEquals("java.lang.String", name.getType().toString());
    assertNull(name.getBoxedType());
    assertEquals("NAME", name.getAllCapsName());
    assertEquals("Name", name.getCapitalizedName());
    assertEquals("name", name.getGetterName());
    assertFalse(name.isUsingBeanConvention());
  }

  @Test
  public void complexGetterNames() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getCustomURLTemplate();",
        "  public abstract String getTop50Sites();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("customURLTemplate", "top50Sites");
    assertEquals("CUSTOM_URL_TEMPLATE", properties.get("customURLTemplate").getAllCapsName());
    assertEquals("TOP50_SITES", properties.get("top50Sites").getAllCapsName());
  }

  @Test
  public void twoGetters_interface() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  int getAge();",
        "  class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals(model.typeMirror(int.class), properties.get("age").getType());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
  }

  @Test
  public void booleanGetter() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean isAvailable();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("available");
    assertEquals(model.typeMirror(boolean.class), properties.get("available").getType());
    assertEquals("Available", properties.get("available").getCapitalizedName());
    assertEquals("isAvailable", properties.get("available").getGetterName());
    assertEquals("AVAILABLE", properties.get("available").getAllCapsName());
  }

  @Test
  public void finalGetter() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public final String getName() {",
        "    return null;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).isEmpty();
  }

  @Test
  public void defaultCodeGenerator() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "interface DataType {",
        "  String getName();",
        "  class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertEquals(DefaultProperty.class, properties.get("name").getCodeGenerator().getClass());
  }

  @Test
  public void nonAbstractGetter() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public String getName() {",
        "    return null;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).isEmpty();
  }

  @Test
  public void nonAbstractMethodNamedIssue() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public boolean issue() {",
        "    return true;",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).isEmpty();
  }

  @Test
  public void voidGetter() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract void getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).isEmpty();
    messager.verifyError("getName", "Getter methods must not be void on @FreeBuilder types");
  }

  @Test
  public void nonBooleanIsMethod() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String isName();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).isEmpty();
    messager.verifyError(
        "isName", "Getter methods starting with 'is' must return a boolean on @FreeBuilder types");
  }

  @Test
  public void getterWithArgument() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName(boolean capitalized);",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).isEmpty();
    messager.verifyError("getName", "Getter methods cannot take parameters on @FreeBuilder types");
  }

  @Test
  public void abstractMethodNamedGet() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String get();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("get");
    assertEquals("Get", properties.get("get").getCapitalizedName());
    assertEquals("get", properties.get("get").getGetterName());
    assertEquals("GET", properties.get("get").getAllCapsName());
  }

  @Test
  public void abstractMethodNamedGetter() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getter();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("getter");
    assertEquals("Getter", properties.get("getter").getCapitalizedName());
    assertEquals("getter", properties.get("getter").getGetterName());
    assertEquals("GETTER", properties.get("getter").getAllCapsName());
  }

  @Test
  public void abstractMethodNamedIssue() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String issue();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("issue");
    assertEquals("ISSUE", properties.get("issue").getAllCapsName());
    assertEquals("Issue", properties.get("issue").getCapitalizedName());
    assertEquals("issue", properties.get("issue").getGetterName());
    assertEquals("java.lang.String", properties.get("issue").getType().toString());
  }

  @Test
  public void abstractMethodWithNonAsciiName() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getürkt();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("getürkt");
    assertEquals("GETÜRKT", properties.get("getürkt").getAllCapsName());
    assertEquals("Getürkt", properties.get("getürkt").getCapitalizedName());
    assertEquals("getürkt", properties.get("getürkt").getGetterName());
    assertEquals("java.lang.String", properties.get("getürkt").getType().toString());
  }

  @Test
  public void abstractMethodNamedIs() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract boolean is();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("is");
    assertEquals("IS", properties.get("is").getAllCapsName());
    assertEquals("Is", properties.get("is").getCapitalizedName());
    assertEquals("is", properties.get("is").getGetterName());
    assertEquals("boolean", properties.get("is").getType().toString());
  }

  @Test
  public void mixedValidAndInvalidGetters() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract void getNothing();",
        "  public abstract int getAge();",
        "  public abstract float isDoubleBarrelled();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    messager.verifyError("getNothing", "Getter methods must not be void on @FreeBuilder types");
    messager.verifyError(
        "isDoubleBarrelled",
        "Getter methods starting with 'is' must return a boolean on @FreeBuilder types");
  }

  @Test
  public void noDefaults() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public abstract int getAge();",
        "  public static class Builder extends DataType_Builder {",
        "    public Builder() {",
        "    }",
        "  }",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
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
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
  }

  @Test
  public void implementsGenericInterface() throws CannotGenerateCodeException {
    model.newType(
        "package com.example;",
        "public interface IDataType<T> {",
        "  public abstract T getProperty();",
        "}");
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType implements IDataType<String> {",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("property");
    assertEquals("java.lang.String", properties.get("property").getType().toString());
  }

  @Test
  public void notGwtSerializable() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = false)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(builder.getMetadata().getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(builder.getMetadata().getValueTypeVisibility()).isEqualTo(PRIVATE);
    assertThat(builder.getMetadata().getValueTypeAnnotations()).isEmpty();
    assertThat(builder.getMetadata().getNestedClasses()).isEmpty();
    assertThat(builder.getMetadata().getVisibleNestedTypes()).containsNoneOf(
        QualifiedName.of("com.example", "DataType", "Value_CustomFieldSerializer"),
        QualifiedName.of("com.example", "DataType", "GwtWhitelist"));
  }

  @Test
  public void gwtSerializable() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "@" + GwtCompatible.class.getName() + "(serializable = true)",
        "public interface DataType {",
        "  class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getGeneratedBuilderAnnotations()).hasSize(1);
    assertThat(asSource(builder.getMetadata().getGeneratedBuilderAnnotations().get(0)))
        .isEqualTo("@GwtCompatible");
    assertThat(builder.getMetadata().getValueTypeVisibility()).isEqualTo(PACKAGE);
    assertThat(builder.getMetadata().getValueTypeAnnotations()).hasSize(1);
    assertThat(asSource(builder.getMetadata().getValueTypeAnnotations().get(0)))
        .isEqualTo("@GwtCompatible(serializable = true)");
    assertThat(builder.getMetadata().getNestedClasses()).hasSize(2);
    assertThat(builder.getMetadata().getVisibleNestedTypes()).containsAllOf(
        QualifiedName.of("com.example", "DataType_Builder", "Value_CustomFieldSerializer"),
        QualifiedName.of("com.example", "DataType_Builder", "GwtWhitelist"));
  }

  @Test
  public void underriddenEquals() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE));
    messager.verifyError(
        "equals", "hashCode and equals must be implemented together on @FreeBuilder types");
  }

  @Test
  public void underriddenHashCode() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE));
    messager.verifyError(
        "hashCode", "hashCode and equals must be implemented together on @FreeBuilder types");
  }

  @Test
  public void underriddenHashCodeAndEquals() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
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

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE));
  }

  @Test
  public void underriddenToString() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.TO_STRING, UnderrideLevel.OVERRIDEABLE));
  }

  @Test
  public void underriddenTriad() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
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

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.HASH_CODE, UnderrideLevel.OVERRIDEABLE,
        StandardMethod.TO_STRING, UnderrideLevel.OVERRIDEABLE));
  }

  @Test
  public void finalEquals() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final boolean equals(Object obj) {",
        "    return (obj instanceof DataType);",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL));
    messager.verifyError(
        "equals", "hashCode and equals must be implemented together on @FreeBuilder types");
  }

  @Test
  public void finalHashCode() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final int hashCode() {",
        "    return DataType.class.hashCode();",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL));
    messager.verifyError(
        "hashCode", "hashCode and equals must be implemented together on @FreeBuilder types");
  }

  @Test
  public void finalHashCodeAndEquals() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
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

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL,
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL));
  }

  @Test
  public void finalToString() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  @Override public final String toString() {",
        "    return \"DataType{}\";",
        "  }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.TO_STRING, UnderrideLevel.FINAL));
  }

  @Test
  public void finalTriad() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
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

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEqualTo(ImmutableMap.of(
        StandardMethod.EQUALS, UnderrideLevel.FINAL,
        StandardMethod.HASH_CODE, UnderrideLevel.FINAL,
        StandardMethod.TO_STRING, UnderrideLevel.FINAL));
  }

  @Test
  public void abstractEquals() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract boolean equals(Object obj);",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEmpty();
  }

  @Test
  public void abstractHashCode() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about value-based equality. */",
        "  @Override public abstract int hashCode();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEmpty();
  }

  @Test
  public void abstractToString() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  /** Some comment about how this is a useful toString implementation. */",
        "  @Override public abstract String toString();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getStandardMethodUnderrides()).isEmpty();
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

    messager.verifyError("PrivateType", "@FreeBuilder types cannot be private");
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
        "@FreeBuilder types cannot be private, but enclosing type PrivateType is inaccessible");
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
        "Inner classes cannot be @FreeBuilder types (did you forget the static keyword?)");
  }

  @Test
  public void nonStaticBuilder() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getName();",
        "  public class Builder extends DataType_Builder {}",
        "}"));

    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name");
    assertEquals("java.lang.String", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
    assertThat(builder.getMetadata().isExtensible()).isFalse();
    assertThat(builder.getMetadata().getBuilderFactory()).isAbsent();
    messager.verifyError("Builder", "Builder must be static on @FreeBuilder types");
  }

  @Test
  public void genericType() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}"));

    assertEquals("com.example.DataType.Builder<A, B>",
        builder.getMetadata().getBuilder().toString());
    assertThat(builder.getMetadata().isExtensible()).isTrue();
    assertEquals(Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR),
        builder.getMetadata().getBuilderFactory());
    assertEquals("com.example.DataType_Builder<A, B>",
        builder.getMetadata().getGeneratedBuilder().toString());
    assertEquals("com.example.DataType_Builder.Partial<A, B>",
        builder.getMetadata().getPartialType().toString());
    assertEquals("com.example.DataType_Builder.Property",
        builder.getMetadata().getPropertyEnum().toString());
    assertEquals("com.example.DataType<A, B>", builder.getMetadata().getType().toString());
    assertEquals("com.example.DataType_Builder.Value<A, B>",
        builder.getMetadata().getValueType().toString());
    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("B", properties.get("age").getType().toString());
    assertNull(properties.get("age").getBoxedType());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("A", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
  }

  @Test
  public void genericTypeWithBounds() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A extends CharSequence, B extends " + Temporal.class.getName()
            + "> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<Q, R> extends DataType_Builder<Q, R> {}",
        "}"));

    assertEquals("com.example.DataType.Builder<A, B>",
        builder.getMetadata().getBuilder().toString());
    assertEquals("DataType<A extends CharSequence, B extends Temporal>",
        SourceStringBuilder.simple().add(builder.getMetadata().getType().declaration()).toString());
  }

  @Test
  public void genericType_rebuilt() throws CannotGenerateCodeException {
    // See also https://github.com/inferred/FreeBuilder/issues/111
    model.newType(
        "package com.example;",
        "abstract class DataType_Builder<A, B> {}");
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType<A, B> {",
        "  public abstract A getName();",
        "  public abstract B getAge();",
        "  public static class Builder<A, B> extends DataType_Builder<A, B> {}",
        "}"));

    assertEquals("com.example.DataType.Builder<A, B>",
        builder.getMetadata().getBuilder().toString());
    assertThat(builder.getMetadata().isExtensible()).isTrue();
    assertEquals(Optional.of(BuilderFactory.NO_ARGS_CONSTRUCTOR),
        builder.getMetadata().getBuilderFactory());
    assertEquals("com.example.DataType_Builder<A, B>",
        builder.getMetadata().getGeneratedBuilder().toString());
    assertEquals("com.example.DataType_Builder.Partial<A, B>",
        builder.getMetadata().getPartialType().toString());
    assertEquals("com.example.DataType_Builder.Property",
        builder.getMetadata().getPropertyEnum().toString());
    assertEquals("com.example.DataType<A, B>", builder.getMetadata().getType().toString());
    assertEquals("com.example.DataType_Builder.Value<A, B>",
        builder.getMetadata().getValueType().toString());
    Map<String, Property> properties = uniqueIndex(builder.getMetadata().getProperties(), GET_NAME);
    assertThat(properties.keySet()).containsExactly("name", "age");
    assertEquals("B", properties.get("age").getType().toString());
    assertNull(properties.get("age").getBoxedType());
    assertEquals("AGE", properties.get("age").getAllCapsName());
    assertEquals("Age", properties.get("age").getCapitalizedName());
    assertEquals("getAge", properties.get("age").getGetterName());
    assertEquals("A", properties.get("name").getType().toString());
    assertNull(properties.get("name").getBoxedType());
    assertEquals("NAME", properties.get("name").getAllCapsName());
    assertEquals("Name", properties.get("name").getCapitalizedName());
    assertEquals("getName", properties.get("name").getGetterName());
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
        "@" + Generated.class.getCanonicalName() + "(\"FreeBuilder FTW!\")",
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
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType() { }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

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

    assertEquals(expectedMetadata, builder.getMetadata());
  }

  @Test
  public void multipleConstructors() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

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

    assertEquals(expectedMetadata, builder.getMetadata());
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
        "<init>", "@FreeBuilder types must have a package-visible no-args constructor");
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
        "DataType", "@FreeBuilder types must have a package-visible no-args constructor");
  }

  @Test
  public void freeEnumBuilder() {
    try {
      analyser.analyse(model.newType(
          "package com.example;",
          "public enum DataType {}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError("DataType", "@FreeBuilder does not support enum types");
  }

  @Test
  public void unnamedPackage() {
    try {
      analyser.analyse(model.newType("public class DataType {}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError("DataType", "@FreeBuilder does not support types in unnamed packages");
  }

  @Test
  public void freeAnnotationBuilder() {
    try {
      analyser.analyse(model.newType(
          "package com.example;",
          "public @interface DataType {}"));
      fail("Expected CannotGenerateCodeException");
    } catch (CannotGenerateCodeException expected) { }

    messager.verifyError("DataType", "@FreeBuilder does not support annotation types");
  }

  @Test
  public void isFullyCheckedCast_nonGenericType() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract String getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).hasSize(1);
    Property property = getOnlyElement(builder.getMetadata().getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_erasedType() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).hasSize(1);
    Property property = getOnlyElement(builder.getMetadata().getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_wildcardType() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<?> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).hasSize(1);
    Property property = getOnlyElement(builder.getMetadata().getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_genericType() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<String> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).hasSize(1);
    Property property = getOnlyElement(builder.getMetadata().getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_lowerBoundWildcard() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Number> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).hasSize(1);
    Property property = getOnlyElement(builder.getMetadata().getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isFalse();
  }

  @Test
  public void isFullyCheckedCast_objectLowerBoundWildcard() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract Iterable<? extends Object> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).hasSize(1);
    Property property = getOnlyElement(builder.getMetadata().getProperties());
    assertEquals("property", property.getName());
    assertThat(property.isFullyCheckedCast()).isTrue();
  }

  @Test
  public void isFullyCheckedCast_oneWildcard() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract java.util.Map<?, String> getProperty();",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getProperties()).hasSize(1);
    Property property = getOnlyElement(builder.getMetadata().getProperties());
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
  public void valueTypeNestedClassesAddedToVisibleList() throws CannotGenerateCodeException {
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "  public interface Objects {}",
        "}"));

    assertThat(builder.getMetadata().getVisibleNestedTypes()).containsExactly(
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
    CodeGenerator builder = (CodeGenerator) analyser.analyse(model.newType(
        "package com.example;",
        "public class DataType extends SuperType {",
        "  DataType(int i) { }",
        "  DataType() { }",
        "  DataType(String s) { }",
        "  public static class Builder extends DataType_Builder {}",
        "}"));

    assertThat(builder.getMetadata().getVisibleNestedTypes()).containsExactly(
        QualifiedName.of("com.example", "SuperType", "Objects"),
        QualifiedName.of("com.example", "DataType", "Builder"),
        QualifiedName.of("com.example", "DataType_Builder", "Partial"),
        QualifiedName.of("com.example", "DataType_Builder", "Property"),
        QualifiedName.of("com.example", "DataType_Builder", "Value"));
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
        + " {}\" to your class to enable the @FreeBuilder API";
  }

  private static String addBuilderToInterfaceMessage(String builder) {
    return "Add \"class Builder extends "
        + builder
        + " {}\" to your interface to enable the @FreeBuilder API";
  }

  private static String asSource(Excerpt annotation) {
    return SourceStringBuilder.simple().add(annotation).toString().trim();
  }

  private static final Function<Property, String> GET_NAME = new Function<Property, String>() {
    @Override
    public String apply(Property propery) {
      return propery.getName();
    }
  };
}
