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

import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SetMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}<Integer>, {1}, {2}")
  public static Iterable<Object[]> featureSets() {
    List<SetType> sets = Arrays.asList(SetType.values());
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_LAMBDAS;
    return () -> Lists
        .cartesianProduct(sets, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final SetType set;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject uncheckedSetProperty;
  private final JavaFileObject checkedSetProperty;

  public SetMutateMethodTest(SetType set, NamingConvention convention, FeatureSet features) {
    this.set = set;
    this.convention = convention;
    this.features = features;

    uncheckedSetProperty = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<Integer> %s;", set.type(), convention.getter())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("}")
        .build();

    checkedSetProperty = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<Integer> %s;", set.type(), convention.getter())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {")
        .addLine("    @Override public Builder addItems(int element) {")
        .addLine("      if (element < 0) {")
        .addLine("        throw new IllegalArgumentException(\"elements must be non-negative\");")
        .addLine("      }")
        .addLine("      return super.addItems(element);")
        .addLine("    }")
        .addLine("  }")
        .addLine("}")
        .build();
  }

  @Test
  public void mutateAndAddModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(uncheckedSetProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(11)")
            .addLine("    .mutateItems(items -> items.add(5))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), set.intsInOrder(11, 5))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReturnsSize() {
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(5)")
            .addLine("    .mutateItems(items -> assertThat(items.size()).equals(1));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndContainsReturnsTrueForContainedElement() {
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(5)")
            .addLine("    .mutateItems(items -> assertThat(items.contains(5)).isTrue());")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndIterateFindsContainedElement() {
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(5)")
            .addLine("    .mutateItems(items -> {")
            .addLine("      assertThat(%s.copyOf(items.iterator())).containsExactly(5);",
                ImmutableSet.class)
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndRemoveModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(5, 11)")
            .addLine("    .mutateItems(items -> items.remove(5))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(11);", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndCallRemoveOnIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(5, 11)")
            .addLine("    .mutateItems(items -> {")
            .addLine("      %s<Integer> it = items.iterator();", Iterator.class)
            .addLine("      while (it.hasNext()) {")
            .addLine("        if (it.next() == 5) {")
            .addLine("          it.remove();")
            .addLine("        }")
            .addLine("      }")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(11);", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(5, 11)")
            .addLine("    .mutateItems(items -> items.clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddDelegatesToAddMethodForValidation() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(5)")
            .addLine("    .mutateItems(items -> items.add(-3));")
            .build())
        .runTest();
  }

  @Test
  public void modifyAndMutateModifiesUnderlyingProperty() {
    assumeTrue(features.get(FUNCTION_PACKAGE).consumer().isPresent());
    behaviorTester
        .with(new Processor(features))
        .with(checkedSetProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().addItems(1, 2).build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .mutateItems(items -> items.remove(1))")
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).containsExactly(2);", convention.getter())
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
