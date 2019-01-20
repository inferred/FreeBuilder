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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getLast;

import static org.inferred.freebuilder.processor.util.ImportManager.shortenReferences;

import com.google.common.annotations.VisibleForTesting;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

import org.inferred.freebuilder.processor.util.Scope.FileScope;
import org.inferred.freebuilder.processor.util.ScopeHandler.Reflection;
import org.inferred.freebuilder.processor.util.ScopeHandler.Visibility;
import org.inferred.freebuilder.processor.util.feature.EnvironmentFeatureSet;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;

/** {@code SourceBuilder} which also handles package declaration and imports. */
public class CompilationUnitBuilder
    extends AbstractSourceBuilder<CompilationUnitBuilder>
    implements SourceParser.EventHandler {

  private final ScopeHandler scopeHandler;
  private final SourceParser parser;
  private final List<Scope> scopes = new ArrayList<>();
  private final List<QualifiedName> types = new ArrayList<>();
  private final List<TypeUsage> usages = new ArrayList<>();
  private String pkg;
  private int importsIndex = -1;
  private final StringBuilder source = new StringBuilder();

  /**
   * Returns a {@link CompilationUnitBuilder}. {@code env} will be inspected for potential import
   * collisions. If {@code features} is not null, it will be used instead of those deduced from
   * {@code env}.
   */
  public static CompilationUnitBuilder forEnvironment(
      ProcessingEnvironment env,
      FeatureSet features) {
    return new CompilationUnitBuilder(
        new CompilerReflection(env.getElementUtils()),
        Optional.ofNullable(features).orElseGet(() -> new EnvironmentFeatureSet(env)));
  }

  /**
   * Returns a {@link CompilationUnitBuilder} for {@code classToWrite} using {@code features}. The
   * file preamble (package and imports) will be generated automatically.
   */
  @VisibleForTesting
  public static CompilationUnitBuilder forTesting(Feature<?>... features) {
    return new CompilationUnitBuilder(
        new RuntimeReflection(ClassLoader.getSystemClassLoader()),
        new StaticFeatureSet(features));
  }

  private CompilationUnitBuilder(
      Reflection reflect,
      FeatureSet features) {
    super(features);
    scopeHandler = new ScopeHandler(reflect);
    parser = new SourceParser(this);
    scopes.add(new FileScope());
    types.add(null);
  }

  @Override
  public void onPackageStatement(String packageName) {
    checkState(importsIndex == -1, "Package redeclared");
    pkg = packageName;
    importsIndex = source.length();
  }

  @Override
  public void onTypeBlockStart(String keyword, String simpleName, Set<String> supertypes) {
    QualifiedName type = nestedType(simpleName);
    types.add(type);
    scopes.add(getLast(scopes));
    scopeHandler.declareGeneratedType(Visibility.UNKNOWN, type, supertypes);
  }

  private QualifiedName nestedType(String simpleName) {
    QualifiedName outerType = getLast(types);
    if (outerType == null) {
      return QualifiedName.of(pkg, simpleName);
    } else {
      return outerType.nestedType(simpleName);
    }
  }

  @Override
  public void onMethodBlockStart(String methodName, Set<String> paramNames) {
    Scope methodScope = new Scope.MethodScope(getLast(scopes));
    for (String paramName : paramNames) {
      methodScope.putIfAbsent(new IdKey(paramName), methodScope);
    }
    types.add(getLast(types));
    scopes.add(methodScope);
  }

  @Override
  public void onOtherBlockStart() {
    types.add(getLast(types));
    scopes.add(getLast(scopes));
  }

  @Override
  public void onBlockEnd() {
    types.remove(types.size() - 1);
    scopes.remove(scopes.size() - 1);
    checkState(!types.isEmpty(), "Unexpected '}'");
  }

  @Override
  protected CompilationUnitBuilder getThis() {
    return this;
  }

  @Override
  public CompilationUnitBuilder append(char c) {
    source.append(c);
    parser.parse(c);
    return this;
  }

  @Override
  public CompilationUnitBuilder append(QualifiedName type) {
    if (type.getPackage().isEmpty() && type.isTopLevel()) {
      return append(type.getSimpleName());
    }
    TypeUsage.Builder usage = new TypeUsage.Builder()
        .start(source.length())
        .type(type)
        .nullableScope(getLast(types));
    append(type.toString());
    usages.add(usage.end(source.length()).build());
    return this;
  }

  @Override
  public Scope scope() {
    return getLast(scopes);
  }

  @Override
  public String toString() {
    if (importsIndex == -1) {
      return formatSource(source.toString());
    } else {
      return formatSource(shortenReferences(source, pkg, importsIndex, usages, scopeHandler));
    }
  }

  @VisibleForTesting
  public static String formatSource(String source) {
    try {
      return new Formatter().formatSource(source);
    } catch (FormatterException | RuntimeException e) {
      StringBuilder message = new StringBuilder()
          .append("Formatter failed:\n")
          .append(e.getMessage())
          .append("\nGenerated source:");
      int lineNo = 0;
      for (String line : source.split("\n")) {
        message
            .append("\n")
            .append(++lineNo)
            .append(": ")
            .append(line);
      }
      throw new RuntimeException(message.toString());
    }
  }
}
