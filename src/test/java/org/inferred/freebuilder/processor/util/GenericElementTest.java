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

import static com.google.common.truth.Truth.assertThat;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeAsTypeElement;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeDeclared;
import static org.inferred.freebuilder.processor.util.ModelUtils.maybeVariable;

import com.google.common.base.Function;

import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Rule;
import org.junit.Test;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;

public class GenericElementTest {

  private static final QualifiedName FOO_BAR_NAME = QualifiedName.of("org.example", "FooBar");

  @Rule public final ModelRule model = new ModelRule();

  @Test
  public void testNonGenericElement() {
    GenericElement foobar =
        new GenericElement.Builder(FOO_BAR_NAME).build();
    assertThat(foobar.getSimpleName().toString()).isEqualTo("FooBar");
    assertThat(foobar.getTypeParameters()).isEmpty();
    assertThat(SourceStringBuilder.simple().add("%s", foobar).toString()).isEqualTo("FooBar");
  }

  @Test
  public void testGenericElement() {
    GenericElement foobar = new GenericElement.Builder(FOO_BAR_NAME)
        .addTypeParameter("T")
        .addTypeParameter("C")
        .build();
    assertThat(foobar.getSimpleName().toString()).isEqualTo("FooBar");
    assertThat(foobar.getTypeParameters()).hasSize(2);
    assertThat(foobar.getTypeParameters().get(0).getSimpleName().toString()).isEqualTo("T");
    assertThat(foobar.getTypeParameters().get(0).toString()).isEqualTo("T");
    assertThat(foobar.getTypeParameters().get(1).getSimpleName().toString()).isEqualTo("C");
    assertThat(foobar.getTypeParameters().get(1).toString()).isEqualTo("C");
  }

  @Test
  public void testBoundedGenericElement() {
    Function<TypeElement, Void> test = new Function<TypeElement, Void>() {
      @Override
      public Void apply(TypeElement foobar) {
        assertThat(foobar.getSimpleName().toString()).isEqualTo("FooBar");
        assertThat(foobar.getTypeParameters()).hasSize(2);
        assertThat(foobar.getTypeParameters().get(0).getSimpleName().toString()).isEqualTo("T");
        assertThat(foobar.getTypeParameters().get(0).toString()).isEqualTo("T");
        assertThat(foobar.getTypeParameters().get(1).getSimpleName().toString()).isEqualTo("C");
        assertThat(foobar.getTypeParameters().get(1).toString()).isEqualTo("C");
        return null;
      }
    };
    TypeElement realFoobar =
        model.newType("package org.example; interface FooBar<T extends Number, C> { }");
    GenericElement fakeFoobar = new GenericElement.Builder(FOO_BAR_NAME)
        .addTypeParameter("T", newTopLevelClass("java.lang.Number"))
        .addTypeParameter("C")
        .build();
    test.apply(realFoobar);
    test.apply(fakeFoobar);
  }

  @Test
  public void testGenericMirror_withTypeVariables() {
    Function<DeclaredType, Void> test = new Function<DeclaredType, Void>() {
      @Override public Void apply(DeclaredType foobar) {
        assertThat(foobar.asElement().getSimpleName().toString()).isEqualTo("FooBar");
        assertThat(foobar.getTypeArguments()).hasSize(2);
        assertThat(SourceStringBuilder.simple().add("%s", foobar).toString())
            .isEqualTo("FooBar<T, C>");
        return null;
      }
    };
    DeclaredType realFoobar = (DeclaredType)
        model.newType("package org.example; interface FooBar<T, C> { }").asType();
    GenericMirror fakeFoobar = new GenericElement.Builder(FOO_BAR_NAME)
        .addTypeParameter("T")
        .addTypeParameter("C")
        .build()
        .asType();
    test.apply(realFoobar);
    test.apply(fakeFoobar);
  }

  @Test
  public void testGenericMirror_withBounds() {
    Function<DeclaredType, Void> test = new Function<DeclaredType, Void>() {
      @Override public Void apply(DeclaredType foobar) {
        assertThat(foobar.asElement().getSimpleName().toString()).isEqualTo("FooBar");
        assertThat(foobar.getTypeArguments()).hasSize(2);
        assertThat(SourceStringBuilder.simple().add("%s", foobar).toString())
            .isEqualTo("FooBar<T, C>");
        return null;
      }
    };
    DeclaredType realFoobar = (DeclaredType)
        model.newType("package org.example; interface FooBar<T extends Number, C> { }").asType();
    GenericMirror fakeFoobar = new GenericElement.Builder(FOO_BAR_NAME)
        .addTypeParameter("T", newTopLevelClass("java.lang.Number"))
        .addTypeParameter("C")
        .build()
        .asType();
    test.apply(realFoobar);
    test.apply(fakeFoobar);
  }

  @Test
  public void testGenericMirror_withSelfReference() {
    Function<DeclaredType, Void> test = new Function<DeclaredType, Void>() {
      @Override public Void apply(DeclaredType foobar) {
        // FooBar<E extends FooBar<E>>
        TypeElement foobarElement = maybeAsTypeElement(foobar).get();
        assertThat(foobarElement.getSimpleName().toString()).isEqualTo("FooBar");
        assertThat(foobar.getTypeArguments()).hasSize(1);
        assertThat(SourceStringBuilder.simple().add("%s", foobar).toString())
            .isEqualTo("FooBar<E>");
        // E extends FooBar<E>
        TypeParameterElement typeParameter = foobarElement.getTypeParameters().get(0);
        assertThat(typeParameter.getSimpleName().toString()).isEqualTo("E");
        assertThat(typeParameter.getBounds()).hasSize(1);
        // FooBar<E>
        DeclaredType bound = maybeDeclared(typeParameter.getBounds().get(0)).get();
        assertThat(bound.asElement().getSimpleName().toString()).isEqualTo("FooBar");
        assertThat(bound.getTypeArguments()).hasSize(1);
        // E
        TypeVariable typeArgument = maybeVariable(bound.getTypeArguments().get(0)).get();
        assertThat(typeArgument.asElement()).isEqualTo(typeParameter);
        return null;
      }
    };
    DeclaredType realFoobar = (DeclaredType) model
        .newType("package org.example; interface FooBar<E extends FooBar<E>> { }").asType();
    GenericElement.Builder fakeFoobarBuilder = new GenericElement.Builder(FOO_BAR_NAME);
    fakeFoobarBuilder.getTypeParameter("E").addBound(fakeFoobarBuilder.asType());
    GenericMirror fakeFoobar = fakeFoobarBuilder.build().asType();
    test.apply(realFoobar);
    test.apply(fakeFoobar);
  }
}
