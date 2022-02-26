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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getLast;
import static java.util.stream.Collectors.joining;
import static org.inferred.freebuilder.processor.source.ImportManager.shortenReferences;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.inferred.freebuilder.processor.source.ScopeHandler.Reflection;
import org.inferred.freebuilder.processor.source.ScopeHandler.Visibility;
import org.inferred.freebuilder.processor.source.feature.Feature;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.feature.FeatureType;

/** Internals of {@code SourceBuilder}, handling source parsing and type shortening. */
class CompilationUnitBuilder implements QualifiedNameAppendable, SourceParser.EventHandler {

  private final FeatureSet features;
  private final ScopeHandler scopeHandler;
  private final SourceParser parser;
  private final List<Scope> scopes = new ArrayList<>();
  private final List<QualifiedName> types = new ArrayList<>();
  private final List<TypeUsage> usages = new ArrayList<>();
  private String pkg;
  private String topLevelType;
  private int importsIndex = -1;
  private final StringBuilder source = new StringBuilder();

  CompilationUnitBuilder(Reflection reflect, FeatureSet features) {
    this.features = features;
    scopeHandler = new ScopeHandler(reflect);
    parser = new SourceParser(this);
    scopes.add(new InitialScope());
    types.add(null);
  }

  public <T extends Feature<T>> T feature(FeatureType<T> feature) {
    return features.get(feature);
  }

  public QualifiedName typename() {
    checkState(pkg != null, "No package statement");
    checkState(topLevelType != null, "No class declaration");
    return QualifiedName.of(pkg, topLevelType);
  }

  @Override
  public void onPackageStatement(String packageName) {
    checkState(importsIndex == -1, "Package redeclared");
    checkState(scopes.size() == 1, "Package declaration too late");
    InitialScope initialScope = (InitialScope) getLast(scopes);
    checkState(initialScope.isEmpty(), "Package declaration too late");
    scopes.add(new Scope.FileScope());
    pkg = packageName;
    importsIndex = source.length();
  }

  @Override
  public void onTypeBlockStart(String keyword, String simpleName, Set<String> supertypes) {
    if (topLevelType == null) {
      topLevelType = simpleName;
    }
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
  public void append(char c) {
    source.append(c);
    parser.parse(c);
  }

  @Override
  public void append(CharSequence csq) {
    append(csq, 0, csq.length());
  }

  @Override
  public void append(CharSequence csq, int start, int end) {
    for (int i = start; i < end; i++) {
      append(csq.charAt(i));
    }
  }

  @Override
  public void append(QualifiedName type) {
    if (type.getPackage().isEmpty() && type.isTopLevel()) {
      append(type.getSimpleName());
      return;
    }
    TypeUsage.Builder usage =
        new TypeUsage.Builder().start(source.length()).type(type).nullableScope(getLast(types));
    append(type.toString());
    usages.add(usage.end(source.length()).build());
  }

  public Scope scope() {
    return getLast(scopes);
  }

  @Override
  public String toString() {
    if (importsIndex == -1) {
      return formatSnippet(source, usages);
    } else {
      return formatSource(shortenReferences(source, pkg, importsIndex, usages, scopeHandler));
    }
  }

  private static String formatSnippet(StringBuilder source, List<TypeUsage> usages) {
    StringBuilder snippet = new StringBuilder();
    int offset = 0;
    for (TypeUsage usage : usages) {
      snippet.append(source, offset, usage.start());
      snippet.append(usage.type().getSimpleNames().stream().collect(joining(".")));
      offset = usage.end();
    }
    snippet.append(source, offset, source.length());
    return snippet.toString();
  }

  private static String formatSource(String source) {
    try {
      return new Formatter().formatSource(source);
    } catch (FormatterException | RuntimeException e) {
      StringBuilder message =
          new StringBuilder()
              .append("Formatter failed:\n")
              .append(e.getMessage())
              .append("\nGenerated source:");
      int lineNo = 0;
      for (String line : source.split("\n")) {
        message.append("\n").append(++lineNo).append(": ").append(line);
      }
      throw new RuntimeException(message.toString());
    }
  }

  private static class InitialScope extends Scope {
    @Override
    protected boolean canStore(Key<?> key) {
      return true;
    }
  }
}
