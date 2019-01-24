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
package org.inferred.freebuilder.processor.property;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import static org.inferred.freebuilder.processor.util.FunctionalType.unaryOperator;
import static org.mockito.Answers.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Datatype;
import org.inferred.freebuilder.processor.property.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/** Unit tests for {@link NullableProperty.Factory}. */
@RunWith(MockitoJUnitRunner.class)
public class NullablePropertyFactoryTest {

  @Rule public final ModelRule model = new ModelRule();
  @Mock(answer = RETURNS_SMART_NULLS) private Config config;
  private final NullableProperty.Factory factory = new NullableProperty.Factory();
  @Mock(answer = RETURNS_SMART_NULLS) private Datatype datatype;

  @Before
  public void setUp() {
    when(config.getDatatype()).thenReturn(datatype);
  }

  @Test
  public void notNullable() {
    ExecutableElement getterMethod = (ExecutableElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> public abstract String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .buildPartial();
    when(config.getProperty()).thenReturn(property);
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator).isEqualTo(Optional.empty());
  }

  @Test
  public void nullable() {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract @" + Nullable.class.getName() + " String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    DeclaredType builderType = (DeclaredType)
        getOnlyElement(typesIn(dataType.getEnclosedElements())).asType();
    ExecutableElement getterMethod = getOnlyElement(methodsIn(dataType.getEnclosedElements()));
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .setCapitalizedName("Name")
        .buildPartial();
    when(config.getBuilder()).thenReturn(builderType);
    when(config.getProperty()).thenReturn(property);
    when(config.getElements()).thenReturn(model.elementUtils());
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator.get()).isEqualTo(new NullableProperty(
        datatype,
        property,
        ImmutableSet.of(model.typeElement(Nullable.class)),
        unaryOperator(model.typeMirror(String.class))));
  }

  @Test
  public void arbitraryNullableAnnotation() {
    model.newType(
        "package foo.bar;",
        "public @interface Nullable {}");
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  public abstract @foo.bar.Nullable String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    DeclaredType builderType = (DeclaredType)
        getOnlyElement(typesIn(dataType.getEnclosedElements())).asType();
    ExecutableElement getterMethod = getOnlyElement(methodsIn(dataType.getEnclosedElements()));
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .setCapitalizedName("Name")
        .buildPartial();
    when(config.getBuilder()).thenReturn(builderType);
    when(config.getProperty()).thenReturn(property);
    when(config.getElements()).thenReturn(model.elementUtils());
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator.get()).isEqualTo(new NullableProperty(
        datatype,
        property,
        ImmutableSet.of(model.typeElement("foo.bar.Nullable")),
        unaryOperator(model.typeMirror(String.class))));
  }

  @Test
  public void multipleNullableAnnotations() {
    model.newType(
        "package foo.bar;",
        "public @interface Nullable {}");
    TypeElement dataType = model.newType(
        "package com.example;",
        "public class DataType {",
        "  @" + Nullable.class.getName(),
        "  @foo.bar.Nullable",
        "  public abstract String getName();",
        "",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    DeclaredType builderType = (DeclaredType)
        getOnlyElement(typesIn(dataType.getEnclosedElements())).asType();
    ExecutableElement getterMethod = getOnlyElement(methodsIn(dataType.getEnclosedElements()));
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .setCapitalizedName("Name")
        .buildPartial();
    when(config.getBuilder()).thenReturn(builderType);
    when(config.getProperty()).thenReturn(property);
    when(config.getElements()).thenReturn(model.elementUtils());
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator.get()).isEqualTo(new NullableProperty(
        datatype,
        property,
        ImmutableSet.of(
            model.typeElement(Nullable.class),
            model.typeElement("foo.bar.Nullable")),
        unaryOperator(model.typeMirror(String.class))));
  }

  @Test
  public void nullablePrimitive() {
    ExecutableElement getterMethod = (ExecutableElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> public abstract @" + Nullable.class.getName() + " int getAge();",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .buildPartial();
    when(config.getProperty()).thenReturn(property);
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator).isEqualTo(Optional.empty());
  }
}
