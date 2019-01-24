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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.inferred.freebuilder.processor.model.ClassTypeImpl.newTopLevelClass;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

/** Tests for {@link FilerUtils}. */
@RunWith(MockitoJUnitRunner.class)
public class FilerUtilsTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Mock private Filer filer;
  @Mock private JavaFileObject sourceFile;
  private final StringWriter source = new StringWriter();
  private TypeElement originatingElement;

  private final SourceBuilder unit = SourceBuilder.forTesting()
      .addLine("package com.example;")
      .addLine("class Bar { }");

  @Before
  public void setup() throws IOException {
    originatingElement = newTopLevelClass("com.example.Foo").asElement();
    when(filer.createSourceFile(eq("com.example.Bar"), (Element[]) any())).thenReturn(sourceFile);
    when(sourceFile.openWriter()).thenReturn(source);
  }

  @Test
  public void testSimplePath() throws IOException {
    FilerUtils.writeCompilationUnit(filer, unit, originatingElement);
    assertEquals("package com.example;\n\nclass Bar {}\n", source.toString());
  }

  @Test
  public void testConstructor_avoidsEclipseWriterBug() throws IOException {
    // Due to a bug in Eclipse, we *must* call close on the object returned from openWriter().
    // Eclipse proxies a Writer but does not implement the fluent API correctly.
    // Here, we implement the fluent Writer API with the same bug:
    Writer mockWriter = Mockito.mock(Writer.class, (Answer<?>) invocation -> {
      if (Writer.class.isAssignableFrom(invocation.getMethod().getReturnType())) {
        // Erroneously return the delegate writer (matching the Eclipse bug!)
        return source;
      } else {
        return Answers.RETURNS_SMART_NULLS.get().answer(invocation);
      }
    });
    when(sourceFile.openWriter()).thenReturn(mockWriter);

    FilerUtils.writeCompilationUnit(filer, unit, originatingElement);
    verify(mockWriter).close();
  }

  @Test
  public void testFailuresSuppressed() throws IOException {
    Writer mockWriter = Mockito.mock(Writer.class);
    doThrow(new IOException("Error appending")).when(mockWriter).append(any());
    doThrow(new IOException("Error closing")).when(mockWriter).close();
    when(sourceFile.openWriter()).thenReturn(mockWriter);

    thrown.expect(IOException.class);
    thrown.expectMessage("Error appending");
    thrown.expect(suppressed(instanceOf(IOException.class)));
    thrown.expect(suppressed(hasProperty("message", equalTo("Error closing"))));
    FilerUtils.writeCompilationUnit(filer, unit, originatingElement);
  }

  private static Matcher<Throwable> suppressed(Matcher<?> matcher) {
    return new BaseMatcher<Throwable>() {

      @Override
      public boolean matches(Object item) {
        if (!(item instanceof Throwable)) {
          return false;
        }
        Throwable t = (Throwable) item;
        if (t.getSuppressed().length != 1) {
          return false;
        }
        return matcher.matches(t.getSuppressed()[0]);
      }

      @Override
      public void describeMismatch(Object item, Description description) {
        if (!(item instanceof Throwable)) {
          description.appendValue(item).appendText(" is not an exception");
        }
        Throwable t = (Throwable) item;
        if (t.getSuppressed().length == 0) {
          description.appendValue(item).appendText(" has no suppressed exceptions");
        } else if (t.getSuppressed().length > 1) {
          description.appendValue(item).appendText(" has multiple suppressed exceptions");
        }
        Throwable suppressed = t.getSuppressed()[0];
        if (!matcher.matches(suppressed)) {
          description.appendText("has suppressed exception ");
          matcher.describeMismatch(suppressed, description);
        }
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("exception with suppressed ").appendDescriptionOf(matcher);
      }
    };
  }

}
