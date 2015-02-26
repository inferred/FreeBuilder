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

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * A {@link SourceBuilder} that writes to a {@link StringBuilder}.
 */
public final class SourceStringBuilder implements SourceBuilder {

  private final StringBuilder destination;
  private final TypeShortener shortener;

  /**
   * Returns a {@link SourceStringBuilder} that always shortens types, even if that causes
   * conflicts.
   */
  public static SourceStringBuilder simple() {
    return new SourceStringBuilder(new TypeShortener.AlwaysShorten());
  }

  SourceStringBuilder(TypeShortener shortener) {
    this(shortener, new StringBuilder());
  }

  SourceStringBuilder(TypeShortener shortener, StringBuilder destination) {
    this.destination = destination;
    this.shortener = shortener;
  }

  @Override
  public SourceBuilder add(String fmt, Object... args) {
    Object[] substituteArgs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      substituteArgs[i] = substitute(args[i]);
    }
    destination.append(String.format(fmt, substituteArgs));
    return this;
  }

  @Override
  public SourceBuilder addLine(String fmt, Object... args) {
    return add(fmt + "\n", args);
  }

  /** Returns the source code written so far. */
  @Override
  public String toString() {
    return destination.toString();
  }

  private Object substitute(Object arg) {
    if (arg instanceof Package) {
      return ((Package) arg).getName();
    } else if (arg instanceof PackageElement) {
      return ((PackageElement) arg).getQualifiedName();
    } else if (arg instanceof Class<?>) {
      return shortener.shorten((Class<?>) arg);
    } else if (arg instanceof TypeElement) {
      return shortener.shorten((TypeElement) arg);
    } else if (arg instanceof DeclaredType
        && ((DeclaredType) arg).asElement() instanceof TypeElement) {
      DeclaredType mirror = (DeclaredType) arg;
      checkArgument(isLegalType(mirror), "Cannot write unknown type %s", mirror);
      return shortener.shorten(mirror);
    } else if (arg instanceof TypeReference) {
      return shortener.shorten((TypeReference) arg);
    } else {
      return arg;
    }
  }

  private static boolean isLegalType(TypeMirror mirror) {
    return !(new IsInvalidTypeVisitor().visit(mirror));
  }
}
