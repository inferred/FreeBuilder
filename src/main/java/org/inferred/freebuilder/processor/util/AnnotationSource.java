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

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.apache.commons.lang3.StringEscapeUtils.escapeJava;
import static org.inferred.freebuilder.processor.util.ModelUtils.asElement;

import java.util.List;
import java.util.Map.Entry;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;

/**
 * Static methods for annotation-related source-code generation.
 */
public class AnnotationSource {

  /**
   * Adds a source-code representation of {@code annotation} to {@code}.
   */
  public static void addSource(SourceBuilder code, AnnotationMirror annotation) {
    new ValueSourceAdder(code).visitAnnotation(annotation, null);
  }

  /**
   * Returns true if {@code annotation} has a single element named "value", letting us drop the
   * 'value=' prefix in the source code.
   */
  private static boolean hasSingleValueWithDefaultKey(AnnotationMirror annotation) {
    if (annotation.getElementValues().size() != 1) {
      return false;
    }
    ExecutableElement key = getOnlyElement(annotation.getElementValues().keySet());
    return key.getSimpleName().contentEquals("value");
  }

  private static class ValueSourceAdder
      extends SimpleAnnotationValueVisitor6<Void, AnnotationValue> {

    private final SourceBuilder code;

    ValueSourceAdder(SourceBuilder code) {
      this.code = code;
    }

    @Override
    public Void visitAnnotation(AnnotationMirror annotation, AnnotationValue unused) {
      // By explicitly adding annotations rather than relying on AnnotationMirror.toString(),
      // we can import the types and make the code (hopefully) more readable.
      code.add("@%s", QualifiedName.of(asElement(annotation.getAnnotationType())));
      if (annotation.getElementValues().isEmpty()) {
        return null;
      }
      code.add("(");
      if (hasSingleValueWithDefaultKey(annotation)) {
        AnnotationValue value = getOnlyElement(annotation.getElementValues().values());
        visit(value, value);
      } else {
        String separator = "";
        for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
            : annotation.getElementValues().entrySet()) {
          code.add("%s%s = ", separator, entry.getKey().getSimpleName());
          visit(entry.getValue(), entry.getValue());
          separator = ", ";
        }
      }
      code.add(")");
      return null;
    }

    @Override
    public Void visitArray(List<? extends AnnotationValue> vals, AnnotationValue unused) {
      // Single-element arrays can omit the enclosing braces
      if (vals.size() == 1) {
        AnnotationValue value = getOnlyElement(vals);
        visit(value, value);
      } else {
        code.add("{");
        String separator = "";
        for (AnnotationValue value : vals) {
          code.add(separator);
          visit(value, value);
          separator = ", ";
        }
        code.add("}");
      }
      return null;
    }

    @Override
    public Void visitString(String s, AnnotationValue p) {
      // Some versions of Eclipse contain a bug where strings are not correctly escaped by
      // AnnotationValue.toString(), so we special-case strings to ensure it's done correctly.
      code.add("\"%s\"", escapeJava(s));
      return null;
    }

    @Override
    protected Void defaultAction(Object obj, AnnotationValue value) {
      code.add("%s", value.toString());
      return null;
    }

    @Override
    public Void visitUnknown(AnnotationValue value, AnnotationValue unused) {
      code.add("%s", value.toString());
      return null;
    }
  }

  private AnnotationSource() { }
}
