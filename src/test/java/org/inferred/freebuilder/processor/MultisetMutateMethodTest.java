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
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;

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

import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MultisetMutateMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_GUAVA_AND_LAMBDAS;
  }

  private static final JavaFileObject UNCHECKED_PROPERTY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperties();", Multiset.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject CHECKED_PROPERTY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperties();", Multiset.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder setCountOfProperties(int element, int count) {")
      .addLine("      %s.checkArgument(element >= 0, \"elements must be non-negative\");",
          Preconditions.class)
      .addLine("      return super.setCountOfProperties(element, count);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject INTERNED_PROPERTY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<String> getProperties();", Multiset.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder setCountOfProperties(String element, int count) {")
      .addLine("      return super.setCountOfProperties(element.intern(), count);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void mutateAndAddModifiesUnderlyingProperty_whenUnchecked() {
    behaviorTester
        .with(new Processor(features))
        .with(UNCHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5, 11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddModifiesUnderlyingProperty_whenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5, 11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(-3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAcceptsMaxIntOccurrences() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToProperties(5, Integer.MAX_VALUE - 1)")
            .addLine("    .mutateProperties(set -> set.add(5))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties().count(5)).isEqualTo(Integer.MAX_VALUE);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddRejectsMaxIntPlusOneOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("too many occurrences: 2147483648");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToProperties(5, Integer.MAX_VALUE)")
            .addLine("    .mutateProperties(set -> set.add(5));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.add(s))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.getProperties(), 0)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(11, 3))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5, 11, 11, 11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleRejectsNegativeOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("occurrences cannot be negative: -2");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(11, -2));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleAcceptsMaxIntOccurrences() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(11, Integer.MAX_VALUE))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties().count(11)).isEqualTo(Integer.MAX_VALUE);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleRejectsMaxIntPlusOneOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("too many occurrences: 2147483648");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(5, Integer.MAX_VALUE));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleReturnsOldCount() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.add(5, 3)).isEqualTo(1));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.add(-3, 3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.add(s, 3))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.getProperties(), 1)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.setCount(5, 3))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5, 5, 5);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountReturnsOldCount() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.setCount(5, 3)).isEqualTo(1));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.setCount(-3, 3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountChecksCount() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("count cannot be negative but was: -3");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.setCount(3, -3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.setCount(s, 3))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.getProperties(), 1)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountModifiesUnderlyingPropertyIfOldCountMatches() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.setCount(5, 1, 3))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5, 5, 5);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountDoesNothingIfOldCountDoesNotMatch() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.setCount(5, 2, 3))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountReturnsTrueIfOldCountMatches() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.setCount(5, 1, 3)).isTrue());")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountReturnsFalseIfOldCountDoesNotMatch() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.setCount(5, 2, 3)).isFalse());")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.setCount(-3, 0, 3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountChecksCount() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("newCount cannot be negative but was: -3");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.setCount(3, 1, -3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.setCount(s, 3))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.getProperties(), 1)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(11, 11, 12)))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(5, 11, 11, 12);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(11, -3)));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllAcceptsMaxIntOccurrences() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToProperties(5, Integer.MAX_VALUE - 2)")
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(5, 11, 5)))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties().count(5)).isEqualTo(Integer.MAX_VALUE);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllRejectsMaxIntPlusOneOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("too many occurrences: 2147483648");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToProperties(5, Integer.MAX_VALUE - 1)")
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(5, 11, 5)));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(s, \"baz\", s)))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.getProperties(), 0)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReturnsSize() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.size()).equals(1));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndContainsReturnsTrueForContainedElement() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> assertThat(set.contains(5)).isTrue());")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndIterateFindsContainedElement() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(5)")
            .addLine("    .mutateProperties(set -> {")
            .addLine("      assertThat(%s.copyOf(set.iterator())).containsExactly(5);",
                ImmutableMultiset.class)
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndRemoveModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
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
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5, 11)")
            .addLine("    .mutateProperties(set -> {")
            .addLine("      Iterator<Integer> it = set.iterator();")
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
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(5, 11)")
            .addLine("    .mutateProperties(set -> set.clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEmpty();")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addPackageImport("java.util")
        .addPackageImport("com.google.common.collect")
        .addStaticImport(Iterables.class, "get");
  }

}
