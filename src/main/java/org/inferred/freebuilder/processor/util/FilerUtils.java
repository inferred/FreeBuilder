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

import com.google.common.base.Throwables;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

/** Static utility methods for working with {@link Filer}. */
public class FilerUtils {

  /**
   * Writes {@code source} to the correct file for {@code classToWrite}.
   *
   * <p>This is complicated mainly by an EJC bug that returns the wrong object from
   * {@link Writer#append(CharSequence)}, plus how to handle any exception thrown from
   * {@link Writer#close()}.
   */
  public static void writeCompilationUnit(
      Filer filer,
      QualifiedName classToWrite,
      Element originatingElement,
      String source) throws IOException {
    Writer writer = filer
        .createSourceFile(classToWrite.toString(), originatingElement)
        .openWriter();
    try {
      writer.append(source);
    } catch (Throwable e) {
      if (ADD_SUPPRESSED != null) {
        // Use suppressed exceptions in Java 7+
        try {
          writer.close();
        } catch (Throwable t) {
          try {
            ADD_SUPPRESSED.invoke(e, t);
          } catch (Exception x) {
            throw new RuntimeException("Failed to add suppressed exception: " + x.getMessage(), e);
          }
        }
      } else {
        // Ignore any error thrown calling close() in Java 6
        try {
          writer.close();
        } catch (Throwable ignored) {}
      }
      Throwables.propagateIfPossible(e, IOException.class);
      throw Throwables.propagate(e);
    }
    writer.close();
  }

  private static final Method ADD_SUPPRESSED;
  static {
    Method addSuppressed;
    try {
      addSuppressed = Throwable.class.getMethod("addSuppressed", Throwable.class);
    } catch (NoSuchMethodException e) {
      addSuppressed = null;
    }
    ADD_SUPPRESSED = addSuppressed;
  }

  private FilerUtils() {}
}
