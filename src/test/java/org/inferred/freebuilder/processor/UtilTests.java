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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Set;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UtilTests {

  @Rule public final ModelRule model = new ModelRule();

  @Test
  public void testUpperBound_knownType() {
    // T -> T
    TypeMirror result = Util.upperBound(model.elementUtils(), model.typeMirror("Number"));
    assertSameType(model.typeMirror("Number"), result);
  }

  @Test
  public void testUpperBound_unknownType() {
    // ? -> Object
    TypeMirror result = Util.upperBound(
        model.elementUtils(), model.typeUtils().getWildcardType(null, null));
    assertSameType(model.typeMirror("Object"), result);
  }

  @Test
  public void testUpperBound_extendsBound() {
    // ? extends T -> T
    TypeMirror result = Util.upperBound(
        model.elementUtils(), model.typeUtils().getWildcardType(model.typeMirror("Number"), null));
    assertSameType(model.typeMirror("Number"), result);
  }

  @Test
  public void testUpperBound_superBound() {
    // ? super T -> Object
    TypeMirror result = Util.upperBound(
        model.elementUtils(), model.typeUtils().getWildcardType(null, model.typeMirror("Number")));
    assertSameType(model.typeMirror("Object"), result);
  }

  @Test
  public void testErasesToAnyOf_nonGenericType() {
    assertTrue(Util.erasesToAnyOf((DeclaredType) model.typeMirror("Number"), Number.class));
  }

  @Test
  public void testErasesToAnyOf_subType() {
    assertFalse(Util.erasesToAnyOf((DeclaredType) model.typeMirror("Double"), Number.class));
  }

  @Test
  public void testErasesToAnyOf_superType() {
    assertFalse(Util.erasesToAnyOf((DeclaredType) model.typeMirror("Object"), Number.class));
  }

  @Test
  public void testErasesToAnyOf_boxedType() {
    assertTrue(Util.erasesToAnyOf((DeclaredType) model.typeMirror("Double"), Double.class));
  }

  @Test
  public void testErasesToAnyOf_primitiveType() {
    assertFalse(Util.erasesToAnyOf((DeclaredType) model.typeMirror("Double"), double.class));
  }

  @Test
  public void testErasesToAnyOf_erasedType() {
    assertTrue(Util.erasesToAnyOf((DeclaredType) model.typeMirror("java.util.List"), List.class));
  }

  @Test
  public void testErasesToAnyOf_fullySpecifiedType() {
    assertTrue(Util.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.List<Double>"), List.class));
  }

  @Test
  public void testErasesToAnyOf_wildcardType() {
    WildcardType wildcard = model.typeUtils().getWildcardType(null, null);
    assertTrue(Util.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.List", wildcard), List.class));
  }

  @Test
  public void testErasesToAnyOf_firstOfTwo() {
    assertTrue(Util.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.List"), List.class, Set.class));
  }

  @Test
  public void testErasesToAnyOf_secondOfTwo() {
    assertTrue(Util.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.Set"), List.class, Set.class));
  }

  @Test
  public void testErasesToAnyOf_neitherOfTwo() {
    assertFalse(Util.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.Collection"), List.class, Set.class));
  }

  private void assertSameType(TypeMirror expected, TypeMirror actual) {
    if (!model.typeUtils().isSameType(expected, actual)) {
      String expectedString = (expected == null) ? "null" : expected.toString();
      String actualString = (actual == null) ? "null" : actual.toString();
      if (expectedString.equals(actualString)) {
        expectedString = extendedToString(expected);
        actualString = extendedToString(actual);
      }
      throw new ComparisonFailure(
          "",
          expectedString,
          actualString);
    }
  }

  private static String extendedToString(Object object) {
    if (object == null) {
      return null;
    }
    return object.getClass().getCanonicalName() + "<" + object + ">";
  }
}
