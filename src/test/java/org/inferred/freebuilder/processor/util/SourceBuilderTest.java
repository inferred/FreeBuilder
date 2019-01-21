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

import static org.junit.Assert.assertEquals;

import static javax.lang.model.util.ElementFilter.fieldsIn;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;

/** Tests for {@link SourceBuilder}. */
public class SourceBuilderTest {

  @Rule public final ModelRule model = new ModelRule();
  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testEmptyUnit() {
    String code = source().toString();
    assertEquals("\n", code);
  }

  @Test
  public void testAddLine() {
    SourceBuilder unit = source()
        .addLine("package com.example;")
        .addLine("public class Bar {")
        .addLine("  // %s %s", "Foo", 100)
        .addLine("}");
    assertEquals(
        "package com.example;\n\n"
            + "public class Bar {\n"
            + "  // Foo 100\n"
            + "}\n",
        unit.toString());
    assertEquals(QualifiedName.of("com.example", "Bar"), unit.typename());
  }

  @Test
  public void testAddLine_typeInSamePackage() {
    TypeElement element = model.newType("package com.example; public class Foo { }");
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be short: %s", element)
        .toString();
    assertThat(code).contains("// This should be short: Foo\n");
  }

  @Test
  public void testAddLine_typeInJavaLangPackage() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be short: %s", String.class)
        .toString();
    assertThat(code).contains("// This should be short: String\n");
  }

  @Test
  public void testAddLine_primitiveType() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be short: %s", int.class)
        .toString();
    assertThat(code).contains("// This should be short: int\n");
  }

  @Test
  public void testAddLine_typeInDifferentPackage() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be imported: %s", AtomicLong.class)
        .toString();
    assertThat(code).isEqualTo(""
        + "package com.example;\n\n"
        + "import java.util.concurrent.atomic.AtomicLong;\n\n"
        + "// This should be imported: AtomicLong\n");
  }

  @Test
  public void testAddLine_typeRepeated() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be imported: %s", AtomicLong.class)
        .addLine("// This should reuse the import: %s", AtomicLong.class)
        .toString();
    assertThat(code).isEqualTo(""
        + "package com.example;\n\n"
        + "import java.util.concurrent.atomic.AtomicLong;\n\n"
        + "// This should be imported: AtomicLong\n"
        + "// This should reuse the import: AtomicLong\n");
  }

  @Test
  public void testAddLine_trivialNestedTypeInDifferentPackage() {
    // "Builder" is too trivial a name to import
    String code = source()
        .addLine("package com.example;")
        .addLine("// Top-level should be imported: %s", ImmutableList.Builder.class)
        .toString();
    assertThat(code).contains("import com.google.common.collect.ImmutableList;\n");
    assertThat(code)
        .contains("// Top-level should be imported: ImmutableList.Builder\n");
  }

  @Test
  public void testAddLine_sensibleNestedTypeInDifferentPackage() {
    // "SimpleEntry" is a decent name to import
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be imported: %s", AbstractMap.SimpleEntry.class)
        .toString();
    assertThat(code).contains("import java.util.AbstractMap.SimpleEntry;\n");
    assertThat(code).contains("// This should be imported: SimpleEntry\n");
  }

  @Test
  public void testAddLine_typesWithSameName() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be explicit: %s", java.util.List.class)
        .addLine("// This should be explicit: %s", java.awt.List.class)
        .toString();
    assertThat(code).doesNotContain("import java.util.List;\n");
    assertThat(code).doesNotContain("import java.awt.List;\n");
    assertThat(code).contains("// This should be explicit: java.util.List\n");
    assertThat(code).contains("// This should be explicit: java.awt.List\n");
  }

  @Test
  public void testAddLine_typeMirrorInJavaLangPackage() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be short: %s", model.typeMirror(String.class))
        .toString();
    assertThat(code).contains("// This should be short: String\n");
  }

  @Test
  public void testAddLine_typeMirrorInDifferentPackage() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be imported: %s", model.typeMirror(AtomicLong.class))
        .toString();
    assertThat(code).contains("import java.util.concurrent.atomic.AtomicLong;");
    assertThat(code).contains("// This should be imported: AtomicLong\n");
  }

  @Test
  public void testAddLine_genericTypeMirror() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be imported: %s",
            model.typeMirror("java.util.List<java.lang.String>"))
        .toString();
    assertThat(code).contains("import java.util.List;\n");
    assertThat(code).contains("// This should be imported: List<String>\n");
  }

  @Test
  public void testAddLine_typeElementInJavaLangPackage() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be short: %s",
            model.typeUtils().asElement(model.typeMirror(String.class)))
        .toString();
    assertThat(code).contains("// This should be short: String\n");
  }

  @Test
  public void testAddLine_typeElementInDifferentPackage() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be imported: %s",
            model.typeUtils().asElement(model.typeMirror(AtomicLong.class)))
        .toString();
    assertThat(code).contains("import java.util.concurrent.atomic.AtomicLong;\n");
    assertThat(code).contains("// This should be imported: AtomicLong\n");
  }

  @Test
  public void testAddLine_genericTypeElement() {
    String code = source()
        .addLine("package com.example;")
        .addLine("// This should be imported: %s",
            model.typeUtils().asElement(model.typeMirror("java.util.List<java.lang.String>")))
        .toString();
    assertThat(code).contains("import java.util.List;\n");
    // Turning a parameterized type mirror into an element loses the type parameters.
    assertThat(code).contains("// This should be imported: List\n");
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
    source()
        .addLine("package com.example;").addLine("%s", errorType);
  }

  private SourceBuilder source() {
    return SourceBuilder.forEnvironment(model.environment(), null);
  }
}
