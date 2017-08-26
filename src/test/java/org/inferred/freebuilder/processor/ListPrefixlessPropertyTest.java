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
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.SourceLevel;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

/** Behavioral tests for {@code List<?>} properties. */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ListPrefixlessPropertyTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject LIST_PROPERTY_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<%s> items();", List.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final String STRING_VALIDATION_ERROR_MESSAGE = "Cannot add empty string";

  private static final JavaFileObject VALIDATED_STRINGS = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<%s> items();", List.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder addItems(String element) {")
      .addLine("      if (element.isEmpty()) {")
      .addLine("        throw new IllegalArgumentException(\"%s\");",
          STRING_VALIDATION_ERROR_MESSAGE)
      .addLine("      }")
      .addLine("      return super.addItems(element);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final String INT_VALIDATION_ERROR_MESSAGE = "Value must be non-negative";

  private static final JavaFileObject VALIDATED_INTS = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<Integer> items();", List.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder addItems(int item) {")
      .addLine("      if (item < 0) {")
      .addLine("        throw new IllegalArgumentException(\"%s\");", INT_VALIDATION_ERROR_MESSAGE)
      .addLine("      }")
      .addLine("      return super.addItems(item);")
      .addLine("    }")
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
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems((String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(\"one\", null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", \"two\").spliterator())")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator_null() {
    assumeStreamsAvailable();
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", null).spliterator());")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", \"two\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_null() {
    assumeStreamsAvailable();
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"one\", null));")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIntStream() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> items();", List.class, Integer.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 2))", IntStream.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(1, 2).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"two\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_onlyIteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(new %s(\"one\", \"two\"))", DodgySingleIterable.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  /** Throws a {@link NullPointerException} on second call to {@link #iterator()}. */
  public static class DodgySingleIterable implements Iterable<String> {
    private ImmutableList<String> values;

    public DodgySingleIterable(String... values) {
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
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clearItems()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testClear_emptyList() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .clearItems()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGetter_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.items();", List.class)
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(\"one\", \"two\");")
            .addLine("assertThat(itemsView).containsExactly(\"one\", \"two\").inOrder();")
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(\"three\", \"four\");")
            .addLine("assertThat(itemsView).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGetter_returnsUnmodifiableList() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.items();", List.class)
            .addLine("itemsView.add(\"something\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\");")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.items()).containsExactly(\"one\", \"two\").inOrder();")
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
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> items();", List.class, String.class)
            .addLine("")
            .addLine("  public abstract Builder toBuilder();")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value1 = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .buildPartial();")
            .addLine("DataType value2 = value1.toBuilder()")
            .addLine("    .addItems(\"three\")")
            .addLine("    .build();")
            .addLine("assertThat(value2.items())")
            .addLine("    .containsExactly(\"one\", \"two\", \"three\")")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clear()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"three\", \"four\").inOrder();")
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
            .addLine("  public abstract %s<%s> items();", List.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    private Builder() { }")
            .addLine("  }")
            .addLine("  public static Builder builder(String... items) {")
            .addLine("    return new Builder().addItems(items);")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = DataType.builder(\"one\", \"two\")")
            .addLine("    .clear()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
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
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testInstanceReuse() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(value).build();")
            .addLine("assertThat(value.items()).isSameAs(copy.items());")
            .build())
        .runTest();
  }

  @Test
  public void testFromReusesImmutableListInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(value).build();")
            .addLine("assertThat(copy.items()).isSameAs(value.items());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromReusesImmutableListInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder().mergeFrom(value).build();")
            .addLine("assertThat(copy.items()).isSameAs(value.items());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromEmptyListDoesNotPreventReuseOfImmutableListInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .from(value)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("    .build();")
            .addLine("assertThat(copy.items()).isSameAs(value.items());")
            .build())
        .runTest();
  }

  @Test
  public void testPropertyClearAfterMergeFromValue() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clearItems()")
            .addLine("    .build();")
            .addLine("assertThat(copy.items()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClearAfterMergeFromValue() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertThat(copy.items()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testCollectionProperty() {
    // See also https://github.com/google/FreeBuilder/issues/227
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> items();", Collection.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testCollectionProperty_withWildcard() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<? extends %s> items();", Collection.class, Number.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(2.7)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(1, 2.7).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testListOfParameters() {
    // See also
    //  - https://github.com/google/FreeBuilder/issues/178
    //  - https://github.com/google/FreeBuilder/issues/229
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType<E> {")
            .addLine("  public abstract %s<E> items();", List.class)
            .addLine("")
            .addLine("  public static class Builder<E> extends DataType_Builder<E> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType<String> value = new DataType.Builder<String>()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testListOfParameterWildcards() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType<E> {")
            .addLine("  public abstract %s<? extends E> items();", List.class)
            .addLine("")
            .addLine("  public static class Builder<E> extends DataType_Builder<E> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType<Number> value = new DataType.Builder<Number>()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(2.7)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(1, 2.7).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testImmutableListProperty() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> items();", ImmutableList.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testImmutableListProperty_withWildcard() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<? extends %s> items();",
                ImmutableList.class, Number.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(2.7)")
            .addLine("    .build();")
            .addLine("assertThat(value.items()).containsExactly(1, 2.7).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testValidation_varargsAdd() {
    thrown.expectMessage(STRING_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_STRINGS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(\"item\", \"\");", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllSpliterator() {
    assumeStreamsAvailable();
    thrown.expectMessage(STRING_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_STRINGS)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"item\", \"\").spliterator());")
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllStream() {
    assumeStreamsAvailable();
    thrown.expectMessage(STRING_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_STRINGS)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(\"item\", \"\"));")
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllIterable() {
    thrown.expectMessage(STRING_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_STRINGS)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"item\", \"\"));", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_varargsAdd() {
    thrown.expectMessage(INT_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_INTS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(3, -2);", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_addAllSpliterator() {
    assumeStreamsAvailable();
    thrown.expectMessage(INT_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_INTS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(Stream.of(3, -2).spliterator());")
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_addAllStream() {
    assumeStreamsAvailable();
    thrown.expectMessage(INT_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_INTS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(Stream.of(3, -2));")
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_addAllIntStream() {
    assumeStreamsAvailable();
    thrown.expectMessage(INT_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_INTS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(%s.of(3, -2));", IntStream.class)
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_addAllIterable() {
    thrown.expectMessage(INT_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_INTS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(%s.of(3, -2));", ImmutableList.class)
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
            .addLine("  @JsonProperty(\"stuff\") %s<%s> items();", List.class, String.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.items()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testCompilesWithoutWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testCanNameBoxedPropertyElements() {
    // See also https://github.com/google/FreeBuilder/issues/258
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> elements();", List.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addElements(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.elements()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testCanNamePrimitivePropertyElements() {
    // See also https://github.com/google/FreeBuilder/issues/258
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> elements();", List.class, Integer.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addElements(1, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.elements()).containsExactly(1, 2).inOrder();")
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
            .addLine("  public abstract %1$s<%1$s<%2$s>> items();", List.class, String.class)
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
            .addLine("  public abstract %s<T> items();", List.class)
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
            .addLine("  public abstract %1$s<%1$s<%2$s>> items();", List.class, String.class)
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
            .addLine("  public abstract %s<T> items();", List.class)
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

  private void assumeGuavaAvailable() {
    assumeTrue("Guava available", features.get(GUAVA).isAvailable());
  }

  private void assumeStreamsAvailable() {
    assumeTrue("Streams available", features.get(SOURCE_LEVEL).stream().isPresent());
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(ImmutableList.class)
        .addImport(Stream.class);
  }
}
