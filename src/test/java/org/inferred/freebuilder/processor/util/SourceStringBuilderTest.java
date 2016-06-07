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
package org.inferred.freebuilder.processor.util;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

@RunWith(MockitoJUnitRunner.class)
public class SourceStringBuilderTest {

  @Rule public final ModelRule model = new ModelRule();
  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final ImportManager shortener = new ImportManager.Builder().build();
  private final SourceBuilder builder = new SourceStringBuilder(shortener, new StaticFeatureSet());

  @Test
  public void testConstructor() {
    assertEquals("", builder.toString());
  }

  @Test
  public void testAddLine() {
    builder
        .addLine("public class Bar {")
        .addLine("  // %s %d", "Foo", 100)
        .addLine("}");
    assertEquals(
         "public class Bar {\n"
            + "  // Foo 100\n"
            + "}\n",
        builder.toString());
  }

  @Test
  public void testAddLine_typeInJavaLangPackage() {
    builder.addLine("// This should be short: %s", String.class);
    assertThat(builder.toString()).isEqualTo("// This should be short: String\n");
  }

  @Test
  public void testAddLine_primitiveType() {
    builder.addLine("// This should be short: %s", int.class);
    assertThat(builder.toString()).isEqualTo("// This should be short: int\n");
  }

  @Test
  public void testAddLine_typeInDifferentPackage() {
    builder.addLine("// This should be imported: %s", AtomicLong.class);
    assertThat(shortener.getClassImports())
        .containsExactly("java.util.concurrent.atomic.AtomicLong");
    assertThat(builder.toString()).isEqualTo("// This should be imported: AtomicLong\n");
  }

  @Test
  public void testAddLine_nestedTypeInDifferentPackage() {
    builder.addLine("// This should be imported: %s", ImmutableList.Builder.class);
    assertThat(shortener.getClassImports())
        .containsExactly("com.google.common.collect.ImmutableList");
    assertThat(builder.toString()).isEqualTo("// This should be imported: ImmutableList.Builder\n");
  }

  @Test
  public void testAddLine_typesWithSameName() {
    builder
        .addLine("// This should be imported: %s", java.util.List.class)
        .addLine("// This should be explicit: %s", java.awt.List.class);
    assertThat(shortener.getClassImports())
        .containsExactly("java.util.List");
    assertThat(builder.toString()).doesNotContain("import java.awt.List;\n");
    assertThat(builder.toString()).isEqualTo(
        "// This should be imported: List\n// This should be explicit: java.awt.List\n");
  }

  @Test
  public void testAddLine_typeMirrorInJavaLangPackage() {
    builder.addLine("// This should be short: %s", model.typeMirror(String.class));
    assertThat(builder.toString()).isEqualTo("// This should be short: String\n");
  }

  @Test
  public void testAddLine_typeMirrorInDifferentPackage() {
    builder.addLine("// This should be imported: %s", model.typeMirror(AtomicLong.class));
    assertThat(shortener.getClassImports())
        .containsExactly("java.util.concurrent.atomic.AtomicLong");
    assertThat(builder.toString()).isEqualTo("// This should be imported: AtomicLong\n");
  }

  @Test
  public void testAddLine_genericTypeMirror() {
    builder.addLine("// This should be imported: %s",
        model.typeMirror("java.util.List<java.lang.String>"));
    assertThat(shortener.getClassImports())
        .containsExactly("java.util.List");
    assertThat(builder.toString()).isEqualTo("// This should be imported: List<String>\n");
  }

  @Test
  public void testAddLine_typeElementInJavaLangPackage() {
    builder.addLine("// This should be short: %s",
        model.typeUtils().asElement(model.typeMirror(String.class)));
    assertThat(builder.toString()).isEqualTo("// This should be short: String\n");
  }

  @Test
  public void testAddLine_typeElementInDifferentPackage() {
    builder.addLine("// This should be imported: %s",
        model.typeUtils().asElement(model.typeMirror(AtomicLong.class)));
    assertThat(shortener.getClassImports())
        .containsExactly("java.util.concurrent.atomic.AtomicLong");
    assertThat(builder.toString()).isEqualTo("// This should be imported: AtomicLong\n");
  }

  @Test
  public void testAddLine_genericTypeElement() {
    builder.addLine("// This should be imported: %s",
        model.typeUtils().asElement(model.typeMirror("java.util.List<java.lang.String>")));
    assertThat(shortener.getClassImports())
        .containsExactly("java.util.List");
    // Turning a parameterized type mirror into an element loses the type parameters.
    assertThat(builder.toString()).isEqualTo("// This should be imported: List\n");
  }

  @Test
  public void testAddLine_errorTypeArgument() {
    TypeElement myType = model.newType(
        "package com.example; class MyType {",
        "  java.util.List<NoSuchType<Foo>> foo;",
        "}");
    DeclaredType errorType = (DeclaredType)
        getOnlyElement(fieldsIn(myType.getEnclosedElements())).asType();
    assertEquals(TypeKind.ERROR, errorType.getTypeArguments().get(0).getKind());
    // Note: myType.toString() returns "java.util.List<<any>>" on current compilers. Weird.
    thrown.expect(IllegalArgumentException.class);
    builder.addLine("%s", errorType);
  }

  @Test
  public void testAddLine_excerpt() {
    builder.addLine("%s = null;", Excerpts.add("%s %s", "Foo", "bar"));
    assertThat(builder.toString()).isEqualTo("Foo bar = null;\n");
  }

  @Test
  public void testAddLine_emptyAnnotation() {
    testAnnotation("@MyAnnotation", "@interface MyAnnotation { }");
  }

  @Test
  public void testAddLine_annotationWithStringValueParameter() {
    testAnnotation("@MyAnnotation(\"hi there!\\\"\\n\")",
        "@interface MyAnnotation { String value(); }");
  }

  @Test
  public void testAddLine_annotationWithIntegerValueParameter() {
    testAnnotation("@MyAnnotation(42)", "@interface MyAnnotation { int value(); }");
  }

  @Test
  public void testAddLine_annotationWithIntegerParameter() {
    testAnnotation("@MyAnnotation(p = 42)", "@interface MyAnnotation { int p(); }");
  }

  @Test
  public void testAddLine_annotationWithIntegerArrayParameter_noIntegers() {
    testAnnotation("@MyAnnotation(p = {})", "@interface MyAnnotation { int[] p(); }");
  }

  @Test
  public void testAddLine_annotationWithIntegerArrayParameter_oneInteger() {
    testAnnotation("@MyAnnotation(p = 10)", "@interface MyAnnotation { int[] p(); }");
  }

  @Test
  public void testAddLine_annotationWithIntegerArrayParameter_twoIntegers() {
    testAnnotation("@MyAnnotation(p = {10, 66})", "@interface MyAnnotation { int[] p(); }");
  }

  @Test
  public void testAddLine_nestedAnnotations() {
    testAnnotation("@OuterAnnotation(@InnerAnnotation(2))",
        "@interface InnerAnnotation { int value(); }",
        "@interface OuterAnnotation { InnerAnnotation value(); }");
  }

  private void testAnnotation(String annotationUsage, String... annotationDefinitions) {
    for (String annotationDefinition : annotationDefinitions) {
      model.newType("package com.example; " + annotationDefinition);
    }
    TypeElement annotatedType = model.newType(
        "package com.example; " + annotationUsage + " class MyType { }");
    AnnotationMirror annotation = getOnlyAnnotation(annotatedType);
    builder.addLine("%s", annotation);
    assertThat(builder.toString()).isEqualTo(annotationUsage + "\n");
  }

  private static AnnotationMirror getOnlyAnnotation(Element element) {
    // Ignore @Target, as it's added by the model framework
    List<AnnotationMirror> annotations = new ArrayList<AnnotationMirror>();
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (!annotation.getAnnotationType().asElement().getSimpleName().contentEquals("Target")) {
        annotations.add(annotation);
      }
    }
    return getOnlyElement(annotations);
  }
}
