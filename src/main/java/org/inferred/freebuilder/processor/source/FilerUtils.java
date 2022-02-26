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

import java.io.IOException;
import java.io.Writer;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

/** Static utility methods for working with {@link Filer}. */
public class FilerUtils {

  /**
   * Writes {@code unit} to the correct file.
   *
   * <p>This is complicated by an EJC bug that returns the wrong object from {@link
   * Writer#append(CharSequence)}.
   */
  public static void writeCompilationUnit(
      Filer filer, SourceBuilder unit, Element originatingElement) throws IOException {
    String typename = unit.typename().toString();
    String finalSource = unit.toString();
    try (Writer writer = filer.createSourceFile(typename, originatingElement).openWriter()) {
      writer.append(finalSource);
    }
  }

  private FilerUtils() {}
}
