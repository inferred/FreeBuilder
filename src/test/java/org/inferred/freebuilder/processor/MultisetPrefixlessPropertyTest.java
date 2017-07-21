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

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.testing.EqualsTester;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.SourceLevel;
import org.inferred.freebuilder.processor.util.testing.BehaviorTestRunner.Shared;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.CompilationException;
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MultisetPrefixlessPropertyTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject MULTISET_PROPERTY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<%s> items();", Multiset.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject MULTISET_PRIMITIVES_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<Integer> items();", Multiset.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.items()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems((String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(\"one\", null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
        .addLine("DataType value = new DataType.Builder()")
        .addLine("    .addItems(\"one\", \"one\")")
        .addLine("    .build();")
        .addLine("assertThat(value.items()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"two\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", null));", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"one\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_iteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(new %s(\"one\", \"two\"))", DodgyStringIterable.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  /** Throws a {@link NullPointerException} the second time {@link #iterator()} is called. */
  public static class DodgyStringIterable implements Iterable<String> {
    private ImmutableList<String> values;

    public DodgyStringIterable(String... values) {
      this.values = ImmutableList.copyOf(values);
    }

    @Override
    public Iterator<String> iterator() {
      try {
        return values.iterator();
      } finally {
        values = null;
      }
    }
  }

  @Test
  public void testAddAllStream() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", \"two\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_null() {
    assumeStreamsAvailable();
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", null));")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_duplicate() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", \"one\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", \"two\").spliterator())")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator_null() {
    assumeStreamsAvailable();
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", null).spliterator());")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator_duplicate() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", \"one\").spliterator())")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems(\"two\", 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(")
            .addLine("    \"one\", \"one\", \"one\", \"two\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems((String) null, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_negativeOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems(\"two\", -2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems(\"one\", 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(")
            .addLine("    \"one\", \"one\", \"one\", \"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\", \"three\")")
            .addLine("    .setCountOfItems(\"one\", 3)")
            .addLine("    .setCountOfItems(\"two\", 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(")
            .addLine("    \"one\", \"one\", \"one\", \"two\", \"two\", \"three\");")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf_toZero() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .setCountOfItems(\"two\", 0)")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clearItems()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testDefaultEmpty_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.items()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems((Integer) null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_duplicate_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(1)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(1, null);")
            .build());
    thrown.expect(CompilationException.class);
    behaviorTester.runTest();
  }

  @Test
  public void testAddVarargs_duplicate_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
        .addLine("DataType value = new DataType.Builder()")
        .addLine("    .addItems(1, 1)")
        .addLine("    .build();")
        .addLine("assertThat(value.items()).iteratesAs(1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 2))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, null));", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_duplicate_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 1))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems(2, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 1, 1, 2, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems((Integer) null, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_negativeOccurrences_primitive() {
    thrown.expect(IllegalArgumentException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems(2, -2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_duplicate_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems(1, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 1, 1, 1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1, 2, 3)")
            .addLine("    .setCountOfItems(1, 3)")
            .addLine("    .setCountOfItems(2, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 1, 1, 2, 2, 3);")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf_toZero_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .setCountOfItems(2, 0)")
            .addLine("    .addItems(3, 4)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(1, 3, 4);")
            .build())
        .runTest();
  }

  @Test
  public void testClear_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .clearItems()")
            .addLine("    .addItems(3, 4)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(3, 4);")
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.items();", Multiset.class)
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(\"one\", \"two\");")
            .addLine("assertThat(itemsView).iteratesAs(\"one\", \"two\");")
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(\"three\", \"four\");")
            .addLine("assertThat(itemsView).iteratesAs(\"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsUnmodifiableSet() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.items();", Multiset.class)
            .addLine("itemsView.add(\"anything\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.build().items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\");")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.build().items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s> items();", Multiset.class, String.class)
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value1 = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .buildPartial();")
            .addLine("DataType value2 = value1.toBuilder()")
            .addLine("    .addItems(\"three\")")
            .addLine("    .build();")
            .addLine("assertThat(value2.items()).iteratesAs(\"one\", \"two\", \"three\");")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clear()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noBuilderFactory() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> items();", Multiset.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(String... items) {")
            .addLine("      addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder(\"hello\")")
            .addLine("    .clear()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testImmutableSetProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> items();", ImmutableMultiset.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> items();", Multiset.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setCountOfItems(String unused, int unused2) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"zero\")")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .addAllItems(%s.of(\"three\", \"four\"))", ImmutableList.class)
            .addLine("    .addCopiesToItems(\"seven\", 3)")
            .addLine("    .setCountOfItems(\"eight\", 3)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> items();", Multiset.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setCountOfItems(int unused, int unused2) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(0)")
            .addLine("    .addItems(1, 2)")
            .addLine("    .addAllItems(%s.of(3, 4))", ImmutableList.class)
            .addLine("    .addCopiesToItems(7, 3)")
            .addLine("    .setCountOfItems(8, 3)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTISET_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder().build(),")
            .addLine("        DataType.builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .addItems(\"one\", \"two\")")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .addItems(\"one\", \"two\")")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .addItems(\"one\")")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .addItems(\"one\")")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .addItems(\"one\", \"one\")")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .addItems(\"one\", \"one\")")
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testJacksonInteroperability() {
    // See also https://github.com/google/FreeBuilder/issues/68
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("import " + JsonProperty.class.getName() + ";")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public interface DataType {")
            .addLine("  @JsonProperty(\"stuff\") %s<%s> items();", Multiset.class, String.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
            .addLine("    .registerModule(new %s());", GuavaModule.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.items()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testGenericFieldCompilesWithoutHeapPollutionWarnings() {
    assumeTrue("Java 7+", features.get(SOURCE_LEVEL).compareTo(SourceLevel.JAVA_7) >= 0);
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s<%s>> items();",
                Multiset.class, List.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(")
            .addLine("    ImmutableList.of(\"one\", \"two\"),")
            .addLine("    ImmutableList.of(\"three\", \"four\"));")
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testGenericBuildableTypeCompilesWithoutHeapPollutionWarnings() {
    assumeTrue("Java 7+", features.get(SOURCE_LEVEL).compareTo(SourceLevel.JAVA_7) >= 0);
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType<T> {")
            .addLine("  public abstract %s<T> items();", Multiset.class)
            .addLine("")
            .addLine("  public static class Builder<T> extends DataType_Builder<T> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("new DataType.Builder<String>().addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testCanOverrideGenericFieldVarargsAdder() {
    // Ensure we remove the final annotation needed to apply @SafeVarargs.
    assumeTrue("Java 7+", features.get(SOURCE_LEVEL).compareTo(SourceLevel.JAVA_7) >= 0);
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s<%s>> items();",
                Multiset.class, List.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder addItems(%s<%s>... items) {",
                List.class, String.class)
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testCanOverrideGenericBuildableVarargsAdder() {
    // Ensure we remove the final annotation needed to apply @SafeVarargs.
    assumeTrue("Java 7+", features.get(SOURCE_LEVEL).compareTo(SourceLevel.JAVA_7) >= 0);
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType<T> {")
            .addLine("  public abstract %s<T> items();", Multiset.class)
            .addLine("")
            .addLine("  public static class Builder<T> extends DataType_Builder<T> {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder<T> addItems(T... items) {")
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .compiles()
        .withNoWarnings();
  }

  private void assumeStreamsAvailable() {
    assumeTrue("Streams available", features.get(SOURCE_LEVEL).stream().isPresent());
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(Arrays.class)
        .addImport(Stream.class)
        .addImport(ImmutableList.class);
  }
}
