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
package org.inferred.freebuilder.processor.property;

import static org.inferred.freebuilder.processor.property.ElementFactory.STRINGS;
import static org.inferred.freebuilder.processor.property.ElementFactory.TYPES;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.FeatureSets;
import org.inferred.freebuilder.processor.NamingConvention;
import org.inferred.freebuilder.processor.Processor;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.testing.BehaviorTester;
import org.inferred.freebuilder.processor.source.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.source.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.source.testing.TestBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ListMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "List<{0}>, checked={1}, {2}, {3}")
  public static Iterable<Object[]> parameters() {
    List<Boolean> checked = ImmutableList.of(false, true);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(TYPES, checked, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory elements;
  private final boolean checked;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final SourceBuilder listPropertyType;
  private final SourceBuilder genericType;
  /** Simple type that substitutes passed-in objects, in this case by interning strings. */
  private final SourceBuilder internedStringsType;

  public ListMutateMethodTest(
      ElementFactory elements,
      boolean checked,
      NamingConvention convention,
      FeatureSet features) {
    this.elements = elements;
    this.checked = checked;
    this.convention = convention;
    this.features = features;

    listPropertyType = SourceBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s> %s;", List.class, elements.type(), convention.get())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {");
    if (checked) {
      listPropertyType
          .addLine("    @Override public Builder addItems(%s element) {",
              elements.unwrappedType())
          .addLine("      if (!(%s)) {", elements.validation())
          .addLine("        throw new IllegalArgumentException(\"%s\");", elements.errorMessage())
          .addLine("      }")
          .addLine("      return super.addItems(element);")
          .addLine("    }");
    }
    listPropertyType
        .addLine("  }")
        .addLine("}");

    genericType = SourceBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType<T extends %s> {", elements.supertype())
        .addLine("  %s<T> %s;", List.class, convention.get())
        .addLine("")
        .addLine("  public static class Builder<T extends %s> extends DataType_Builder<T> {",
            elements.supertype());
    if (checked) {
      genericType
          .addLine("    @Override public Builder<T> addItems(T element) {")
          .addLine("      if (!(%s)) {", elements.supertypeValidation())
          .addLine("        throw new IllegalArgumentException(\"%s\");", elements.errorMessage())
          .addLine("      }")
          .addLine("      return super.addItems(element);")
          .addLine("    }");
    }
    genericType
        .addLine("  }")
        .addLine("}");

    if (!checked && elements == STRINGS) {
      internedStringsType = SourceBuilder.forTesting()
          .addLine("package com.example;")
          .addLine("@%s", FreeBuilder.class)
          .addLine("public interface DataType {")
          .addLine("  %s<String> %s;", List.class, convention.get())
          .addLine("")
          .addLine("  public static class Builder extends DataType_Builder {")
          .addLine("    @Override public Builder addItems(String element) {")
          .addLine("      return super.addItems(element.intern());")
          .addLine("    }")
          .addLine("  }")
          .addLine("}");
    } else {
      internedStringsType = null;
    }
  }

  @Before
  public void setUp() {
    behaviorTester.withPermittedPackage(elements.type().getPackage());
  }

  @Test
  public void mutateAndAddModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.add(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(elements.errorMessage());
    }
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder().mutateItems(items -> items.add(%s));",
                elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddModifiesUnderlyingPropertyWhenGeneric() {
    behaviorTester
        .with(new Processor(features))
        .with(genericType)
        .with(testBuilder()
            .addLine("DataType<%1$s> value = new DataType.Builder<%1$s>()", elements.type())
            .addLine("    .mutateItems(items -> items.add(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddChecksArgumentsForTypeVariable() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(elements.errorMessage());
    }
    behaviorTester
        .with(new Processor(features))
        .with(genericType)
        .with(testBuilder()
            .addLine("new DataType.Builder<%s>().mutateItems(items -> items.add(%s));",
                elements.type(), elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddKeepsSubstitute() {
    assumeTrue(internedStringsType != null);
    behaviorTester
        .with(new Processor(features))
        .with(internedStringsType)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.add(s))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s.get(0)).isSameAs(i);", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex0ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.add(0, %s))", elements.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(3, 0, 1, 2))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex1ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.add(1, %s))", elements.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 3, 1, 2))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex2ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.add(2, %s))", elements.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1, 3, 2))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex3ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.add(3, %s))", elements.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1, 2, 3))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(elements.errorMessage());
    }
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.add(2, %s));", elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexKeepsSubstitute() {
    assumeTrue(internedStringsType != null);
    behaviorTester
        .with(new Processor(features))
        .with(internedStringsType)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\", \"three\")")
            .addLine("    .mutateItems(items -> items.add(2, s))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s.get(2)).isSameAs(i);", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.set(1, %s))", elements.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 3, 2))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(elements.errorMessage());
    }
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.set(1, %s));", elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetAtIndexKeepsSubstitute() {
    assumeTrue(internedStringsType != null);
    behaviorTester
        .with(new Processor(features))
        .with(internedStringsType)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\", \"three\")")
            .addLine("    .mutateItems(items -> items.set(2, s))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s.get(2)).isSameAs(i);", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReadsFromUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> assertThat(items.size()).isEqualTo(3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndGetReadsFromUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> assertThat(items.get(1)).isEqualTo(%s));",
                elements.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndRemoveModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.remove(1))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 2))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2))
            .addLine("    .mutateItems(items -> items.clear())")
            .addLine("    .addItems(%s)", elements.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get(), elements.example(3))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearSubListModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1, 2, 3, 4, 5, 6))
            .addLine("    .mutateItems(items -> items.subList(1, 5).clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 5, 6))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomFunctionalInterface() {
    SourceBuilder customMutatorType = SourceBuilder.forTesting();
    for (String line : listPropertyType.toString().split("\n")) {
      if (line.contains("extends DataType_Builder")) {
        int insertLocation = line.indexOf("{") + 1;
        customMutatorType
            .addLine("%s", line.substring(0, insertLocation))
            .addLine("    public interface Mutator {")
            .addLine("      void mutate(%s<%s> list);", List.class, elements.type())
            .addLine("    }")
            .addLine("    @Override public Builder mutateItems(Mutator mutator) {")
            .addLine("      return super.mutateItems(mutator);")
            .addLine("    }")
            .addLine("%s", line.substring(insertLocation));
      } else {
        customMutatorType.addLine("%s", line);
      }
    }
    behaviorTester
        .with(new Processor(features))
        .with(customMutatorType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.add(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomGenericFunctionalInterface() {
    SourceBuilder customMutatorType = SourceBuilder.forTesting();
    for (String line : genericType.toString().split("\n")) {
      if (line.contains("extends DataType_Builder")) {
        int insertOffset = line.indexOf('{') + 1;
        customMutatorType
            .addLine("%s", line.substring(0, insertOffset))
            .addLine("    public interface Mutator<T> {")
            .addLine("      void mutate(%s<T> list);", List.class)
            .addLine("    }")
            .addLine("    @Override public Builder mutateItems(Mutator<T> mutator) {")
            .addLine("      return super.mutateItems(mutator);")
            .addLine("    }")
            .addLine("%s", line.substring(insertOffset));
      } else {
        customMutatorType.addLine("%s", line);
      }
    }
    behaviorTester
        .with(new Processor(features))
        .with(customMutatorType)
        .with(testBuilder()
            .addLine("DataType<%1$s> value = new DataType.Builder<%1$s>()", elements.type())
            .addLine("    .mutateItems(items -> items.add(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.example(0))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
