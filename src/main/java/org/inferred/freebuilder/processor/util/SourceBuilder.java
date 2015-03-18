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

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Source code builder, using format strings for readability, with sensible formatting for
 * type objects.
 *
 * <pre><code>
 * // Imports StringBuilder and appends "  StringBuilder foo;\n" to the source code.
 * builder.addLine("  %s foo;", StringBuilder.class);</pre></code>
 */
public interface SourceBuilder {

  /**
   * Appends formatted text to the source.
   *
   * <p>Formatting is done by {@link String#format}, except that:<ul>
   * <li> {@link Package} and {@link PackageElement} instances use their fully-qualified names
   *      (no "package " prefix).
   * <li> {@link Class}, {@link TypeElement}, {@link DeclaredType} and {@link TypeReference}
   *      instances use their qualified names where necessary, or shorter versions if a suitable
   *      import line can be added.
   * <li> {@link Excerpt} instances have {@link Excerpt#addTo(SourceBuilder)} called.
   * </ul>
   */
  SourceBuilder add(String fmt, Object... args);

  /**
   * Appends a formatted line of code to the source.
   *
   * <p>Formatting is done by {@link String#format}, except that:<ul>
   * <li> {@link Package} and {@link PackageElement} instances use their fully-qualified names
   *      (no "package " prefix).
   * <li> {@link Class}, {@link TypeElement}, {@link DeclaredType} and {@link TypeReference}
   *      instances use their qualified names where necessary, or shorter versions if a suitable
   *      import line can be added.
   * <li> {@link Excerpt} instances have {@link Excerpt#addTo(SourceBuilder)} called.
   * </ul>
   */
  SourceBuilder addLine(String fmt, Object... args);

  SourceLevel getSourceLevel();
}
