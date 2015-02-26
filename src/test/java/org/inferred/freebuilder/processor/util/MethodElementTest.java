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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Expect;

import org.inferred.freebuilder.processor.util.MethodElement;
import org.inferred.freebuilder.processor.util.MethodElement.MethodSourceBuilder;
import org.inferred.freebuilder.processor.util.MethodElement.ParameterElement;
import org.inferred.freebuilder.processor.util.TypeShortener.NeverShorten;
import org.inferred.freebuilder.processor.util.testing.Model;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;

public class MethodElementTest {

  @Rule public final Expect expect = Expect.create();
  @Rule public final ExpectedException thrown = ExpectedException.none();

  private static final Name METHOD_NAME = new NameImpl("Bill");
  private static final Name PARAM_NAME_1 = new NameImpl("Ted");
  private static final Name PARAM_NAME_2 = new NameImpl("Bogus");
  private static final Name PARAM_NAME_3 = new NameImpl("Journey");
  private static final PrimitiveMirror CHAR = new PrimitiveMirror(TypeKind.CHAR);
  private static final PrimitiveMirror INT = new PrimitiveMirror(TypeKind.INT);
  private static final PrimitiveMirror FLOAT = new PrimitiveMirror(TypeKind.FLOAT);
  private static final PrimitiveMirror DOUBLE = new PrimitiveMirror(TypeKind.DOUBLE);

  @SuppressWarnings("deprecation")
  @Test
  public void testEmptyMethodElement() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME);
    expect.that(method.getAnnotationMirrors()).isEmpty();
    expect.that(method.getDefaultValue()).isNull();
    expect.that(method.getEnclosedElements()).isEmpty();
    expect.that(method.getKind()).isEqualTo(ElementKind.METHOD);
    expect.that(method.getModifiers()).isEmpty();
    expect.that(method.getParameters()).isEmpty();
    expect.that(method.getReturnType()).isEqualTo(FLOAT);
    expect.that(method.getSimpleName()).isEqualTo(METHOD_NAME);
    expect.that(method.getThrownTypes()).isEmpty();
    expect.that(method.getTypeParameters()).isEmpty();
    expect.that(method.asType().getKind()).isEqualTo(TypeKind.EXECUTABLE);
    expect.that(method.asType().getParameterTypes()).isEmpty();
    expect.that(method.asType().getReturnType()).isEqualTo(FLOAT);
    expect.that(method.asType().getThrownTypes()).isEmpty();
    expect.that(method.asType().getTypeVariables()).isEmpty();
  }

  @Test
  public void testWriteDefinitionTo_emptyMethod() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME);
    StringBuilder builder = new StringBuilder();
    try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
      methodWriter.addLine("return 0.3;");
    }
    String source = builder.toString();
    assertEquals(
        "  float Bill() {\n"
            + "    return 0.3;\n"
            + "  }\n",
        source);
  }

  @Test
  public void testWriteDefinitionTo_abstractEmptyMethod() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .setModifiers(EnumSet.of(Modifier.ABSTRACT));
    StringBuilder builder = new StringBuilder();
    method.startWritingTo(newSourceBuilder(builder)).close();
    assertEquals("  abstract float Bill();\n", builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_abstractEmptyMethod_throws() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .setModifiers(EnumSet.of(Modifier.ABSTRACT));
    try (MethodSourceBuilder methodWriter =
        method.startWritingTo(newSourceBuilder(new StringBuilder()))) {
      thrown.expect(IllegalStateException.class);
      methodWriter.addLine("anything");
    }
  }

  @Test
  public void testWriteDefinitionTo_oneParameter() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .addParameter(new ParameterElement(FLOAT, PARAM_NAME_1));
    StringBuilder builder = new StringBuilder();
    try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
      methodWriter.addLine("return 0.3;");
    }
    assertEquals(
        "  float Bill(\n"
            + "      float Ted) {\n"
            + "    return 0.3;\n"
            + "  }\n",
        builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_oneParameter_abstract() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .setModifiers(EnumSet.of(Modifier.ABSTRACT))
        .addParameter(new ParameterElement(FLOAT, PARAM_NAME_1));
    StringBuilder builder = new StringBuilder();
    method.startWritingTo(newSourceBuilder(builder)).close();
    assertEquals(
        "  abstract float Bill(\n"
            + "      float Ted);\n",
        builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_oneFinalParameter() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .setModifiers(EnumSet.of(Modifier.ABSTRACT))
        .addParameter(new ParameterElement(FLOAT, PARAM_NAME_1).setFinal());
    StringBuilder builder = new StringBuilder();
    method.startWritingTo(newSourceBuilder(builder)).close();
    assertEquals(
        "  abstract float Bill(\n"
            + "      final float Ted);\n",
            builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_twoParameters() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .addParameter(new ParameterElement(INT, PARAM_NAME_1))
        .addParameter(new ParameterElement(DOUBLE, PARAM_NAME_2));
    StringBuilder builder = new StringBuilder();
    try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
      methodWriter.addLine("return 0.3;");
    }
    assertEquals(
        "  float Bill(\n"
            + "      int Ted,\n"
            + "      double Bogus) {\n"
            + "    return 0.3;\n"
            + "  }\n",
        builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_threeParameters() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .addParameter(new ParameterElement(INT, PARAM_NAME_1))
        .addParameter(new ParameterElement(DOUBLE, PARAM_NAME_2))
        .addParameter(new ParameterElement(CHAR, PARAM_NAME_3));
    StringBuilder builder = new StringBuilder();
    try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
      methodWriter.addLine("return 0.3;");
    }
    assertEquals(
        "  float Bill(\n"
            + "      int Ted,\n"
            + "      double Bogus,\n"
            + "      char Journey) {\n"
            + "    return 0.3;\n  }\n",
        builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_oneThrownType() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .addThrownType(newTopLevelClass("org.example.Foo"));
    StringBuilder builder = new StringBuilder();
    try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
      methodWriter.addLine("return 0.3;");
    }
    assertEquals(
        "  float Bill()\n"
            + "      throws org.example.Foo {\n"
            + "    return 0.3;\n"
            + "  }\n",
        builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_oneThrownType_abstract() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .setModifiers(EnumSet.of(Modifier.ABSTRACT))
        .addThrownType(newTopLevelClass("org.example.Foo"));
    StringBuilder builder = new StringBuilder();
    method.startWritingTo(newSourceBuilder(builder)).close();
    assertEquals("  abstract float Bill()\n"
            + "      throws org.example.Foo;\n", builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_twoThrownTypes() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .addThrownType(newTopLevelClass("org.example.Foo"))
        .addThrownType(newTopLevelClass("org.example.Bar"));
    StringBuilder builder = new StringBuilder();
    try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
      methodWriter.addLine("return 0.3;");
    }
    assertEquals(
        "  float Bill()\n"
            + "      throws org.example.Foo,\n"
            + "          org.example.Bar {\n"
            + "    return 0.3;\n"
            + "  }\n",
        builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_threeThrownTypes() {
    MethodElement method = new MethodElement(FLOAT, METHOD_NAME)
        .addThrownType(newTopLevelClass("org.example.Foo"))
        .addThrownType(newTopLevelClass("org.example.Bar"))
        .addThrownType(newTopLevelClass("org.example.Baz"));
    StringBuilder builder = new StringBuilder();
    try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
      methodWriter.addLine("return 0.3;");
    }
    assertEquals(
        "  float Bill()\n"
            + "      throws org.example.Foo,\n"
            + "          org.example.Bar,\n"
            + "          org.example.Baz {\n"
            + "    return 0.3;\n"
            + "  }\n",
        builder.toString());
  }

  @Test
  public void testWriteDefinitionTo_realTypes() {
    Model model = Model.create();
    try {
      ExecutableElement realMethod = getOnlyElement(methodsIn(model.newType(
          "package org.example;",
          "public class Foo {",
          "  public @interface Annotation { }",
          "  public @interface Annotation2 {",
          "    String name();",
          "  }",
          "  @Annotation @Annotation2(name=\"bar\")",
          "  public final " + List.class.getCanonicalName() + "<String> doSomething(",
          "      @Annotation String parameterA,",
          "      @Annotation2(name=\"baz\") long parameterB,",
          "      " + Set.class.getCanonicalName() + "<Integer> parameterC)",
          "          throws " + IOException.class.getCanonicalName() + ",",
          "          " + ExecutionException.class.getCanonicalName() + ",",
          "          " + InterruptedException.class.getCanonicalName() + " {",
          "    throw new " + UnsupportedOperationException.class.getCanonicalName() + "();",
          "  }",
          "}").getEnclosedElements()));

      MethodElement method =
          new MethodElement(realMethod.getReturnType(), realMethod.getSimpleName());
      for (AnnotationMirror annotation : realMethod.getAnnotationMirrors()) {
        method.addAnnotationMirror(annotation);
      }
      for (VariableElement realParameter : realMethod.getParameters()) {
        ParameterElement parameter =
            new ParameterElement(realParameter.asType(), realParameter.getSimpleName());
        for (AnnotationMirror annotation : realParameter.getAnnotationMirrors()) {
          parameter.addAnnotationMirror(annotation);
        }
        method.addParameter(parameter);
      }
      for (TypeMirror type : realMethod.getThrownTypes()) {
        method.addThrownType(type);
      }
      method.setModifiers(realMethod.getModifiers());
      StringBuilder builder = new StringBuilder();
      try (MethodSourceBuilder methodWriter = method.startWritingTo(newSourceBuilder(builder))) {
        methodWriter.addLine("return %s.of(\"cheese\", \"cake\");", ImmutableList.class);
      }

      assertEquals(
          "  @org.example.Foo.Annotation\n"
              + "  @org.example.Foo.Annotation2(name=\"bar\")\n"
              + "  public final java.util.List<java.lang.String> doSomething(\n"
              + "      @org.example.Foo.Annotation java.lang.String parameterA,\n"
              + "      @org.example.Foo.Annotation2(name=\"baz\") long parameterB,\n"
              + "      java.util.Set<java.lang.Integer> parameterC)\n"
              + "          throws java.io.IOException,\n"
              + "              java.util.concurrent.ExecutionException,\n"
              + "              java.lang.InterruptedException {\n"
              + "    return com.google.common.collect.ImmutableList.of(\"cheese\", \"cake\");\n"
              + "  }\n",
          builder.toString());
    } finally {
      model.destroy();
    }
  }

  private static class PrimitiveMirror implements PrimitiveType {

    private final TypeKind kind;

    PrimitiveMirror(TypeKind kind) {
      checkState(kind.isPrimitive());
      this.kind = kind;
    }

    @Override
    public TypeKind getKind() {
      return kind;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
      return v.visitPrimitive(this, p);
    }

    @Override
    public String toString() {
      return kind.toString().toLowerCase();
    }

    @Override
    public int hashCode() {
      return kind.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      return (o instanceof PrimitiveMirror) && kind.equals(((PrimitiveMirror) o).kind);
    }
  }

  private static SourceBuilder newSourceBuilder(StringBuilder builder) {
    return new SourceStringBuilder(new NeverShorten(), builder);
  }
}
