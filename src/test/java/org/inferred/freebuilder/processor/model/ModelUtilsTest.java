package org.inferred.freebuilder.processor.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.reflect.TypeToken;

import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.ComparisonFailure;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

public class ModelUtilsTest {

  @Rule public final ModelRule model = new ModelRule();

  @Test
  public void primitiveTypeDoesNotNeedSafeVarargs() {
    TypeMirror intType = model.typeMirror(int.class);
    assertFalse(ModelUtils.needsSafeVarargs(intType));
  }

  @Test
  public void nonGenericTypeDoesNotNeedSafeVarargs() {
    TypeMirror numberType = model.typeMirror(Number.class);
    assertFalse(ModelUtils.needsSafeVarargs(numberType));
  }

  @Test
  public void rawTypeDoesNotNeedSafeVarargs() {
    TypeMirror rawType = model.typeMirror(List.class);
    assertFalse(ModelUtils.needsSafeVarargs(rawType));
  }

  @Test
  public void plainWildcardedListDoesNotNeedSafeVarargs() {
    TypeMirror wildcardList = model.typeMirror(new TypeToken<List<?>>() {});
    assertFalse(ModelUtils.needsSafeVarargs(wildcardList));
  }

  @Test
  public void plainWildcardedMapDoesNotNeedSafeVarargs() {
    TypeMirror wildcardMap = model.typeMirror(new TypeToken<Map<?, ?>>() {});
    assertFalse(ModelUtils.needsSafeVarargs(wildcardMap));
  }

  @Test
  public void parameterizedListNeedsSafeVarargs() {
    TypeMirror parameterizedList = model.typeMirror(new TypeToken<List<String>>() {});
    assertTrue(ModelUtils.needsSafeVarargs(parameterizedList));
  }

  @Test
  public void parameterizedMapNeedsSafeVarargs() {
    TypeMirror parameterizedMap = model.typeMirror(new TypeToken<Map<String, Double>>() {});
    assertTrue(ModelUtils.needsSafeVarargs(parameterizedMap));
  }

  @Test
  public void listWithLowerBoundedWildcardNeedsSafeVarargs() {
    TypeMirror listWithLowerBoundedWildcard =
        model.typeMirror(new TypeToken<List<? extends Number>>() {});
    assertTrue(ModelUtils.needsSafeVarargs(listWithLowerBoundedWildcard));
  }

  @Test
  public void listWithUpperBoundedWildcardNeedsSafeVarargs() {
    TypeMirror listWithUpperBoundedWildcard =
        model.typeMirror(new TypeToken<List<? super Number>>() {});
    assertTrue(ModelUtils.needsSafeVarargs(listWithUpperBoundedWildcard));
  }

  @Test
  public void typeArgumentNeedsSafeVarargs() {
    ExecutableElement method = (ExecutableElement) model.newElementWithMarker(
        "interface Foo<T> {",
        "  ---> public T method();",
        "}");
    TypeMirror typeArgument = method.getReturnType();
    assertTrue(ModelUtils.needsSafeVarargs(typeArgument));
  }

  @Test
  public void testUpperBound_knownType() {
    // T -> T
    TypeMirror result = ModelUtils.upperBound(model.elementUtils(), model.typeMirror("Number"));
    assertSameType(model.typeMirror("Number"), result);
  }

  @Test
  public void testUpperBound_unknownType() {
    // ? -> Object
    TypeMirror result = ModelUtils.upperBound(
        model.elementUtils(), model.typeUtils().getWildcardType(null, null));
    assertSameType(model.typeMirror("Object"), result);
  }

  @Test
  public void testUpperBound_extendsBound() {
    // ? extends T -> T
    TypeMirror result = ModelUtils.upperBound(
        model.elementUtils(), model.typeUtils().getWildcardType(model.typeMirror("Number"), null));
    assertSameType(model.typeMirror("Number"), result);
  }

  @Test
  public void testUpperBound_superBound() {
    // ? super T -> Object
    TypeMirror result = ModelUtils.upperBound(
        model.elementUtils(), model.typeUtils().getWildcardType(null, model.typeMirror("Number")));
    assertSameType(model.typeMirror("Object"), result);
  }

  @Test
  public void testErasesToAnyOf_nonGenericType() {
    assertTrue(ModelUtils.erasesToAnyOf((DeclaredType) model.typeMirror("Number"), Number.class));
  }

  @Test
  public void testErasesToAnyOf_subType() {
    assertFalse(ModelUtils.erasesToAnyOf((DeclaredType) model.typeMirror("Double"), Number.class));
  }

  @Test
  public void testErasesToAnyOf_superType() {
    assertFalse(ModelUtils.erasesToAnyOf((DeclaredType) model.typeMirror("Object"), Number.class));
  }

  @Test
  public void testErasesToAnyOf_boxedType() {
    assertTrue(ModelUtils.erasesToAnyOf((DeclaredType) model.typeMirror("Double"), Double.class));
  }

  @Test
  public void testErasesToAnyOf_primitiveType() {
    assertFalse(ModelUtils.erasesToAnyOf((DeclaredType) model.typeMirror("Double"), double.class));
  }

  @Test
  public void testErasesToAnyOf_erasedType() {
    assertTrue(ModelUtils.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.List"), List.class));
  }

  @Test
  public void testErasesToAnyOf_fullySpecifiedType() {
    assertTrue(ModelUtils.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.List<Double>"), List.class));
  }

  @Test
  public void testErasesToAnyOf_wildcardType() {
    WildcardType wildcard = model.typeUtils().getWildcardType(null, null);
    assertTrue(ModelUtils.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.List", wildcard), List.class));
  }

  @Test
  public void testErasesToAnyOf_firstOfTwo() {
    assertTrue(ModelUtils.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.List"), List.class, Set.class));
  }

  @Test
  public void testErasesToAnyOf_secondOfTwo() {
    assertTrue(ModelUtils.erasesToAnyOf(
        (DeclaredType) model.typeMirror("java.util.Set"), List.class, Set.class));
  }

  @Test
  public void testErasesToAnyOf_neitherOfTwo() {
    assertFalse(ModelUtils.erasesToAnyOf(
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
      throw new ComparisonFailure("", expectedString, actualString);
    }
  }

  private static String extendedToString(Object object) {
    if (object == null) {
      return null;
    }
    return object.getClass().getCanonicalName() + "<" + object + ">";
  }
}
