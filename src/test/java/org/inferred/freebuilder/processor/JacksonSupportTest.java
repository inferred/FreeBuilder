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

import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.testing.FakeMessager;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.element.AnnotationMirror;
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
  public void jacksonAnnotationAddedToAccessorAnnotations() throws CannotGenerateCodeException {
     TypeElement dataType = model.newType(
        "package com.example;",
        "public interface DataType {",
        "  @" + JsonProperty.class.getName() + "(\"foobar\")",
        "  int getFooBar();",
        "  class Builder extends DataType_Builder {}",
        "}");

    Metadata metadata = analyser.analyse(dataType);

    Property property = getOnlyElement(metadata.getProperties());
    assertThat(property.getAccessorAnnotations()).hasSize(1);
    AnnotationMirror accessorAnnotation = getOnlyElement(property.getAccessorAnnotations());
    assertThat(asElement(accessorAnnotation.getAnnotationType()).getSimpleName().toString())
        .isEqualTo("JsonProperty");
  }
}
