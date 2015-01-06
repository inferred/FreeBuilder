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
import com.google.common.collect.Multimaps;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

/**
 * Fake implementation of {@link Messager} that stores all messages as strings of the form
 * "[SEVERITY] Message", indexed (non-uniquely) by element.
 */
public class FakeMessager implements Messager {

  private final Multimap<String, String> messagesByElement = ArrayListMultimap.create();

  public Multimap<String, String> getMessagesByElement() {
    return Multimaps.unmodifiableMultimap(messagesByElement);
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
}