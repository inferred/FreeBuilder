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
package org.inferred.freebuilder.processor.util.testing;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

/**
 * Test rule for verifying compiler messages sent to {@link Messager}.
 */
public class MessagerRule implements Messager, TestRule {

  private final Multimap<String, String> messagesByElement = ArrayListMultimap.create();

  public void verifyError(String element, String message) {
    verify(Kind.ERROR, element, message);
  }

  public void verifyNote(String element, String message) {
    verify(Kind.NOTE, element, message);
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg, Element e) {
    messagesByElement.put(e.getSimpleName().toString(), String.format("[%s] %s", kind, msg));
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg, Element e, AnnotationMirror a) {
    messagesByElement.put(
        e.getSimpleName().toString() + "@" + a.getAnnotationType().asElement().getSimpleName(),
        String.format("[%s] %s", kind, msg));
  }

  @Override
  public void printMessage(Kind kind, CharSequence msg, Element e, AnnotationMirror a,
      AnnotationValue v) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        base.evaluate();
        if (!messagesByElement.isEmpty()) {
          StringBuilder detail = new StringBuilder(
              "The following compilation messages were unexpectedly output:");
          messagesByElement.values().forEach(message -> {
            detail.append("\n  ").append(message);
          });
          throw new AssertionError(detail.toString());
        }
      }
    };
  }

  private void verify(Kind kind, String element, String message) {
    boolean present = messagesByElement.remove(element, String.format("[%s] %s", kind, message));
    if (!present) {
      StringBuilder detail = new StringBuilder("Expected ")
          .append(kind.toString().toLowerCase().replace('_', ' '))
          .append(" \"")
          .append(message)
          .append("\" on ")
          .append(element)
          .append(" but got");
      if (messagesByElement.isEmpty()) {
        detail.append(" no messages");
      } else if (messagesByElement.containsKey(element)) {
        detail.append(":");
        for (String actual : messagesByElement.get(element)) {
          detail.append("\n  ").append(actual);
        }
      } else {
        detail.append(" errors on other elements:");
        for (String actual : messagesByElement.keySet()) {
          detail.append("\n  ").append(actual);
        }
      }
      throw new AssertionError(detail.toString());
    }
  }
}
