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
package org.inferred.freebuilder.processor.source;

import static java.util.stream.Collectors.joining;
import static org.inferred.freebuilder.processor.model.ClassTypeImpl.newNestedClass;
import static org.inferred.freebuilder.processor.model.ClassTypeImpl.newTopLevelClass;
import static org.junit.Assert.assertEquals;

import com.google.common.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;
import org.inferred.freebuilder.processor.model.ClassTypeImpl;
import org.inferred.freebuilder.processor.source.testing.Model;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TypeMirrorAppenderTest {

  private static class FakeAppendable implements QualifiedNameAppendable {

    private final StringBuilder s = new StringBuilder();

    @Override
    public void append(char c) {
      s.append(c);
    }

    @Override
    public void append(CharSequence csq) {
      s.append(csq);
    }

    @Override
    public void append(CharSequence csq, int start, int end) {
      s.append(csq, start, end);
    }

    @Override
    public void append(QualifiedName type) {
      s.append("{{").append(type.getSimpleNames().stream().collect(joining("."))).append("}}");
    }

    @Override
    public String toString() {
      return s.toString();
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
    assertEquals(
        "{{TypeMirrorAppenderTest.OuterClass}}<{{List}}<{{String}}>>.InnerClass",
        shorten(
            model.typeMirror(
                new TypeToken<TypeMirrorAppenderTest.OuterClass<List<String>>.InnerClass>() {})));
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

    assertEquals(
        "{{Map}}<{{Name}}, ? extends {{Logger}}>",
        shorten(model.typeMirror(new TypeToken<Map<Name, ? extends Logger>>() {})));
  }

  private static String shorten(TypeMirror type) {
    FakeAppendable result = new FakeAppendable();
    TypeMirrorAppender.appendShortened(type, result);
    return result.toString();
  }

  private static class OuterClass<T> {
    private class InnerClass {}

    @SuppressWarnings("unused")
    T field;
  }
}
