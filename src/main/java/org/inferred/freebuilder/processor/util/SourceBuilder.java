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

import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureType;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * Source code builder, using format strings for readability, with sensible formatting for
 * type objects.
 *
 * <pre>
 * // Imports StringBuilder and appends "  StringBuilder foo;\n" to the source code.
 * builder.addLine("  %s foo;", StringBuilder.class);</pre>
 */
public interface SourceBuilder {

  /**
   * Appends formatted text to the source.
   *
   * <p>Formatting is done by {@link String#format}, except that:<ul>
   * <li> {@link Package} and {@link PackageElement} instances use their fully-qualified names
   *      (no "package " prefix).
   * <li> {@link Class}, {@link TypeElement}, {@link DeclaredType} and {@link QualifiedName}
   *      instances use their qualified names where necessary, or shorter versions if a suitable
   *      import line can be added.
   * <li> {@link Excerpt} instances have {@link Excerpt#addTo(SourceBuilder)} called.
   * </ul>
   */
  SourceBuilder add(String fmt, Object... args);

  /**
   * Equivalent to {@code add("%s", excerpt)}.
   */
  SourceBuilder add(Excerpt excerpt);

  /**
   * Appends a formatted line of code to the source.
   *
   * <p>Formatting is done by {@link String#format}, except that:<ul>
   * <li> {@link Package} and {@link PackageElement} instances use their fully-qualified names
   *      (no "package " prefix).
   * <li> {@link Class}, {@link TypeElement}, {@link DeclaredType} and {@link QualifiedName}
   *      instances use their qualified names where necessary, or shorter versions if a suitable
   *      import line can be added.
   * <li> {@link Excerpt} instances have {@link Excerpt#addTo(SourceBuilder)} called.
   * </ul>
   */
  SourceBuilder addLine(String fmt, Object... args);

  /**
   * Returns a {@code SourceStringBuilder} with the same configuration as this builder. In
   * particular, the {@code TypeShortener} will be shared, so any types added to the sub-builder
   * will be included in the imports for this builder (and its parents).
   */
  SourceStringBuilder subBuilder();

  /**
   * Returns a {@code SourceStringBuilder} with the same configuration as this builder, but with
   * a different scope. In particular, the {@code TypeShortener} will be shared, so any types added
   * to the sub-builder will be included in the imports for this builder (and its parents).
   */
  SourceStringBuilder subScope(Scope newScope);

  /**
   * Returns the instance of {@code featureType} appropriate for the source being written. For
   * instance, <code>code.feature({@link GuavaLibrary#GUAVA
   * GUAVA}).{@link GuavaLibrary#isAvailable() isAvailable()}</code> returns true if the Guava
   * library can be used in the generated source code.
   *
   * <p>Fluent extension point for features dynamically determined based on the current
   * {@link ProcessingEnvironment}.
   *
   * @see Feature
   */
  <T extends Feature<T>> T feature(FeatureType<T> featureType);

  /**
   * Returns the current scope (e.g. visible method parameters).
   */
  Scope scope();

}
