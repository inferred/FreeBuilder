/*
 * Copyright 2015 Google Inc. All rights reserved.
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

import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.junit.Assert.*;

import org.inferred.freebuilder.processor.util.feature.SourceLevel;
import org.junit.Test;

public class ParameterizedTypeTest {

  private static final QualifiedName MY_TYPE_NAME = QualifiedName.of("com.example", "MyType");
  private static final ClassTypeImpl MY_TYPE = newTopLevelClass("com.example.MyType");
  private static final ClassTypeImpl MY_NESTED_TYPE =
      ClassTypeImpl.newNestedClass(MY_TYPE.asElement(), "MyNestedType");

  @Test
  public void testFromDeclaredType_simpleType() {
    ParameterizedType type = ParameterizedType.from(MY_TYPE);
    assertEquals(MY_TYPE_NAME, type.getQualifiedName());
    assertFalse(type.isParameterized());
    assertEquals("MyType", prettyPrint(type, SourceLevel.JAVA_7));
    assertEquals("new MyType", prettyPrint(type.constructor(), SourceLevel.JAVA_6));
    assertEquals("new MyType", prettyPrint(type.constructor(), SourceLevel.JAVA_7));
    assertEquals("MyType", prettyPrint(type.declaration(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType}", prettyPrint(type.javadocLink(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_7));
  }

  @Test
  public void testFromDeclaredType_nestedType() {
    ParameterizedType type = ParameterizedType.from(MY_NESTED_TYPE);
    assertEquals(QualifiedName.of("com.example", "MyType", "MyNestedType"),
        type.getQualifiedName());
    assertFalse(type.isParameterized());
    assertEquals("MyType.MyNestedType", prettyPrint(type, SourceLevel.JAVA_7));
    assertEquals("new MyType.MyNestedType", prettyPrint(type.constructor(), SourceLevel.JAVA_6));
    assertEquals("new MyType.MyNestedType", prettyPrint(type.constructor(), SourceLevel.JAVA_7));
    assertEquals("MyNestedType", prettyPrint(type.declaration(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType.MyNestedType}",
        prettyPrint(type.javadocLink(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType.MyNestedType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_7));
  }

  @Test
  public void testFromDeclaredType_genericType() {
    GenericElement myType = new GenericElement.Builder(MY_TYPE_NAME).addTypeParameter("V").build();
    ParameterizedType type = ParameterizedType.from(myType);
    assertEquals(MY_TYPE_NAME, type.getQualifiedName());
    assertTrue(type.isParameterized());
    assertEquals("MyType<V>", prettyPrint(type, SourceLevel.JAVA_7));
    assertEquals("new MyType<V>", prettyPrint(type.constructor(), SourceLevel.JAVA_6));
    assertEquals("new MyType<>", prettyPrint(type.constructor(), SourceLevel.JAVA_7));
    assertEquals("MyType<V>", prettyPrint(type.declaration(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType}", prettyPrint(type.javadocLink(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_7));
  }

  @Test
  public void testFromDeclaredType_parameterWithSingleBound() {
    GenericElement myType = new GenericElement.Builder(MY_TYPE_NAME)
        .addTypeParameter("V", newTopLevelClass("java.lang.Number"))
        .build();
    ParameterizedType type = ParameterizedType.from(myType);
    assertEquals(MY_TYPE_NAME, type.getQualifiedName());
    assertTrue(type.isParameterized());
    assertEquals("MyType<V>", prettyPrint(type, SourceLevel.JAVA_7));
    assertEquals("new MyType<V>", prettyPrint(type.constructor(), SourceLevel.JAVA_6));
    assertEquals("new MyType<>", prettyPrint(type.constructor(), SourceLevel.JAVA_7));
    assertEquals("MyType<V extends Number>", prettyPrint(type.declaration(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType}", prettyPrint(type.javadocLink(), SourceLevel.JAVA_7));
    assertEquals("{@link MyType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_7));
  }

  @Test
  public void testFromDeclaredType_parameterWithMultipleBounds() {
    GenericElement myType = new GenericElement.Builder(MY_TYPE_NAME)
        .addTypeParameter("V",
            newTopLevelClass("java.lang.Number"),
            newTopLevelClass("java.lang.Comparable"),
            newTopLevelClass("java.util.Formattable"))
        .build();
    ParameterizedType type = ParameterizedType.from(myType);
    assertEquals("MyType<V extends Number & Comparable & Formattable>",
        prettyPrint(type.declaration(), SourceLevel.JAVA_7));
  }

  private static String prettyPrint(Excerpt type, SourceLevel sourceLevel) {
    return SourceStringBuilder.simple(sourceLevel).add(type).toString();
  }

}
