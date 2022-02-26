package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.NamePicker.pickName;
import static org.junit.Assert.assertEquals;

import com.google.common.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.inferred.freebuilder.processor.Datatype.Visibility;
import org.inferred.freebuilder.processor.source.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NamePickerTest {

  @Rule public final ModelRule model = new ModelRule();

  private DeclaredType exampleType;

  @Before
  public void createExampleType() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "public class DataType extends ThingThatWillBeGenerated {",
            "  public int methodReturningInt();",
            "  protected int protectedMethod();",
            "  int packageProtectedMethod();",
            "  private int privateMethod();",
            "  private int otherPrivateMethod();",
            "  private int _otherPrivateMethodImpl();",
            "  public java.util.List<Integer> methodReturningIntegerList();",
            "  public int methodTakingFloat(float a);",
            "  protected int protectedMethodTakingFloat(float a);",
            "  int packageProtectedMethodTakingFloat(float a);",
            "  private int privateMethodTakingFloat(float a);",
            "  public int methodTakingIntegerList(java.util.List<Integer> a);",
            "}");
    exampleType = (DeclaredType) type.asType();
  }

  private NameAndVisibility pickNameForExampleType(
      Class<?> returnType, String preferredName, Class<?>... parameterTypes) {
    TypeMirror[] parameterTypeMirrors =
        Arrays.asList(parameterTypes).stream()
            .map(model::typeMirror)
            .collect(toArray(TypeMirror[]::new));
    return pickName(
        exampleType,
        model.elementUtils(),
        model.typeUtils(),
        model.typeMirror(returnType),
        preferredName,
        parameterTypeMirrors);
  }

  private NameAndVisibility pickNameForExampleType(
      TypeToken<?> returnType, String preferredName, TypeToken<?>... parameterTypes) {
    TypeMirror[] parameterTypeMirrors =
        Arrays.asList(parameterTypes).stream()
            .map(model::typeMirror)
            .collect(toArray(TypeMirror[]::new));
    return pickName(
        exampleType,
        model.elementUtils(),
        model.typeUtils(),
        model.typeMirror(returnType),
        preferredName,
        parameterTypeMirrors);
  }

  @Test
  public void unusedMethodName() {
    NameAndVisibility result = pickNameForExampleType(int.class, "unusedMethod");
    assertEquals("unusedMethod", result.name());
    assertEquals(Visibility.PUBLIC, result.visibility());
  }

  @Test
  public void methodOverridePublic() {
    NameAndVisibility result = pickNameForExampleType(int.class, "methodReturningInt");
    assertEquals("methodReturningInt", result.name());
    assertEquals(Visibility.PUBLIC, result.visibility());
  }

  @Test
  public void methodOverrideProtected() {
    NameAndVisibility result = pickNameForExampleType(int.class, "protectedMethod");
    assertEquals("protectedMethod", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void methodOverridePackageProtected() {
    NameAndVisibility result = pickNameForExampleType(int.class, "packageProtectedMethod");
    assertEquals("packageProtectedMethod", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void methodOverridePrivate() {
    NameAndVisibility result = pickNameForExampleType(int.class, "privateMethod");
    assertEquals("_privateMethodImpl", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void methodOverridePrivate_secondChoiceAlsoPrivate() {
    NameAndVisibility result = pickNameForExampleType(int.class, "otherPrivateMethod");
    assertEquals("_otherPrivateMethodImpl2", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void incompatibleReturnType() {
    NameAndVisibility result = pickNameForExampleType(Integer.class, "methodReturningInt");
    assertEquals("_methodReturningIntImpl", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void incompatibleGenericReturnType() {
    NameAndVisibility result =
        pickNameForExampleType(new TypeToken<List<String>>() {}, "methodReturningIntegerList");
    assertEquals("_methodReturningIntegerListImpl", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void unusedMethodNameWithParameter() {
    NameAndVisibility result = pickNameForExampleType(int.class, "unusedMethod", int.class);
    assertEquals("unusedMethod", result.name());
    assertEquals(Visibility.PUBLIC, result.visibility());
  }

  @Test
  public void methodNameUsedForDifferentNumberOfArguments() {
    NameAndVisibility result = pickNameForExampleType(int.class, "protectedMethod", int.class);
    assertEquals("protectedMethod", result.name());
    assertEquals(Visibility.PUBLIC, result.visibility());
  }

  @Test
  public void singleParameterMethodOverridePublic() {
    NameAndVisibility result = pickNameForExampleType(int.class, "methodTakingFloat", float.class);
    assertEquals("methodTakingFloat", result.name());
    assertEquals(Visibility.PUBLIC, result.visibility());
  }

  @Test
  public void singleParameterMethodOverrideProtected() {
    NameAndVisibility result =
        pickNameForExampleType(int.class, "protectedMethodTakingFloat", float.class);
    assertEquals("protectedMethodTakingFloat", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void singleParameterMethodOverridePackageProtected() {
    NameAndVisibility result =
        pickNameForExampleType(int.class, "packageProtectedMethodTakingFloat", float.class);
    assertEquals("packageProtectedMethodTakingFloat", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void singleParameterMethodOverridePrivate() {
    NameAndVisibility result =
        pickNameForExampleType(int.class, "privateMethodTakingFloat", float.class);
    assertEquals("_privateMethodTakingFloatImpl", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void singleParameterMethodOverload() {
    NameAndVisibility result = pickNameForExampleType(int.class, "methodTakingFloat", int.class);
    assertEquals("methodTakingFloat", result.name());
    assertEquals(Visibility.PUBLIC, result.visibility());
  }

  @Test
  public void singleParameterIncompatibleReturnType() {
    NameAndVisibility result =
        pickNameForExampleType(float.class, "methodTakingFloat", float.class);
    assertEquals("_methodTakingFloatImpl", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  @Test
  public void singleParameterGenericMethodOverload() {
    NameAndVisibility result =
        pickNameForExampleType(
            TypeToken.of(int.class), "methodTakingIntegerList", new TypeToken<List<Integer>>() {});
    assertEquals("methodTakingIntegerList", result.name());
    assertEquals(Visibility.PUBLIC, result.visibility());
  }

  @Test
  public void incompatibleParameter() {
    NameAndVisibility result =
        pickNameForExampleType(
            TypeToken.of(int.class), "methodTakingIntegerList", new TypeToken<List<Float>>() {});
    assertEquals("_methodTakingIntegerListImpl", result.name());
    assertEquals(Visibility.PACKAGE, result.visibility());
  }

  private static <T> Collector<T, ?, T[]> toArray(Function<Integer, T[]> arrayConstructor) {
    return Collector.of(
        (Supplier<List<T>>) ArrayList::new,
        List::add,
        (left, right) -> {
          left.addAll(right);
          return left;
        },
        list -> list.toArray(arrayConstructor.apply(list.size())));
  }
}
