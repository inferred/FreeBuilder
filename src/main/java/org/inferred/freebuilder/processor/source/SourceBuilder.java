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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.ClassLoader.getSystemClassLoader;
import static org.inferred.freebuilder.processor.source.IsInvalidTypeVisitor.isLegalType;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.inferred.freebuilder.processor.source.ScopeHandler.Reflection;
import org.inferred.freebuilder.processor.source.feature.EnvironmentFeatureSet;
import org.inferred.freebuilder.processor.source.feature.Feature;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.feature.FeatureType;
import org.inferred.freebuilder.processor.source.feature.GuavaLibrary;
import org.inferred.freebuilder.processor.source.feature.StaticFeatureSet;

/**
 * Source code builder, using format strings for readability, with sensible formatting for type
 * objects.
 *
 * <pre>
 * // Imports StringBuilder and appends "  StringBuilder foo;\n" to the source code.
 * builder.addLine("  %s foo;", StringBuilder.class);</pre>
 */
public class SourceBuilder {

  /**
   * Returns a {@link SourceBuilder}. {@code env} will be inspected for potential import collisions.
   * If {@code features} is not null, it will be used instead of those deduced from {@code env}.
   */
  public static SourceBuilder forEnvironment(ProcessingEnvironment env, FeatureSet features) {
    return new SourceBuilder(
        new CompilerReflection(env.getElementUtils()),
        Optional.ofNullable(features).orElseGet(() -> new EnvironmentFeatureSet(env)));
  }

  /**
   * Returns a {@link SourceBuilder} using {@code features}. The system classloader will be
   * inspected for potential import collisions.
   */
  @VisibleForTesting
  public static SourceBuilder forTesting(Feature<?>... features) {
    return forTesting(new StaticFeatureSet(features));
  }

  /**
   * Returns a {@link SourceBuilder} using {@code features}. The system classloader will be
   * inspected for potential import collisions.
   */
  @VisibleForTesting
  public static SourceBuilder forTesting(FeatureSet features) {
    return new SourceBuilder(new RuntimeReflection(getSystemClassLoader()), features);
  }

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private final CompilationUnitBuilder source;

  private SourceBuilder(Reflection reflect, FeatureSet features) {
    source = new CompilationUnitBuilder(reflect, features);
  }

  /**
   * Appends formatted text to the source.
   *
   * <p>Formatting supports {@code %s} and {@code %n$s}. Most args are converted according to their
   * {@link Object#toString()} method, except that:
   *
   * <ul>
   *   <li>{@link Package} and {@link PackageElement} instances use their fully-qualified names (no
   *       "package " prefix).
   *   <li>{@link Class}, {@link TypeElement}, {@link DeclaredType} and {@link QualifiedName}
   *       instances use their qualified names where necessary, or shorter versions if a suitable
   *       import line can be added.
   *   <li>{@link Excerpt} instances have {@link Excerpt#addTo(SourceBuilder)} called.
   * </ul>
   */
  public SourceBuilder add(String fmt, Object... args) {
    TemplateApplier.withParams(args).onText(source::append).onParam(this::add).parse(fmt);
    return this;
  }

  /** Equivalent to {@code add("%s", excerpt)}. */
  public SourceBuilder add(Excerpt excerpt) {
    excerpt.addTo(this);
    return this;
  }

  /**
   * Appends a formatted line of code to the source.
   *
   * <p>Formatting supports {@code %s} and {@code %n$s}. Most args are converted according to their
   * {@link Object#toString()} method, except that:
   *
   * <ul>
   *   <li>{@link Package} and {@link PackageElement} instances use their fully-qualified names (no
   *       "package " prefix).
   *   <li>{@link Class}, {@link TypeElement}, {@link DeclaredType} and {@link QualifiedName}
   *       instances use their qualified names where necessary, or shorter versions if a suitable
   *       import line can be added.
   *   <li>{@link Excerpt} instances have {@link Excerpt#addTo(SourceBuilder)} called.
   * </ul>
   */
  public SourceBuilder addLine(String fmt, Object... args) {
    add(fmt, args);
    source.append(LINE_SEPARATOR);
    return this;
  }

  /**
   * Returns the instance of {@code featureType} appropriate for the source being written. For
   * instance, <code>code.feature({@link GuavaLibrary#GUAVA
   * GUAVA}).{@link GuavaLibrary#isAvailable() isAvailable()}</code> returns true if the Guava
   * library can be used in the generated source code.
   *
   * <p>Fluent extension point for features dynamically determined based on the current {@link
   * ProcessingEnvironment}.
   *
   * @see Feature
   */
  public <T extends Feature<T>> T feature(FeatureType<T> featureType) {
    return source.feature(featureType);
  }

  /** Returns the current scope (e.g. visible method parameters). */
  public Scope scope() {
    return source.scope();
  }

  /**
   * Return the qualified name of the main type declared by this unit.
   *
   * @throws IllegalStateException if no package or type has been declared
   */
  public QualifiedName typename() {
    return source.typename();
  }

  @Override
  public String toString() {
    return source.toString();
  }

  private void add(Object arg) {
    if (arg instanceof Excerpt) {
      ((Excerpt) arg).addTo(this);
    } else if (arg instanceof Package) {
      source.append(((Package) arg).getName());
    } else if (arg instanceof Element) {
      ElementAppender.appendShortened((Element) arg, source);
    } else if (arg instanceof Class<?>) {
      source.append(QualifiedName.of((Class<?>) arg));
    } else if (arg instanceof TypeMirror) {
      TypeMirror mirror = (TypeMirror) arg;
      checkArgument(isLegalType(mirror), "Cannot write unknown type %s", mirror);
      TypeMirrorAppender.appendShortened(mirror, source);
    } else if (arg instanceof QualifiedName) {
      source.append((QualifiedName) arg);
    } else if (arg instanceof AnnotationMirror) {
      AnnotationSource.addSource(this, (AnnotationMirror) arg);
    } else if (arg instanceof CharSequence) {
      source.append((CharSequence) arg);
    } else {
      source.append(arg.toString());
    }
  }
}
