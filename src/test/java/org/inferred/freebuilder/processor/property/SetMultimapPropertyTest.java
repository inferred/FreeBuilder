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

import static org.inferred.freebuilder.processor.property.ElementFactory.TYPES;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.testing.EqualsTester;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.FeatureSets;
import org.inferred.freebuilder.processor.MultimapSubject;
import org.inferred.freebuilder.processor.NamingConvention;
import org.inferred.freebuilder.processor.Processor;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.testing.BehaviorTester;
import org.inferred.freebuilder.processor.source.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.source.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.source.testing.TestBuilder;
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

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SetMultimapPropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "SetMultimap<{0}, {1}>, {2}, {3}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA;
    return () -> Lists
        .cartesianProduct(TYPES, TYPES, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory key;
  private final ElementFactory value;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final SourceBuilder dataType;

  public SetMultimapPropertyTest(
      ElementFactory key,
      ElementFactory value,
      NamingConvention convention,
      FeatureSet features) {
    this.key = key;
    this.value = value;
    this.convention = convention;
    this.features = features;

    dataType = SourceBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  public abstract %s<%s, %s> %s;",
            SetMultimap.class, key.type(), value.type(), convention.get("items"))
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {}")
        .addLine("}");
  }

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("%s<%s, %s> foo = value.%s;",
                SetMultimap.class, key.type(), value.type(), convention.get("items"))
            .addLine("assertThat(foo).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testPut() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPut_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .putItems((%s) null, %s);", key.type(), value.example(3))
            .build())
        .runTest();
  }

  @Test
  public void testPut_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .putItems(%s, (%s) null);", key.example(2), value.type())
            .build())
        .runTest();
  }

  @Test
  public void testPut_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s, %s.of(%s))",
                key.example(0), ImmutableList.class, value.examples(1, 2))
            .addLine("    .putAllItems(%s, %s.of(%s))",
                key.example(3), ImmutableSet.class, value.example(4))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(0), value.example(2))
            .addLine("    .and(%s, %s)", key.example(3), value.example(4))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putAllItems((%s) null, %s.of(%s));",
                key.type(), ImmutableList.class, value.examples(1, 2))
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putAllItems(%s, %s.asList(%s, (%s) null));",
                key.example(0), Arrays.class, value.example(1), value.type())
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s, %s.of(%s))",
                key.example(0), ImmutableList.class, value.examples(1, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_iteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s, new %s<%s>(%s))",
                key.example(0), DodgyIterable.class, value.type(), value.examples(1, 2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(0), value.example(2))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  /** Throws a {@link NullPointerException} the second time {@link #iterator()} is called. */
  public static class DodgyIterable<T> implements Iterable<T> {
    private ImmutableList<T> values;

    @SafeVarargs
    public DodgyIterable(T... values) {
      this.values = ImmutableList.copyOf(values);
    }

    @Override
    public Iterator<T> iterator() {
      try {
        return values.iterator();
      } finally {
        values = null;
      }
    }
  }

  @Test
  public void testPutAllMultimap() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        %s, %s,", key.example(0), value.example(1))
            .addLine("        %s, %s,", key.example(0), value.example(2))
            .addLine("        %s, %s))", key.example(3), value.example(4))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(0), value.example(2))
            .addLine("    .and(%s, %s)", key.example(3), value.example(4))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s<%2$s, %3$s> values = %1$s.create();",
                LinkedHashMultimap.class, key.type(), value.type())
            .addLine("values.put((%s) null, %s);", key.type(), value.example(0))
            .addLine("new DataType.Builder().putAllItems(values);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s<%2$s, %3$s> values = %1$s.create();",
                LinkedHashMultimap.class, key.type(), value.type())
            .addLine("values.put(%s, (%s) null);", key.example(0), value.type())
            .addLine("new DataType.Builder().putAllItems(values);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        %s, %s,", key.example(0), value.example(1))
            .addLine("        %s, %s,", key.example(0), value.example(1))
            .addLine("        %s, %s))", key.example(2), value.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        %s, %s,", key.example(0), value.example(1))
            .addLine("        %s, %s,", key.example(0), value.example(2))
            .addLine("        %s, %s))", key.example(3), value.example(2))
            .addLine("    .removeItems(%s, %s)", key.example(0), value.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(3), value.example(2))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_doesNotThrowIfEntryNotPresent() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        %s, %s,", key.example(0), value.example(1))
            .addLine("        %s, %s))", key.example(3), value.example(2))
            .addLine("    .removeItems(%s, %s)", key.example(0), value.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(3), value.example(2))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .removeItems((%s) null, %s);", key.type(), value.example(1))
            .build())
        .runTest();
  }

  @Test
  public void testRemove_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .removeItems(%s, (%s) null);", key.example(0), value.type())
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        %s, %s,", key.example(0), value.example(1))
            .addLine("        %s, %s,", key.example(0), value.example(2))
            .addLine("        %s, %s))", key.example(3), value.example(2))
            .addLine("    .removeAllItems(%s)", key.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(3), value.example(2))
            .addLine("    .andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll_doesNotThrowIfKeyNotPresent() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        %s, %s))", key.example(0), value.example(1))
            .addLine("    .removeAllItems(%s)", key.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .removeAllItems((%s) null);", key.type())
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .clearItems()")
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(4))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(2), value.example(3))
            .addLine("    .and(%s, %s)", key.example(0), value.example(4))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<%s, %s> itemsView = builder.%s;",
                SetMultimap.class, key.type(), value.type(), convention.get("items"))
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(%s, %s);", key.example(0), value.example(1))
            .addLine("assertThat(itemsView).contains(%s, %s).andNothingElse();",
                key.example(0), value.example(1))
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(%s, %s);", key.example(2), value.example(3))
            .addLine("assertThat(itemsView).contains(%s, %s).andNothingElse();",
                key.example(2), value.example(3))
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsUnmodifiableSetMultimap() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<%s, %s> itemsView = builder.%s;",
                SetMultimap.class, key.type(), value.type(), convention.get("items"))
            .addLine("itemsView.put(%s, %s);", key.example(0), value.example(1))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .build();")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.build().%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(2), value.example(3))
            .addLine("    .and(%s, %s)", key.example(0), value.example(1))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .putItems(%s, %s);", key.example(0), value.example(1))
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.build().%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(2), value.example(3))
            .addLine("    .and(%s, %s)", key.example(0), value.example(1))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;",
                SetMultimap.class, key.type(), value.type(), convention.get())
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value1 = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .buildPartial();")
            .addLine("DataType value2 = value1.toBuilder()")
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value2.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .clear()")
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(4))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(2), value.example(3))
            .addLine("    .and(%s, %s)", key.example(0), value.example(4))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testImmutableSetMultimapProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;",
                ImmutableSetMultimap.class, key.type(), value.type(), convention.get("items"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingPut() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;",
                SetMultimap.class, key.type(), value.type(), convention.get("items"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(%s unused, %s unused2) {",
                key.unwrappedType(), value.unwrappedType())
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)",
                key.example(0), value.example(0))
            .addLine("    .putAllItems(%s, %s.of(%s))",
                key.example(1), ImmutableList.class, value.examples(1, 2))
            .addLine("    .putAllItems(%s.of(%s, %s))",
                ImmutableMultimap.class, key.example(3), value.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().build(),")
            .addLine("        new DataType.Builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("            .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("            .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("            .putItems(%s, %s)", key.example(0), value.example(3))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("            .putItems(%s, %s)", key.example(0), value.example(3))
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
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public interface DataType {")
            .addLine("  @%s(\"stuff\")", JsonProperty.class)
            .addLine("  %s<%s, %s> %s;",
                SetMultimap.class, key.type(), value.type(), convention.get("items"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .putItems(%s, %s)", key.example(2), value.example(3))
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
            .addLine("    .registerModule(new %s());", GuavaModule.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.%s)", convention.get("items"))
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addStaticImport(MultimapSubject.class, "assertThat")
        .addImport("com.example.DataType");
  }
}
