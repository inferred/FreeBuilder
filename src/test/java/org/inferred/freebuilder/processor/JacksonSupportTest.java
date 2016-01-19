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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.SourceLevel;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.inferred.freebuilder.processor.util.testing.FakeMessager;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

import javax.lang.model.element.TypeElement;

/** Unit tests for {@link Analyser}. */
@RunWith(JUnit4.class)
public class JacksonSupportTest {

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
  public void noAnnotationAddedIfJsonDeserializeMissing() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  int getFooBar();",
        "  class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertThat(property.getAccessorAnnotations()).named("property accessor annotations").isEmpty();
  }

  @Test
  public void jacksonAnnotationAddedWithExplicitName() throws CannotGenerateCodeException {
    // See also https://github.com/google/FreeBuilder/issues/68
    TypeElement dataType = model.newType(
        "package com.example;",
        "import " + JsonProperty.class.getName() + ";",
        "@" + JsonDeserialize.class.getName() + "(builder = DataType.Builder.class)",
        "public interface DataType {",
        "  @JsonProperty(\"bob\") int getFooBar();",
        "  class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertPropertyHasJsonPropertyAnnotation(property, "bob");
  }

  @Test
  public void jacksonAnnotationAddedWithImplicitName() throws CannotGenerateCodeException {
    // See also https://github.com/google/FreeBuilder/issues/90
    TypeElement dataType = model.newType(
        "package com.example;",
        "@" + JsonDeserialize.class.getName() + "(builder = DataType.Builder.class)",
        "public interface DataType {",
        "  int getFooBar();",
        "  class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertPropertyHasJsonPropertyAnnotation(property, "fooBar");
  }

  @Test
  public void jsonAnyGetterAnnotationDisablesImplicitProperty() throws CannotGenerateCodeException {
    TypeElement dataType = model.newType(
        "package com.example;",
        "@" + JsonDeserialize.class.getName() + "(builder = DataType.Builder.class)",
        "public interface DataType {",
        "  @" + JsonAnyGetter.class.getName(),
        "  " + Map.class.getName() + "<Integer, String> getFooBar();",
        "  class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertThat(property.getAccessorAnnotations()).named("property accessor annotations").isEmpty();
  }

  private static void assertPropertyHasJsonPropertyAnnotation(
      Property property, String propertyName) {
    assertThat(property.getAccessorAnnotations()).named("property accessor annotations").hasSize(1);
    Excerpt accessorAnnotation = getOnlyElement(property.getAccessorAnnotations());
    assertThat(asString(accessorAnnotation)).isEqualTo("@JsonProperty(\"" + propertyName + "\")\n");
  }

  private static String asString(Excerpt excerpt) {
    SourceBuilder code = SourceStringBuilder.simple(SourceLevel.JAVA_6, true);
    code.add(excerpt);
    return code.toString();
  }
}
