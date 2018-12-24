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
package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.ElementFactory.TYPES;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SetMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}<{1}>, checked={2}, {3}, {4}")
  public static Iterable<Object[]> featureSets() {
    List<SetType> sets = Arrays.asList(SetType.values());
    List<Boolean> checked = ImmutableList.of(false, true);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_LAMBDAS;
    return () -> Lists
        .cartesianProduct(sets, TYPES, checked, conventions, features)
        .stream()
        .filter(list -> list.get(0) != SetType.SORTED_SET
            || list.get(1) != ElementFactory.NON_COMPARABLES)
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final SetType set;
  private final ElementFactory elements;
  private final boolean checked;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject setPropertyType;

  public SetMutateMethodTest(
      SetType set,
      ElementFactory elements,
      boolean checked,
      NamingConvention convention,
      FeatureSet features) {
    this.set = set;
    this.elements = elements;
    this.checked = checked;
    this.convention = convention;
    this.features = features;

    SourceBuilder setPropertyTypeBuilder = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s> %s;", set.type(), elements.type(), convention.get())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {");
    if (checked) {
      setPropertyTypeBuilder
          .addLine("    @Override public Builder addItems(%s element) {", elements.unwrappedType())
          .addLine("      if (!(%s)) {", elements.validation())
          .addLine("        throw new IllegalArgumentException(\"%s\");", elements.errorMessage())
          .addLine("      }")
          .addLine("      return super.addItems(element);")
          .addLine("    }");
    }
    setPropertyType = setPropertyTypeBuilder
        .addLine("  }")
        .addLine("}")
        .build();
  }

  @Before
  public void before() {
    behaviorTester
        .with(new Processor(features))
        .withPermittedPackage(NonComparable.class.getPackage());
  }

  @Test
  public void mutateAndAddModifiesUnderlyingProperty() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .mutateItems(items -> items.add(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(set.inOrder(1, 0)))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReturnsSize() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .mutateItems(items -> assertThat(items.size()).equals(1));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndContainsReturnsTrueForContainedElement() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .mutateItems(items -> assertThat(items.contains(%s)).isTrue());",
                elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndContainsReturnsFalseForNonContainedElement() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .mutateItems(items -> assertThat(items.contains(%s)).isFalse());",
                elements.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndIterateFindsContainedElement() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(2, 0, 1))
            .addLine("    .mutateItems(items -> {")
            .addLine("      assertThat(%s.copyOf(items.iterator())).containsExactly(%s).inOrder();",
                ImmutableSet.class, elements.examples(set.inOrder(2, 0, 1)))
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndRemoveModifiesUnderlyingProperty() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .mutateItems(items -> items.remove(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get(), elements.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndCallRemoveOnIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .mutateItems(items -> {")
            .addLine("      %s<%s> it = items.iterator();", Iterator.class, elements.type())
            .addLine("      while (it.hasNext()) {")
            .addLine("        if (%s.equals(it.next(), %s)) {", Objects.class, elements.example(0))
            .addLine("          it.remove();")
            .addLine("        }")
            .addLine("      }")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);", convention.get(),
                elements.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearModifiesUnderlyingProperty() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .mutateItems(items -> items.clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddDelegatesToAddMethodForValidation() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(elements.errorMessage());
    }
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .mutateItems(items -> items.add(%s));", elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void modifyAndMutateModifiesUnderlyingProperty() {
    behaviorTester
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().addItems(%s).build();",
                elements.examples(0, 1))
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .mutateItems(items -> items.remove(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).containsExactly(%s);",
                convention.get(), elements.example(1))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomFunctionalInterface() throws IOException {
    SourceBuilder customMutatorType = new SourceBuilder();
    for (String line : setPropertyType.getCharContent(true).toString().split("\n")) {
      customMutatorType.addLine("%s", line);
      if (line.contains("extends DataType_Builder")) {
        customMutatorType
            .addLine("    public interface Mutator {")
            .addLine("      void mutate(%s<%s> set);", set.type(), elements.type())
            .addLine("    }")
            .addLine("    @Override public Builder mutateItems(Mutator mutator) {")
            .addLine("      return super.mutateItems(mutator);")
            .addLine("    }");
      }
    }

    behaviorTester
        .with(customMutatorType.build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().addItems(%s).build();",
                elements.examples(0, 1))
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .mutateItems(items -> items.remove(%s))", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).containsExactly(%s);",
                convention.get(), elements.example(1))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
