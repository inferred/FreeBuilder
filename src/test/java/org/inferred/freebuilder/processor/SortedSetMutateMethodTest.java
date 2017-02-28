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

import com.google.common.base.Preconditions;
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

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

/**
 * Partial set of tests of {@link SortedSetPropertyFactory}. Tests common to unsorted tests can be
 * found in {@link SetPropertyTest} and {@link SetMutateMethodTest}.
 */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SortedSetMutateMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> parameters() {
    return FeatureSets.WITH_LAMBDAS;
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final FeatureSet features;

  private static final String FAILED_VALIDATION_MESSAGE = "Elements cannot start with a '0'";

  private static final JavaFileObject VALIDATED_SORTED_SET_PROPERTY_TYPE = new SourceBuilder()
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
      .addLine("")
      .addLine("    @Override")
      .addLine("    public Builder addItems(String element) {")
      .addLine("      %s.checkArgument(!element.startsWith(\"0\"), \"%s\");",
          Preconditions.class, FAILED_VALIDATION_MESSAGE)
      .addLine("      return super.addItems(element);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  public SortedSetMutateMethodTest(FeatureSet features) {
    this.features = features;
  }

  public static final Comparator<String> NATURAL_ORDER = new Comparator<String>() {
    private final Comparator<String> delegate =
        Ordering.natural().onResultOf(Integer::parseInt);

    @Override
    public int compare(String o1, String o2) {
      return delegate.compare(o1, o2);
    }

    @Override
    public String toString() {
      return "'natural order'";
    }
  };

  @Test
  public void testGetsDataInOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .mutateItems(items -> {")
            .addLine("        assertThat(items).containsExactly(\"3\", \"11\", \"222\").inOrder();")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testComparator() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"11\", \"3\", \"222\")")
            .addLine("    .mutateItems(items -> {")
            .addLine("        assertThat(items.comparator()).isEqualTo(NATURAL_ORDER);")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_contents() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"6\", \"11\", \"3\", \"222\", \"44\")")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"6\", \"44\");")
            .addLine("        assertThat(subset).containsExactly(\"6\", \"11\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testHeadSet_contents() {
    behaviorTester
    .with(new Processor(features))
    .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
    .with(testBuilder()
        .addLine("new DataType.Builder()")
        .addLine("    .setComparatorForItems(NATURAL_ORDER)")
        .addLine("    .addItems(\"6\", \"11\", \"3\", \"222\", \"44\")")
        .addLine("    .mutateItems(items -> {")
        .addLine("        Set<String> subset = items.headSet(\"44\");")
        .addLine("        assertThat(subset).containsExactly(\"3\", \"6\", \"11\");")
        .addLine("    });")
        .build())
    .runTest();
  }

  @Test
  public void testTailSet_contents() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"6\", \"11\", \"3\", \"222\", \"44\")")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.tailSet(\"6\");")
            .addLine("        assertThat(subset).containsExactly(\"6\", \"11\", \"44\", \"222\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_validatesInsertedItems() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(FAILED_VALIDATION_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"6\", \"44\");")
            .addLine("        subset.add(\"007\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_permitsInsertionAtBoundaries_defaultOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"44\", \"6\");")
            .addLine("        subset.add(\"44\");")
            .addLine("        subset.add(\"599999\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"44\", \"599999\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_permitsInsertionAtBoundaries_naturalOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"6\", \"44\");")
            .addLine("        subset.add(\"43\");")
            .addLine("        subset.add(\"6\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"6\", \"43\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_deniesInsertionBelowLowerBound_defaultOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be at least 44 (got 4399)");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"44\", \"6\");")
            .addLine("        subset.add(\"4399\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_deniesInsertionBelowLowerBound_naturalOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be at least 6 (got 5) using comparator 'natural order'");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"6\", \"44\");")
            .addLine("        subset.add(\"5\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_deniesInsertionAtUpperBound_defaultOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be less than 6 (got 6)");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"44\", \"6\");")
            .addLine("        subset.add(\"6\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_deniesInsertionAtUpperBound_naturalOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be less than 44 (got 44) using comparator 'natural order'");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.subSet(\"6\", \"44\");")
            .addLine("        subset.add(\"44\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testHeadSet_validatesInsertedItems() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(FAILED_VALIDATION_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.headSet(\"44\");")
            .addLine("        subset.add(\"007\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testHeadSet_permitsInsertionBelowUpperBound_defaultOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.headSet(\"6\");")
            .addLine("        subset.add(\"44\");")
            .addLine("        subset.add(\"599999\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"44\", \"599999\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testHeadSet_permitsInsertionBelowUpperBound_naturalOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.headSet(\"44\");")
            .addLine("        subset.add(\"43\");")
            .addLine("        subset.add(\"6\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"6\", \"43\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testHeadSet_deniesInsertionAtUpperBound_defaultOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be less than 6 (got 6)");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.headSet(\"6\");")
            .addLine("        subset.add(\"6\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testHeadSet_deniesInsertionAtUpperBound_naturalOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be less than 44 (got 44) using comparator 'natural order'");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.headSet(\"44\");")
            .addLine("        subset.add(\"44\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testTailSet_validatesInsertedItems() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(FAILED_VALIDATION_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.tailSet(\"6\");")
            .addLine("        subset.add(\"007\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testTailSet_permitsInsertionAtLowerBound_defaultOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.tailSet(\"44\");")
            .addLine("        subset.add(\"44\");")
            .addLine("        subset.add(\"599999\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"44\", \"599999\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testTailSet_permitsInsertionAtLowerBound_naturalOrder() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.tailSet(\"6\");")
            .addLine("        subset.add(\"43\");")
            .addLine("        subset.add(\"6\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"6\", \"43\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testTailSet_deniesInsertionBelowLowerBound_defaultOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be at least 44 (got 4399)");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.tailSet(\"44\");")
            .addLine("        subset.add(\"4399\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testTailSet_deniesInsertionBelowLowerBound_naturalOrder() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("element must be at least 6 (got 5) using comparator 'natural order'");
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .mutateItems(items -> {")
            .addLine("        Set<String> subset = items.tailSet(\"6\");")
            .addLine("        subset.add(\"5\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testFirstAndLastMatch() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"6\", \"11\", \"3\", \"222\", \"44\")")
            .addLine("    .mutateItems(items -> {")
            .addLine("        assertThat(items.first()).isEqualTo(\"3\");")
            .addLine("        assertThat(items.last()).isEqualTo(\"222\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void testSubSet_firstAndLastMatch() {
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_SORTED_SET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setComparatorForItems(NATURAL_ORDER)")
            .addLine("    .addItems(\"6\", \"11\", \"3\", \"222\", \"44\")")
            .addLine("    .mutateItems(items -> {")
            .addLine("        SortedSet<String> subset = items.subSet(\"6\", \"44\");")
            .addLine("        assertThat(subset.first()).isEqualTo(\"6\");")
            .addLine("        assertThat(subset.last()).isEqualTo(\"11\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addStaticImport(SortedSetMutateMethodTest.class, "NATURAL_ORDER")
        .addImport(Set.class)
        .addImport(SortedSet.class)
        .addImport(Stream.class);
  }
}
