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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.inferred.freebuilder.processor.util.testing.FakeMessager;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.element.TypeElement;
import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

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
    assertPropertyHasAnnotation(property, JsonProperty.class, "@JsonProperty(\"bob\")");
  }

  @Test
  public void jacksonXmlPropertyAnnotationAddedWithExplicitName() throws CannotGenerateCodeException {
    // See also https://github.com/google/FreeBuilder/issues/68
    TypeElement dataType = model.newType(
        "package com.example;",
         "import " + JacksonXmlProperty.class.getName() + ";",
        "@" + JsonDeserialize.class.getName() + "(builder = DataType.Builder.class)",
        "public interface DataType {",
        "  @JacksonXmlProperty(localName=\"b-ob\") int getFooBar();",
        "  class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertPropertyHasAnnotation(property,
            JacksonXmlProperty.class, "@JacksonXmlProperty(localName = \"b-ob\")");
  }

  @Test
  public void jacksonXmlTextAnnotationAddedWithExplicitName() throws CannotGenerateCodeException {
    // See also https://github.com/google/FreeBuilder/issues/68
    TypeElement dataType = model.newType(
            "package com.example;",
            "import " + JacksonXmlText.class.getName() + ";",
            "@" + JsonDeserialize.class.getName() + "(builder = DataType.Builder.class)",
            "public interface DataType {",
            "  @JacksonXmlText(value = false) int getFooBar();",
            "  class Builder extends DataType_Builder {}",
            "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertPropertyHasAnnotation(property, JacksonXmlText.class, "@JacksonXmlText(false)");
  }

  @Test
  public void jacksonXmlElementWrapperAnnotationAddedWithExplicitName() throws CannotGenerateCodeException {
    // See also https://github.com/google/FreeBuilder/issues/68
    TypeElement dataType = model.newType(
            "package com.example;",
            "import " + JacksonXmlElementWrapper.class.getName() + ";",
            "@" + JsonDeserialize.class.getName() + "(builder = DataType.Builder.class)",
            "public interface DataType {",
            "  @JacksonXmlElementWrapper(namespace=\"b-ob\", localName=\"john\", useWrapping=false) int getFooBar();",
            "  class Builder extends DataType_Builder {}",
            "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertPropertyHasAnnotation(property,
            JacksonXmlElementWrapper.class,
            "@JacksonXmlElementWrapper(namespace = \"b-ob\", localName = \"john\", useWrapping = false)");
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
    assertPropertyHasAnnotation(property, JsonProperty.class, "@JsonProperty(\"fooBar\")");
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

  private void assertPropertyHasAnnotation(Property property, Class annotationClass,
                                           String annotationString) {
    Optional<Excerpt> annotationExcerpt = property.getAccessorAnnotations()
            .stream()
            .filter(excerpt -> excerpt.toString().contains(annotationClass.getCanonicalName()))
            .findFirst();
    assertThat(annotationExcerpt).named("property accessor annotations").isNotNull();
    assertThat(asString(annotationExcerpt.get()))
            .isEqualTo(String.format("%s%n", annotationString));
  }

  private static String asString(Excerpt excerpt) {
    return SourceStringBuilder.simple().add(excerpt).toString();
  }
}
