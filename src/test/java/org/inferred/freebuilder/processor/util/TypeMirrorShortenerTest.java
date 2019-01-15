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

import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newNestedClass;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.junit.Assert.assertEquals;

import static java.util.stream.Collectors.joining;

import com.google.common.reflect.TypeToken;

import org.inferred.freebuilder.processor.util.testing.Model;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;

@RunWith(JUnit4.class)
public class TypeMirrorShortenerTest {

  private static class FakeShortener implements TypeShortener {
    @Override
    public void appendShortened(Appendable a, QualifiedName type) throws IOException {
      a.append("{{").append(type.getSimpleNames().stream().collect(joining("."))).append("}}");
    }
  }

  // Models are expensive to create, so tests share a lazy singleton
  private static Model model;

  private static void createModel() {
    if (model == null) {
      model = Model.create();
    }
  }

  @AfterClass
  public static void destroySharedModel() {
    if (model != null) {
      model.destroy();
      model = null;
    }
  }

  @Test
  public void testTopLevelType() {
    assertEquals("{{List}}", shorten(newTopLevelClass("java.util.List")));
  }

  @Test
  public void testNestedType() {
    ClassTypeImpl mapType = newTopLevelClass("java.util.Map");
    assertEquals("{{Map.Entry}}", shorten(newNestedClass(mapType.asElement(), "Entry")));
  }

  @Test
  public void testNestedGenericClass() {
    createModel();
    assertEquals(
        "{{Map.Entry}}<{{String}}, {{List}}<{{Double}}>>",
        shorten(model.typeMirror(new TypeToken<Map.Entry<String, List<Double>>>() {})));
  }

  @Test
  public void testInnerClassOnGenericType() {
    createModel();
    assertEquals("{{TypeMirrorShortenerTest.OuterClass}}<{{List}}<{{String}}>>.InnerClass",
        shorten(model.typeMirror(
            new TypeToken<TypeMirrorShortenerTest.OuterClass<List<String>>.InnerClass>() {})));
  }

  @Test
  public void testTypeVariables() {
    createModel();

    // Type variables should be printed as name only, not e.g. "E extends Enum<E>"
    assertEquals("{{Enum}}<E>", shorten(model.typeElement(Enum.class).asType()));
  }

  @Test
  public void testWildcards() {
    createModel();

    assertEquals("{{Map}}<{{Name}}, ? extends {{Logger}}>",
        shorten(model.typeMirror(new TypeToken<Map<Name, ? extends Logger>>() {})));
  }

  private static String shorten(TypeMirror type) {
    try {
      StringBuilder result = new StringBuilder();
      new TypeMirrorShortener(result).appendShortened(new FakeShortener(), type);
      return result.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class OuterClass<T> {
    private class InnerClass { }

    @SuppressWarnings("unused")
    T field;
  }
}
