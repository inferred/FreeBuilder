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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTestRunner.Shared;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SetMutateMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_LAMBDAS;
  }

  private static final JavaFileObject UNCHECKED_SET_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperties();", Set.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject CHECKED_SET_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperties();", Set.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder addProperties(int element) {")
      .addLine("      %s.checkArgument(element >= 0, \"elements must be non-negative\");",
          Preconditions.class)
      .addLine("      return super.addProperties(element);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void mutateAndAddModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(UNCHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5, 11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReturnsSize() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.size()).equals(1));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndContainsReturnsTrueForContainedElement() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.contains(5)).isTrue());")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndIterateFindsContainedElement() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> {")
            .addLine("      assertThat(%s.copyOf(set.iterator())).containsExactly(5);",
                ImmutableSet.class)
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndRemoveModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(5, 11)")
            .addLine("    .mutateProperties(set -> set.remove(5))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndCallRemoveOnIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(5, 11)")
            .addLine("    .mutateProperties(set -> {")
            .addLine("      %s<Integer> it = set.iterator();", Iterator.class)
            .addLine("      while (it.hasNext()) {")
            .addLine("        if (it.next() == 5) {")
            .addLine("          it.remove();")
            .addLine("        }")
            .addLine("      }")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(5, 11)")
            .addLine("    .mutateProperties(set -> set.clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddDelegatesToAddMethodForValidation() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(-3));")
            .build())
        .runTest();
  }

}
