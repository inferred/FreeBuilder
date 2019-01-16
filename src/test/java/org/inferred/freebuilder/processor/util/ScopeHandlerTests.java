package org.inferred.freebuilder.processor.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.util.ScopeHandler.ScopeState;
import org.inferred.freebuilder.processor.util.ScopeHandler.Visibility;
import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ScopeHandlerTests {

  @Rule public ModelRule model = new ModelRule();
  private ScopeHandler handler;

  @Before
  public void setUp() {
    handler = new ScopeHandler(model.elementUtils());
  }

  @Test
  public void typeCanBeImportedIntoAnotherPackage() {
    ScopeState result = handler.visibilityIn("com.example", QualifiedName.of(Set.class));
    assertThat(result).isEqualTo(ScopeState.IMPORTABLE);
  }

  @Test
  public void typeInThisPackageIsInScope() {
    ScopeState result = handler.visibilityIn("java.util", QualifiedName.of(Set.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void typeInJavaLangPackageIsInScope() {
    ScopeState result = handler.visibilityIn("java.util", QualifiedName.of(Integer.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void generatedTypeInThisPackageIsInScope() {
    QualifiedName myType = QualifiedName.of("com.example", "MyType");
    handler.declareGeneratedType(Visibility.PACKAGE, myType, ImmutableSet.of());
    ScopeState result = handler.visibilityIn("com.example", myType);
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void nestedTypeInThisPackageIsImportable() {
    ScopeState result = handler.visibilityIn("java.util", QualifiedName.of(Map.Entry.class));
    assertThat(result).isEqualTo(ScopeState.IMPORTABLE);
  }

  @Test
  public void typeInThisPackageHidesTypesInOtherPackages() {
    ScopeState result = handler.visibilityIn("java.util", QualifiedName.of("com.example", "Set"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void typeCanBeImportedIntoAnotherScope() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "FooBar"), QualifiedName.of(Set.class));
    assertThat(result).isEqualTo(ScopeState.IMPORTABLE);
  }

  @Test
  public void typeInSamePackageAsScopeIsInScope() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(Map.class), QualifiedName.of(Set.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void generatedTypeInSamePackageAsScopeIsInScope() {
    QualifiedName myType = QualifiedName.of("com.example", "MyType");
    handler.declareGeneratedType(Visibility.PACKAGE, myType, ImmutableSet.of());
    ScopeState result = handler.visibilityIn(QualifiedName.of("com.example", "FooBar"), myType);
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void nestedTypeInSamePackageAsScopeIsImportable() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(Set.class), QualifiedName.of(Map.Entry.class));
    assertThat(result).isEqualTo(ScopeState.IMPORTABLE);
  }

  @Test
  public void typeInSamePackageAsScopeHidesTypesInOtherPackages() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(Map.class), QualifiedName.of("com.example", "Set"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void typeInScopeIsInScope() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(Map.class), QualifiedName.of(Map.Entry.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void typeInOuterScopeIsInScope() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(Map.Entry.class),
        QualifiedName.of(Map.Entry.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  // HashMap extends AbstractMap, pulling in SimpleImmutableEntry, and implements Map, pulling in
  // Entry.

  @Test
  public void typeCanBeImportedIntoScopeWithSupertypes() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashMap.class), QualifiedName.of("com.example", "FooBar"));
    assertThat(result).isEqualTo(ScopeState.IMPORTABLE);
  }

  @Test
  public void typeInSamePackageAsScopeWithSupertypesIsInScope() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashMap.class), QualifiedName.of(Set.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void nestedTypeInSamePackageAsScopeWithSupertypesIsImportable() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashSet.class), QualifiedName.of(Map.Entry.class));
    assertThat(result).isEqualTo(ScopeState.IMPORTABLE);
  }

  @Test
  public void typeInSuperclassIsImportable() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashMap.class), QualifiedName.of(AbstractMap.SimpleImmutableEntry.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void typeInInterfaceIsImportable() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashMap.class), QualifiedName.of(Map.Entry.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void typeInSuperclassHidesTypesInOtherPackages() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashMap.class), QualifiedName.of("com.example", "SimpleImmutableEntry"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void typeInInterfaceHidesTypesInOtherPackages() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashMap.class), QualifiedName.of("com.example", "Entry"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void typeInScopeWithSupertypesIsInScope() {
    ScopeState result = handler.visibilityIn(
        QualifiedName.of(HashMap.class), QualifiedName.of(Map.Entry.class));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void promisedNestedTypeHidesTypeInSameNamespace() {
    QualifiedName outerType = QualifiedName.of("com.example", "A");
    QualifiedName scopeType = outerType.nestedType("B");
    QualifiedName nestedType = outerType.nestedType("Set");
    QualifiedName hiddenType = QualifiedName.of(Set.class);
    handler.predeclareGeneratedType(outerType);
    handler.predeclareGeneratedType(scopeType);
    handler.predeclareGeneratedType(nestedType);

    ScopeState result = handler.visibilityIn(scopeType, hiddenType);
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void promisedNestedTypeInScopeOfSubtype() {
    QualifiedName outerType = QualifiedName.of("com.example", "A");
    QualifiedName nestedType = outerType.nestedType("Set");
    QualifiedName scopeType = QualifiedName.of("com.example", "B");
    handler.predeclareGeneratedType(outerType);
    handler.predeclareGeneratedType(nestedType);
    handler.declareGeneratedType(Visibility.PACKAGE, scopeType, ImmutableSet.of(outerType));

    ScopeState result = handler.visibilityIn(scopeType, nestedType);
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void typeInSuperclassHidesTypeInEnclosingClass() {
    model.newType(
        "package com.example;",
        "class A {",
        "  abstract class B extends java.util.AbstractMap<Object, Object> {}",
        "  interface Entry {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "A", "Entry"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void typeInInterfaceHidesTypeInEnclosingClass() {
    model.newType(
        "package com.example;",
        "class A {",
        "  abstract class B implements java.util.Map<Object, Object> {}",
        "  interface Entry {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "A", "Entry"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void typeInSuperclassHidesTypeInGeneratedEnclosingClass() {
    QualifiedName generatedType = QualifiedName.of("com.example", "A");
    QualifiedName scopeType = generatedType.nestedType("B");
    QualifiedName supertype = QualifiedName.of(AbstractMap.class);
    QualifiedName hiddenNestedType = generatedType.nestedType("Entry");

    handler.declareGeneratedType(Visibility.PACKAGE, generatedType, ImmutableSet.of());
    handler.declareGeneratedType(Visibility.PACKAGE, scopeType, ImmutableSet.of(supertype));
    handler.declareGeneratedType(Visibility.PACKAGE, hiddenNestedType, ImmutableSet.of());

    ScopeState result = handler.visibilityIn(scopeType, hiddenNestedType);
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void typeInInterfaceHidesTypeInGeneratedEnclosingClass() {
    QualifiedName generatedType = QualifiedName.of("com.example", "A");
    QualifiedName scopeType = generatedType.nestedType("B");
    QualifiedName interfaceType = QualifiedName.of(Map.class);
    QualifiedName hiddenNestedType = generatedType.nestedType("Entry");

    handler.declareGeneratedType(Visibility.PACKAGE, generatedType, ImmutableSet.of());
    handler.declareGeneratedType(Visibility.PACKAGE, scopeType, ImmutableSet.of(interfaceType));
    handler.declareGeneratedType(Visibility.PACKAGE, hiddenNestedType, ImmutableSet.of());

    ScopeState result = handler.visibilityIn(scopeType, hiddenNestedType);
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void ambiguousSupertypeScopeReferenceConsideredHidden() {
    model.newType(
        "package com.example;",
        "interface I {",
        "  interface X {}",
        "}",
        "interface J {",
        "  interface X {}",
        "}",
        "class A {",
        "  abstract class B implements I, J {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "I", "X"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void packageProtectedTypeInSupertypeInSamePackageIsInScope() {
    model.newType(
        "package com.example;",
        "class S {",
        "  class X {}",
        "}",
        "class A {",
        "  abstract class B extends S {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "S", "X"));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void packageProtectedTypeInSupertypeInSamePackageHidesTypeInOuterScope() {
    model.newType(
        "package com.example;",
        "class S {",
        "  class X {}",
        "}",
        "class A {",
        "  abstract class B extends S {}",
        "  class X {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "A", "X"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }

  @Test
  public void privateTypeInSupertypeInSamePackageDoesNotHideTypeInOuterScope() {
    model.newType(
        "package com.example;",
        "class S {",
        "  private class X {}",
        "}",
        "class A {",
        "  abstract class B extends S {}",
        "  class X {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "A", "X"));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void packageProtectedTypeInSupertypeInDifferentPackageDoesNotHideTypeInOuterScope() {
    model.newType(
        "package com.example.n;",
        "class S {",
        "  class X {}",
        "}");
    model.newType(
        "package com.example;",
        "class A {",
        "  abstract class B extends com.example.n.S {}",
        "  class X {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "A", "X"));
    assertThat(result).isEqualTo(ScopeState.IN_SCOPE);
  }

  @Test
  public void protectedTypeInSupertypeInDifferentPackageHidesTypeInOuterScope() {
    model.newType(
        "package com.example.n;",
        "public class S {",
        "  protected class X {}",
        "}");
    model.newType(
        "package com.example;",
        "class A {",
        "  abstract class B extends com.example.n.S {}",
        "  class X {}",
        "}");
    ScopeState result = handler.visibilityIn(
        QualifiedName.of("com.example", "A", "B"), QualifiedName.of("com.example", "A", "X"));
    assertThat(result).isEqualTo(ScopeState.HIDDEN);
  }
}
