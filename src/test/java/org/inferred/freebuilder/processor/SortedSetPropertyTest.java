/*
 * Copyright 2016 Google Inc. All rights reserved.
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

import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.Ordering;

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
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

/**
 * Partial set of tests of {@link SortedSetPropertyFactory}. Tests specific to the mutateX methods
 * generated in Java 8+ are in {@link SortedSetMutateMethodTest}. Tests common to unsorted tests can
 * be found in {@link SetPropertyTest} and {@link SetMutateMethodTest}.
 */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SortedSetPropertyTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> parameters() {
    return FeatureSets.ALL;
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final FeatureSet features;

  private static final JavaFileObject SORTED_SET_PROPERTY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String> items();", SortedSet.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override")
      .addLine("    public Builder setComparatorForItems(%s<? super String> comparator) {",
          Comparator.class)
      .addLine("      return super.setComparatorForItems(comparator);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  public SortedSetPropertyTest(FeatureSet features) {
    this.features = features;
  }

  public static final Comparator<String> NATURAL_ORDER =
      Ordering.natural().onResultOf(Integer::parseInt);
  public static final Comparator<String> EXPLICIT_DEFAULT_ORDER = String::compareTo;

  @Test
  public void testDefaultOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"11\", \"222\", \"3\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testNullOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(null)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"11\", \"222\", \"3\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testReverseOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(%s.reverseOrder())", Collections.class)
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"3\", \"222\", \"11\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testNaturalOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"3\", \"11\", \"222\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\");")
            .addLine("assertThat(builder.items())")
            .addLine("    .containsExactly(\"3\", \"11\", \"222\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGetterComparator() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items().comparator()).isEqualTo(NATURAL_ORDER);")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetterComparator() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\");")
            .addLine("assertThat(builder.items().comparator()).isEqualTo(NATURAL_ORDER);")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromReusesImmutableSetInstanceWhenComparatorsBothNull() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(null)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .setComparatorForItems(null)")
            .addLine("    .mergeFrom(value)")
            .addLine("    .build();")
            .addLine("assertThat(copy.items()).isSameAs(value.items());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromReusesImmutableSetInstanceWhenComparatorUnset() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .mergeFrom(value)")
            .addLine("    .build();")
            .addLine("assertThat(copy.items()).isSameAs(value.items());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromReusesImmutableSetInstanceWhenComparatorsMatch() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mergeFrom(value)")
            .addLine("    .build();")
            .addLine("assertThat(copy.items()).isSameAs(value.items());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromDoesNotReuseImmutableSetInstanceWhenComparatorsDoNotMatch() {
    behaviorTester
        .with(new Processor(features))
        .with(SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(null)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .setComparatorForItems(EXPLICIT_DEFAULT_ORDER)")
            .addLine("    .mergeFrom(value)")
            .addLine("    .build();")
            .addLine("assertThat(copy.items()).isEqualTo(value.items());")
            .addLine("assertThat(copy.items()).isNotSameAs(value.items());")
            .build())
        .runTest();
  }

  private void assumeGuavaAvailable() {
    assumeTrue("Guava available", features.get(GUAVA).isAvailable());
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addStaticImport(SortedSetPropertyTest.class, "EXPLICIT_DEFAULT_ORDER")
        .addStaticImport(SortedSetPropertyTest.class, "NATURAL_ORDER")
        .addImport(Stream.class);
  }
}
