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

import org.inferred.freebuilder.processor.util.Scope.FileScope;
import org.inferred.freebuilder.processor.util.Scope.MethodScope;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.io.IOException;

/**
 * A {@link SourceBuilder} that writes to a {@link StringBuilder}.
 */
public class SourceStringBuilder extends AbstractSourceBuilder<SourceStringBuilder> {

  /**
   * Returns a {@link SourceStringBuilder} that always shortens types, even if that causes
   * conflicts.
   */
  public static SourceStringBuilder simple(Feature<?>... features) {
    return new SourceStringBuilder(
        new TypeShortener.AlwaysShorten(),
        new StaticFeatureSet(features),
        new MethodScope(new FileScope()));
  }

  /**
   * Returns a {@link SourceStringBuilder} that returns compilable code.
   */
  public static SourceStringBuilder compilable(FeatureSet features) {
    return new SourceStringBuilder(
        new TypeShortener.NeverShorten(), features, new FileScope());
  }

  private final TypeShortener shortener;
  private final Scope scope;
  private final StringBuilder destination = new StringBuilder();

  SourceStringBuilder(TypeShortener shortener, FeatureSet features, Scope scope) {
    super(features);
    this.shortener = shortener;
    this.scope = scope;
  }

  @Override
  public Scope scope() {
    return scope;
  }

  @Override
  public SourceStringBuilder append(char c) {
    destination.append(c);
    return this;
  }

  @Override
  public SourceStringBuilder append(QualifiedName type) {
    try {
      shortener.appendShortened(destination, type);
      return this;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected SourceStringBuilder getThis() {
    return this;
  }

  @Override
  public String toString() {
    return destination.toString();
  }
}
