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

import static com.google.common.base.Preconditions.checkArgument;

import static org.inferred.freebuilder.processor.util.AnnotationSource.addSource;

import org.inferred.freebuilder.processor.util.Scope.FileScope;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.FeatureType;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.MissingFormatArgumentException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;

/**
 * A {@link SourceBuilder} that writes to a {@link StringBuilder}.
 */
public class SourceStringBuilder implements SourceBuilder {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
  private static final Pattern TEMPLATE_PARAM = Pattern.compile("%([%ns]|([1-9]\\d*)\\$s)");

  private final TypeShortener shortener;
  private final StringBuilder destination = new StringBuilder();
  private final FeatureSet features;
  private final Scope scope;

  /**
   * Returns a {@link SourceStringBuilder} that always shortens types, even if that causes
   * conflicts.
   */
  public static SourceBuilder simple(Feature<?>... features) {
    return new SourceStringBuilder(
        new TypeShortener.AlwaysShorten(), new StaticFeatureSet(features), new FileScope());
  }

  /**
   * Returns a {@link SourceStringBuilder} that returns compilable code.
   */
  public static SourceBuilder compilable(FeatureSet features) {
    return new SourceStringBuilder(
        new TypeShortener.NeverShorten(), features, new FileScope());
  }

  SourceStringBuilder(TypeShortener shortener, FeatureSet features, Scope scope) {
    this.shortener = shortener;
    this.features = features;
    this.scope = scope;
  }

  @Override
  public SourceStringBuilder add(Excerpt excerpt) {
    excerpt.addTo(this);
    return this;
  }

  @Override
  public SourceStringBuilder add(String template, Object... params) {
    int offset = 0;
    int nextParam = 0;
    Matcher matcher = TEMPLATE_PARAM.matcher(template);
    while (matcher.find()) {
      append(template.subSequence(offset, matcher.start()));
      if (matcher.group(1).contentEquals("%")) {
        append("%");
      } else if (matcher.group(1).contentEquals("n")) {
        append(LINE_SEPARATOR);
      } else if (matcher.group(1).contentEquals("s")) {
        if (nextParam >= params.length) {
          throw new MissingFormatArgumentException(matcher.group(0));
        }
        add(params[nextParam++]);
      } else {
        int index = Integer.parseInt(matcher.group(2)) - 1;
        if (index >= params.length) {
          throw new MissingFormatArgumentException(matcher.group(0));
        }
        add(params[index]);
      }
      offset = matcher.end();
    }
    append(template.subSequence(offset, template.length()));

    return this;
  }

  @Override
  public SourceStringBuilder addLine(String fmt, Object... args) {
    add(fmt, args);
    append(LINE_SEPARATOR);
    return this;
  }

  @Override
  public SourceStringBuilder subBuilder() {
    return new SourceStringBuilder(shortener, features, scope);
  }

  @Override
  public SourceStringBuilder subScope(Scope newScope) {
    return new SourceStringBuilder(shortener, features, newScope);
  }

  @Override
  public <T extends Feature<T>> T feature(FeatureType<T> feature) {
    return features.get(feature);
  }

  @Override
  public Scope scope() {
    return scope;
  }

  /** Returns the source code written so far. */
  @Override
  public String toString() {
    return destination.toString();
  }

  private void append(CharSequence chars) {
    destination.append(chars);
  }

  private void add(Object arg) {
    if (arg instanceof Excerpt) {
      ((Excerpt) arg).addTo(this);
    } else if (arg instanceof Package) {
      append(((Package) arg).getName());
    } else if (arg instanceof Element) {
      ADD_ELEMENT.visit((Element) arg, this);
    } else if (arg instanceof Class<?>) {
      append(shortener.shorten(QualifiedName.of((Class<?>) arg)));
    } else if (arg instanceof TypeMirror) {
      TypeMirror mirror = (TypeMirror) arg;
      checkArgument(isLegalType(mirror), "Cannot write unknown type %s", mirror);
      append(shortener.shorten(mirror));
    } else if (arg instanceof QualifiedName) {
      append(shortener.shorten((QualifiedName) arg));
    } else if (arg instanceof AnnotationMirror) {
      addSource(this, (AnnotationMirror) arg);
    } else if (arg instanceof CharSequence) {
      append((CharSequence) arg);
    } else {
      append(arg.toString());
    }
  }

  static boolean isLegalType(TypeMirror mirror) {
    return !(new IsInvalidTypeVisitor().visit(mirror));
  }

  private static final ElementVisitor<Void, SourceStringBuilder> ADD_ELEMENT =
      new SimpleElementVisitor6<Void, SourceStringBuilder>() {

        @Override
        public Void visitPackage(PackageElement e, SourceStringBuilder p) {
          p.append(e.getQualifiedName());
          return null;
        }

        @Override
        public Void visitType(TypeElement e, SourceStringBuilder p) {
          p.append(p.shortener.shorten(QualifiedName.of(e)));
          return null;
        }

        @Override
        protected Void defaultAction(Element e, SourceStringBuilder p) {
          p.append(e.toString());
          return null;
        }
      };
}
