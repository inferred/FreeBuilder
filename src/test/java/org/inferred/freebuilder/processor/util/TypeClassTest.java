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

import static org.inferred.freebuilder.processor.model.ClassTypeImpl.newNestedClass;
import static org.inferred.freebuilder.processor.model.ClassTypeImpl.newTopLevelClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.inferred.freebuilder.processor.model.GenericElement;
import org.inferred.freebuilder.processor.model.ClassTypeImpl.ClassElementImpl;
import org.inferred.freebuilder.processor.util.feature.SourceLevel;
import org.junit.Test;

public class TypeClassTest {

  private static final QualifiedName MY_TYPE_NAME = QualifiedName.of("com.example", "MyType");
  private static final ClassElementImpl MY_TYPE =
      newTopLevelClass("com.example.MyType").asElement();
  private static final ClassElementImpl MY_NESTED_TYPE =
      newNestedClass(MY_TYPE, "MyNestedType").asElement();

  @Test
  public void testFromTypeElement_simpleType() {
    TypeClass type = TypeClass.from(MY_TYPE);
    assertEquals(MY_TYPE_NAME, type.getQualifiedName());
    assertFalse(type.isParameterized());
    assertEquals("MyType", prettyPrint(type, SourceLevel.JAVA_8));
    assertEquals("new MyType", prettyPrint(type.constructor(), SourceLevel.JAVA_8));
    assertEquals("new MyType", prettyPrint(type.constructor(), SourceLevel.JAVA_8));
    assertEquals("MyType", prettyPrint(type.declaration(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType}", prettyPrint(type.javadocLink(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_8));
  }

  @Test
  public void testFromTypeElement_nestedType() {
    TypeClass type = TypeClass.from(MY_NESTED_TYPE);
    assertEquals(QualifiedName.of("com.example", "MyType", "MyNestedType"),
        type.getQualifiedName());
    assertFalse(type.isParameterized());
    assertEquals("MyType.MyNestedType", prettyPrint(type, SourceLevel.JAVA_8));
    assertEquals("new MyType.MyNestedType", prettyPrint(type.constructor(), SourceLevel.JAVA_8));
    assertEquals("new MyType.MyNestedType", prettyPrint(type.constructor(), SourceLevel.JAVA_8));
    assertEquals("MyNestedType", prettyPrint(type.declaration(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType.MyNestedType}",
        prettyPrint(type.javadocLink(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType.MyNestedType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_8));
  }

  @Test
  public void testFromTypeElement_genericType() {
    GenericElement myType = new GenericElement.Builder(MY_TYPE_NAME).addTypeParameter("V").build();
    TypeClass type = TypeClass.from(myType);
    assertEquals(MY_TYPE_NAME, type.getQualifiedName());
    assertTrue(type.isParameterized());
    assertEquals("MyType<V>", prettyPrint(type, SourceLevel.JAVA_8));
    assertEquals("new MyType<>", prettyPrint(type.constructor(), SourceLevel.JAVA_8));
    assertEquals("MyType<V>", prettyPrint(type.declaration(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType}", prettyPrint(type.javadocLink(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_8));
  }

  @Test
  public void testFromTypeElement_parameterWithSingleBound() {
    GenericElement myType = new GenericElement.Builder(MY_TYPE_NAME)
        .addTypeParameter("V", newTopLevelClass("java.lang.Number"))
        .build();
    TypeClass type = TypeClass.from(myType);
    assertEquals(MY_TYPE_NAME, type.getQualifiedName());
    assertTrue(type.isParameterized());
    assertEquals("MyType<V>", prettyPrint(type, SourceLevel.JAVA_8));
    assertEquals("new MyType<>", prettyPrint(type.constructor(), SourceLevel.JAVA_8));
    assertEquals("MyType<V extends Number>", prettyPrint(type.declaration(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType}", prettyPrint(type.javadocLink(), SourceLevel.JAVA_8));
    assertEquals("{@link MyType#foo()}",
        prettyPrint(type.javadocNoArgMethodLink("foo"), SourceLevel.JAVA_8));
  }

  @Test
  public void testFromTypeElement_parameterWithMultipleBounds() {
    GenericElement myType = new GenericElement.Builder(MY_TYPE_NAME)
        .addTypeParameter("V",
            newTopLevelClass("java.lang.Number"),
            newTopLevelClass("java.lang.Comparable"),
            newTopLevelClass("java.util.Formattable"))
        .build();
    TypeClass type = TypeClass.from(myType);
    assertEquals("MyType<V extends Number & Comparable & Formattable>",
        prettyPrint(type.declaration(), SourceLevel.JAVA_8));
  }

  private static String prettyPrint(Excerpt type, SourceLevel sourceLevel) {
    return SourceBuilder.forTesting(sourceLevel).add(type).toString();
  }
}
