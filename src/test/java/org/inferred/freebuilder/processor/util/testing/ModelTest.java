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
package org.inferred.freebuilder.processor.util.testing;

import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.reflect.TypeToken;

/** Tests for {@link Model}. */
@RunWith(JUnit4.class)
public class ModelTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private Model model;

  @Before
  public void setup() {
    model = Model.create();
  }

  @Test
  public void typeUtils() {
    PrimitiveType booleanType = model.typeUtils().getPrimitiveType(TypeKind.BOOLEAN);
    assertEquals("boolean", booleanType.toString());
  }

  @Test
  public void elementUtils() {
    String oneHundredAndOne = model.elementUtils().getConstantExpression(101L);
    assertEquals("101L", oneHundredAndOne);
  }

  @Test
  public void environment() {
    assertSame(model.elementUtils(), model.environment().getElementUtils());
  }

  @Test
  public void newElementWithMarker() {
    TypeElement type = (TypeElement) model.newElementWithMarker(
        "package foo.bar;",
        "public class MyType {",
        "  ---> public class MyInnerType {",
        "    public void doNothing() { }",
        "  }",
        "}");
    assertEquals(ElementKind.CLASS, type.getKind());
    assertEquals(NestingKind.MEMBER, type.getNestingKind());
    assertEquals("MyInnerType", type.getSimpleName().toString());
    assertEquals("foo.bar.MyType.MyInnerType", type.toString());
    assertEquals("doNothing",
        getOnlyElement(methodsIn(type.getEnclosedElements())).getSimpleName().toString());
  }

  @Test
  public void newElementWithMarker_noElementIdentified() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Code must identify the element to be returned using '--->'");
    model.newElementWithMarker(
        "package foo.bar;",
        "public class MyType {",
        "  public class MyInnerType {",
        "    public void doNothing() { }",
        "  }",
        "}");
  }

  @Test
  public void newElementAnnotatedWith() {
    TypeElement type = (TypeElement) model.newElementAnnotatedWith(
        Deprecated.class,
        "package foo.bar;",
        "public class MyType {",
        "  @Deprecated public class MyInnerType {",
        "    public void doNothing() { }",
        "  }",
        "}");
    assertEquals(ElementKind.CLASS, type.getKind());
    assertEquals(NestingKind.MEMBER, type.getNestingKind());
    assertEquals("MyInnerType", type.getSimpleName().toString());
    assertEquals("foo.bar.MyType.MyInnerType", type.toString());
    assertEquals("doNothing",
        getOnlyElement(methodsIn(type.getEnclosedElements())).getSimpleName().toString());
  }

  @Test
  public void newElementAnnotatedWith_noElementIdentified() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("no annotated element");
    model.newElementAnnotatedWith(
        Deprecated.class,
        "package foo.bar;",
        "public class MyType {",
        "  public class MyInnerType {",
        "    public void doNothing() { }",
        "  }",
        "}");
  }

  @Test
  public void newElementAnnotatedWith_twoElementsIdentified() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Multiple elements annotated with @java.lang.Deprecated found");
    model.newElementAnnotatedWith(
        Deprecated.class,
        "package foo.bar;",
        "public class MyType {",
        "  @Deprecated public class MyInnerType {",
        "    @Deprecated public void doNothing() { }",
        "  }",
        "}");
  }

  @Test
  public void newElementAnnotatedWith_compilationError() {
    thrown.expect(CompilationException.class);
    model.newElementAnnotatedWith(
        Deprecated.class,
        "package foo.bar;",
        "public class MyType {",
        "  public class MyInnerType {",
        "    public void doNothing() {",
        "  }",
        "}");
  }

  @Test
  public void newType_class() {
    TypeElement type = model.newType(
        "package foo.bar;",
        "public class MyType {",
        "  public void doNothing() { }",
        "}");
    assertEquals(ElementKind.CLASS, type.getKind());
    assertEquals(NestingKind.TOP_LEVEL, type.getNestingKind());
    assertEquals("MyType", type.getSimpleName().toString());
    assertEquals("foo.bar.MyType", type.toString());
    assertEquals("doNothing",
        getOnlyElement(methodsIn(type.getEnclosedElements())).getSimpleName().toString());
  }

  @Test
  public void newType_interface() {
    TypeElement type = model.newType(
        "package foo.bar;",
        "public interface MyType {",
        "  public void doNothing();",
        "}");
    assertEquals(ElementKind.INTERFACE, type.getKind());
    assertEquals(NestingKind.TOP_LEVEL, type.getNestingKind());
    assertEquals("MyType", type.getSimpleName().toString());
    assertEquals("foo.bar.MyType", type.toString());
    assertEquals("doNothing",
        getOnlyElement(methodsIn(type.getEnclosedElements())).getSimpleName().toString());
  }

  @Test
  public void newType_enum() {
    TypeElement type = model.newType(
        "package foo.bar;",
        "public enum MyType { A, B; }");
    assertEquals(ElementKind.ENUM, type.getKind());
    assertEquals(NestingKind.TOP_LEVEL, type.getNestingKind());
    assertEquals("MyType", type.getSimpleName().toString());
    assertEquals("foo.bar.MyType", type.toString());
  }

  @Test
  public void newType_annotation() {
    TypeElement type = model.newType(
        "package foo.bar;",
        "public @interface MyType {",
        "  String param();",
        "}");
    assertEquals(ElementKind.ANNOTATION_TYPE, type.getKind());
    assertEquals(NestingKind.TOP_LEVEL, type.getNestingKind());
    assertEquals("MyType", type.getSimpleName().toString());
    assertEquals("foo.bar.MyType", type.toString());
    assertEquals("param",
        getOnlyElement(methodsIn(type.getEnclosedElements())).getSimpleName().toString());
  }

  @Test
  public void typeMirror_string_nonGenericType() {
    TypeMirror t1 = model.typeMirror("String");
    assertEquals("java.lang.String", t1.toString());
    TypeMirror t2 = model.typeMirror("String");
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_string_genericType() {
    TypeMirror t1 = model.typeMirror("java.util.List<Integer>");
    assertEquals("java.util.List<java.lang.Integer>", t1.toString());
    TypeMirror t2 = model.typeMirror("java.util.List<Integer>");
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_string_arrayOfGenericsType() {
    TypeMirror t1 = model.typeMirror("java.util.List<String>[]");
    assertEquals("java.util.List<java.lang.String>[]", t1.toString());
    TypeMirror t2 = model.typeMirror("java.util.List<String>[]");
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_string_genericOfArraysType() {
    TypeMirror t1 = model.typeMirror("java.util.List<String[]>");
    assertEquals("java.util.List<java.lang.String[]>", t1.toString());
    TypeMirror t2 = model.typeMirror("java.util.List<String[]>");
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_string_nestedGenericType() {
    TypeMirror t1 = model.typeMirror("java.util.Map<String,java.util.List<Integer>>");
    assertEquals(
        "java.util.Map<java.lang.String,java.util.List<java.lang.Integer>>", t1.toString());
    TypeMirror t2 = model.typeMirror("java.util.Map<String,java.util.List<Integer>>");
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_string_argumentSubstitution() {
    TypeMirror t1 = model.typeMirror("java.util.Map<Integer, String>");
    assertEquals("java.util.Map<java.lang.Integer,java.lang.String>", t1.toString());
    TypeMirror t2 = model.typeMirror(
        "java.util.Map<%1, %2>", model.typeMirror("Integer"), model.typeMirror("String"));
    assertEquals("java.util.Map<java.lang.Integer,java.lang.String>", t2.toString());
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_string_rawTypeSubstitution() {
    TypeMirror t = model.typeMirror(
        "%1<%2>", model.typeMirror("java.util.List"), model.typeMirror("Integer"));
    assertEquals("java.util.List<java.lang.Integer>", t.toString());
  }

  @Test
  public void typeMirror_string_rawTypeSubstitutionError() {
    TypeMirror stringList = model.typeMirror("java.util.List<String>");
    TypeMirror integer = model.typeMirror("Integer");
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(
        "Expected raw type, got 'java.util.List<java.lang.String>'");
    model.typeMirror("%1<%2>", stringList, integer);
  }

  @Test
  public void typeMirror_string_wrongNumberOfGenericParameters() {
    TypeMirror stringList = model.typeMirror("java.util.List<String>");
    TypeMirror integer = model.typeMirror("Integer");
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Incorrect number of arguments for java.util.List (expected 1, got 2)");
    model.typeMirror("java.util.List<%1, %2>", stringList, integer);
  }

  @Test
  public void typeMirror_string_illegalSyntax() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid type string");
    model.typeMirror("java.util.List<String><String>");
  }

  @Test
  public void typeMirror_string_illegalSyntax2() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid type string");
    model.typeMirror("java.util.List, java.util.List");
  }

  @Test
  public void typeMirror_string_illegalSyntax3() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid type string");
    model.typeMirror("java.util.List[]<String>");
  }

  @Test
  public void typeMirror_string_illegalSyntax4() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Invalid type string");
    model.typeMirror("java.util.List[][]");
  }

  @Test
  public void typeMirror_class_void() {
    TypeMirror t1 = model.typeMirror(void.class);
    assertEquals("void", t1.toString());
    TypeMirror t2 = model.typeMirror(void.class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_class_primitiveType() {
    TypeMirror t1 = model.typeMirror(int.class);
    assertEquals("int", t1.toString());
    TypeMirror t2 = model.typeMirror(int.class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_class_primitiveArray() {
    TypeMirror t1 = model.typeMirror(int[].class);
    assertEquals("int[]", t1.toString());
    TypeMirror t2 = model.typeMirror(int[].class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_class_nonGenericType() {
    TypeMirror t1 = model.typeMirror(String.class);
    assertEquals("java.lang.String", t1.toString());
    TypeMirror t2 = model.typeMirror(String.class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_class_nonGenericArray() {
    TypeMirror t1 = model.typeMirror(String[].class);
    assertEquals("java.lang.String[]", t1.toString());
    TypeMirror t2 = model.typeMirror(String[].class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_class_compilationBetweenInstances() {
    TypeMirror t1 = model.typeMirror("java.lang.String");
    TypeMirror t2 = model.typeMirror(String.class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_class_genericType() {
    TypeMirror t1 = model.typeMirror(List.class);
    assertEquals("java.util.List", t1.toString());
    TypeMirror t2 = model.typeMirror(List.class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_class_genericArray() {
    TypeMirror t1 = model.typeMirror(List[].class);
    assertEquals("java.util.List[]", t1.toString());
    TypeMirror t2 = model.typeMirror(List[].class);
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_typeToken_nonGenericType() {
    TypeMirror t1 = model.typeMirror(new TypeToken<String>() {});
    assertEquals("java.lang.String", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<String>() {});
    assertEquals(t1, t2);
  }

  @Test
  public void typeMirror_typeToken_genericType() {
    TypeMirror t1 = model.typeMirror(new TypeToken<List<Integer>>() {});
    assertEquals("java.util.List<java.lang.Integer>", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<List<Integer>>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @SuppressWarnings("rawtypes")
  @Test
  public void typeMirror_typeToken_rawType() {
    TypeMirror t1 = model.typeMirror(new TypeToken<List>() {});
    assertEquals("java.util.List", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<List>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_typeToken_wildcard() {
    TypeMirror t1 = model.typeMirror(new TypeToken<List<?>>() {});
    assertEquals("java.util.List<?>", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<List<?>>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_typeToken_wildcardBoundedAboveType() {
    TypeMirror t1 = model.typeMirror(new TypeToken<List<? super Number>>() {});
    assertEquals("java.util.List<? super java.lang.Number>", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<List<? super Number>>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_typeToken_wildcardBoundedBelow() {
    TypeMirror t1 = model.typeMirror(new TypeToken<List<? extends Number>>() {});
    assertEquals("java.util.List<? extends java.lang.Number>", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<List<? extends Number>>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_typeToken_arrayOfGenericsType() {
    TypeMirror t1 = model.typeMirror(new TypeToken<List<String>[]>() {});
    assertEquals("java.util.List<java.lang.String>[]", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<List<String>[]>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_typeToken_genericOfArraysType() {
    TypeMirror t1 = model.typeMirror(new TypeToken<List<String[]>>() {});
    assertEquals("java.util.List<java.lang.String[]>", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<List<String[]>>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirror_typeToken_nestedGenericType() {
    TypeMirror t1 = model.typeMirror(new TypeToken<Map<String, List<Integer>>>() {});
    assertEquals(
        "java.util.Map<java.lang.String,java.util.List<java.lang.Integer>>", t1.toString());
    TypeMirror t2 = model.typeMirror(new TypeToken<Map<String, List<Integer>>>() {});
    assertTrue("Same type", model.typeUtils().isSameType(t1, t2));
  }

  @Test
  public void typeMirrorEqualsMethodReturnType() {
    ExecutableElement method = (ExecutableElement) model.newElementWithMarker(
        "package example.com;",
        "interface Foo {",
        "  ---> String method();",
        "}");
    TypeMirror stringType = model.typeMirror("String");
    assertTrue("Same type", model.typeUtils().isSameType(stringType, method.getReturnType()));
  }

  @After
  public void teardown() {
    model.destroy();
  }
}
