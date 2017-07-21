package org.inferred.freebuilder.processor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.reflect.TypeToken;

import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

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
}
