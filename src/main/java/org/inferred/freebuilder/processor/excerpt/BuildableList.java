/*
 * Copyright 2017 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.excerpt;

import static org.inferred.freebuilder.processor.BuildableType.PartialToBuilderMethod.TO_BUILDER_AND_MERGE;
import static org.inferred.freebuilder.processor.BuilderFactory.TypeInference.EXPLICIT_TYPES;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.BuildableType;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Excerpts defining a list implementation that stores a mixture of builders and value types.
 */
public class BuildableList extends Excerpt {

  public static LazyName of(BuildableType element) {
    return new BuildableList(element).name();
  }

  private final BuildableType element;

  private BuildableList(BuildableType element) {
    this.element = element;
  }

  LazyName name() {
    return LazyName.of(element.type().getSimpleName() + "BuilderList", this);
  }

  @Override
  public void addTo(SourceBuilder code) {
    code.addLine("@%s({\"rawtypes\", \"unchecked\"})", SuppressWarnings.class)
        .addLine("private class %s extends %s<%s> implements %s {",
            name(),
            AbstractList.class,
            element.builderType(),
            RandomAccess.class)
        .addLine("")
        .addLine("  // A list of value or builder instances");
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("  private %s elements = %s.of();", List.class, ImmutableList.class);
    } else {
      code.addLine("  private final %1$s elements = new %1$s();", ArrayList.class);
    }
    addSize(code);
    addGet(code);
    addSet(code);
    addAdd(code);
    addRemove(code);
    addEnsureCapacity(code);
    addAddValue(code);
    addAddAllValues(code);
    addBuild(code, "build");
    addBuild(code, "buildPartial");
    code.addLine("}");
  }

  private static void addSize(SourceBuilder code) {
    code.addLine("")
        .addLine("@Override")
        .addLine("public int size() {")
        .addLine("  return elements.size();")
        .addLine("}");
  }

  private void addGet(SourceBuilder code) {
    code.addLine("")
        .addLine("@Override")
        .addLine("public %s get(int index) {", element.builderType())
        .addLine("  Object element = elements.get(index);")
        .addLine("  if (element instanceof %s) {", element.type().getQualifiedName());
    convertToArrayList(code);
    convertToBuilder("element", code);
    code.addLine("    elements.set(index, element);")
        .addLine("  }")
        .addLine("  return (%s) element;", element.builderType())
        .addLine("}");
  }

  private void addSet(SourceBuilder code) {
    code.addLine("")
        .addLine("@Override")
        .addLine("public %1$s set(int index, %1$s element) {", element.builderType());
    convertToArrayList(code);
    code.addLine("  Object oldElement = elements.set(index, element);")
        .addLine("  if (oldElement instanceof %s) {", element.type().getQualifiedName());
    convertToBuilder("oldElement", code);
    code.addLine("  }")
        .addLine("  return (%s) oldElement;", element.builderType())
        .addLine("}");
  }

  private void addAdd(SourceBuilder code) {
    code.addLine("")
        .addLine("@Override")
        .addLine("public void add(int index, %s element) {", element.builderType());
    convertToArrayList(code);
    code.addLine("  elements.add(index, element);")
        .addLine("}");
  }

  private void addRemove(SourceBuilder code) {
    code.addLine("")
        .addLine("@Override")
        .addLine("public %s remove(int index) {", element.builderType());
    convertToArrayList(code);
    code.addLine("  Object oldElement = elements.remove(index);")
        .addLine("  if (oldElement instanceof %s) {", element.type().getQualifiedName());
    convertToBuilder("oldElement", code);
    code.addLine("  }")
        .addLine("  return (%s) oldElement;", element.builderType())
        .addLine("}");
  }

  private static void addEnsureCapacity(SourceBuilder code) {
    code.addLine("")
        .addLine("void ensureCapacity(int minCapacity) {");
    if (code.feature(GUAVA).isAvailable()) {
      convertToArrayList(code);
      code.add("  ((%s) elements)", ArrayList.class);
    } else {
      code.add("  elements");
    }
    code.add(".ensureCapacity(minCapacity);%n")
        .addLine("}");
  }

  private void addAddValue(SourceBuilder code) {
    code.addLine("")
        .addLine("void addValue(%s element) {", element.type())
        .addLine("  %s.requireNonNull(element);", Objects.class);
    convertToArrayList(code);
    code.addLine("  elements.add(element);")
        .addLine("}");
  }

  private void addAddAllValues(SourceBuilder code) {
    if (!code.feature(GUAVA).isAvailable()) {
      return;
    }
    code.addLine("")
        .addLine("void addAllValues(%s<? extends %s> values) {", Iterable.class, element.type())
        .addLine("  if (elements.isEmpty() && values instanceof %s) {", ImmutableList.class)
        .addLine("    elements = (%s) values;", ImmutableList.class)
        .addLine("  } else {");
    convertToArrayList(code);
    code.addLine("    if (values instanceof %s) {", Collection.class)
        .addLine("      int newSize = elements.size() + ((%s) values).size();", Collection.class)
        .addLine("      ((%s) elements).ensureCapacity(newSize);", ArrayList.class)
        .addLine("    }")
        .addLine("    values.forEach(this::addValue);")
        .addLine("  }")
        .addLine("}");
  }

  private void addBuild(SourceBuilder code, String buildMethod) {
    code.addLine("")
        .addLine("%s<%s> %s() {", List.class, element.type(), buildMethod);
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("  if (elements instanceof %s) {", ImmutableList.class)
          .addLine("    return (%s<%s>) elements;", ImmutableList.class, element.type())
          .addLine("  }")
          .addLine("  %1$s.Builder<%2$s> values = %1$s.builder();",
              ImmutableList.class, element.type())
          .addLine("  for (Object element : elements) {")
          .addLine("    values.add(%s(element));", buildMethod)
          .addLine("  }")
          .addLine("  return values.build();");
    } else {
      code.addLine("  switch (elements.size()) {")
          .addLine("    case 0:")
          .addLine("      return %s.emptyList();", Collections.class)
          .addLine("    case 1:")
          .addLine("      return %s.singletonList(%s(elements.get(0)));",
              Collections.class, buildMethod)
          .addLine("    default:")
          .addLine("      Object[] values = new Object[elements.size()];")
          .addLine("      for (int i = 0; i < elements.size(); i++) {")
          .addLine("        values[i] = %s(elements.get(i));", buildMethod)
          .addLine("      }")
          .addLine("      return (%1$s<%2$s>)(%1$s<?>)", List.class, element.type())
          .addLine("          %s.unmodifiableList(%s.asList(values));",
              Collections.class, Arrays.class)
          .addLine("  }");
    }
    code.addLine("}")
        .addLine("")
        .addLine("private %s %s(Object element) {", element.type(), buildMethod)
        .addLine("  if (element instanceof %s) {", element.type().getQualifiedName())
        .addLine("    return (%s) element;", element.type())
        .addLine("  } else {")
        .addLine("    return ((%s) element).%s();", element.builderType(), buildMethod)
        .addLine("  }")
        .addLine("}");
  }

  private static void convertToArrayList(SourceBuilder code) {
    if (code.feature(GUAVA).isAvailable()) {
      code.addLine("if (elements instanceof %s) {", ImmutableList.class)
          .addLine("  elements = new %s(elements);", ArrayList.class)
          .addLine("}");
    }
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("type", element);
  }

  private void convertToBuilder(String variable, SourceBuilder code) {
    if (element.partialToBuilder() == TO_BUILDER_AND_MERGE) {
      code.addLine("    %1$s = ((%2$s) %1$s).toBuilder();", variable, element.type());
    } else {
      code.addLine("    %1$s = %2$s.mergeFrom((%3$s) %1$s);",
          variable,
          element.builderFactory().newBuilder(element.builderType(), EXPLICIT_TYPES),
          element.type());
    }
  }
}
