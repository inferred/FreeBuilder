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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Answers.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.PropertyCodeGenerator.Config;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;

/** Unit tests for {@link Analyser}. */
@RunWith(MockitoJUnitRunner.class)
public class NullablePropertyFactoryTest {

  @Rule public final ModelRule model = new ModelRule();
  @Mock(answer = RETURNS_SMART_NULLS) private Config config;
  private final NullableProperty.Factory factory = new NullableProperty.Factory();
  @Mock(answer = RETURNS_SMART_NULLS) private Metadata metadata;

  @Before
  public void setUp() {
    when(config.getMetadata()).thenReturn(metadata);
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

    assertThat(codeGenerator).isAbsent();
  }

  @Test
  public void nullable() {
    ExecutableElement getterMethod = (ExecutableElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> public abstract @" + Nullable.class.getName() + " String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .buildPartial();
    when(config.getProperty()).thenReturn(property);
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator).hasValue(new NullableProperty(
        metadata, property, ImmutableSet.of(model.typeElement(Nullable.class))));
  }

  @Test
  public void arbitraryNullableAnnotation() {
    model.newType(
        "package foo.bar;",
        "public @interface Nullable {}");
    ExecutableElement getterMethod = (ExecutableElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  ---> public abstract @foo.bar.Nullable String getName();",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .buildPartial();
    when(config.getProperty()).thenReturn(property);
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator).hasValue(new NullableProperty(
        metadata, property, ImmutableSet.of(model.typeElement("foo.bar.Nullable"))));
  }

  @Test
  public void multipleNullableAnnotations() {
    model.newType(
        "package foo.bar;",
        "public @interface Nullable {}");
    ExecutableElement getterMethod = (ExecutableElement) model.newElementWithMarker(
        "package com.example;",
        "public class DataType {",
        "  @" + Nullable.class.getName(),
        "  @foo.bar.Nullable",
        "  ---> public abstract String getName();",
        "",
        "  public static class Builder extends DataType_Builder {}",
        "}");
    Property property = new Property.Builder()
        .setType(getterMethod.getReturnType())
        .buildPartial();
    when(config.getProperty()).thenReturn(property);
    doReturn(getterMethod.getAnnotationMirrors()).when(config).getAnnotations();

    Optional<NullableProperty> codeGenerator = factory.create(config);

    assertThat(codeGenerator).hasValue(new NullableProperty(
        metadata, property, ImmutableSet.of(
            model.typeElement(Nullable.class), model.typeElement("foo.bar.Nullable"))));
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

    assertThat(codeGenerator).isAbsent();
  }
}
