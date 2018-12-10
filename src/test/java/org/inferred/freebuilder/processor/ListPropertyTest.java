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

import static org.inferred.freebuilder.processor.ElementFactory.INTEGERS;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.junit.Assume.assumeTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;

import org.inferred.freebuilder.FreeBuilder;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

/** Behavioral tests for {@code List<?>} properties. */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ListPropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "List<{0}>, {1}, {2}")
  public static Iterable<Object[]> parameters() {
    List<ElementFactory> elements = Arrays.asList(ElementFactory.values());
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(elements, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory elements;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject listPropertyType;
  private final JavaFileObject validatedType;

  public ListPropertyTest(
      ElementFactory elements,
      NamingConvention convention,
      FeatureSet features) {
    this.elements = elements;
    this.convention = convention;
    this.features = features;

    listPropertyType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract %s<%s> %s;", List.class, elements.type(), convention.get())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("  public abstract Builder toBuilder();")
        .addLine("}")
        .build();

    validatedType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract %s<%s> %s;", List.class, elements.type(), convention.get())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {")
        .addLine("    @Override public Builder addItems(%s element) {", elements.unwrappedType())
        .addLine("      if (!(%s)) {", elements.validation())
        .addLine("        throw new IllegalArgumentException(\"%s\");", elements.errorMessage())
        .addLine("      }")
        .addLine("      return super.addItems(element);")
        .addLine("    }")
        .addLine("  }")
        .addLine("}")
        .build();
  }

  @Before
  public void setUp() {
    behaviorTester.withPermittedPackage(elements.type().getPackage());
  }

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems((%s) null);", elements.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s, (%s) null);", elements.example(0), elements.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(%s).spliterator())", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(%s, (%s) null).spliterator());",
                elements.example(0), elements.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(%s))", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(%s, (%s) null));",
                elements.example(0), elements.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIntStream() {
    assumeTrue(elements == INTEGERS);
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 2))", IntStream.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(1, 2).inOrder();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s))", ImmutableList.class, elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_onlyIteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(new %s<%s>(%s))",
                DodgySingleIterable.class, elements.type(), elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  /** Throws a {@link NullPointerException} on second call to {@link #iterator()}. */
  public static class DodgySingleIterable<T> implements Iterable<T> {
    private ImmutableList<T> values;

    @SafeVarargs
    public DodgySingleIterable(T... values) {
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
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .clearItems()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testClear_emptyList() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .clearItems()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testGetter_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<%s> itemsView = builder.%s;",
                List.class, elements.type(), convention.get())
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(%s);", elements.examples(0, 1))
            .addLine("assertThat(itemsView).containsExactly(%s).inOrder();",
                elements.examples(0, 1))
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(%s);", elements.examples(2, 3))
            .addLine("assertThat(itemsView).containsExactly(%s).inOrder();",
                elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testGetter_returnsUnmodifiableList() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<%s> itemsView = builder.%s;",
                List.class, elements.type(), convention.get())
            .addLine("itemsView.add(%s);", elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .addItems(%s);", elements.examples(0, 1))
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value1 = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .buildPartial();")
            .addLine("DataType value2 = value1.toBuilder()")
            .addLine("    .addItems(%s)", elements.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value2.%s)", convention.get())
            .addLine("    .containsExactly(%s)", elements.examples(0, 1, 2))
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .clear()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(2, 3))
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
            .addLine("  public abstract %s<%s> %s;",
                List.class, elements.type(), convention.get())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    private Builder() { }")
            .addLine("  }")
            .addLine("  public static Builder builder(%s... items) {", elements.unwrappedType())
            .addLine("    return new Builder().addItems(items);")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = DataType.builder(%s)", elements.examples(0, 1))
            .addLine("    .clear()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().build(),")
            .addLine("        new DataType.Builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.examples(0, 1))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.examples(0, 1))
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.example(0))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.example(0))
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
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(value).build();")
            .addLine("assertThat(value.%1$s).isSameAs(copy.%1$s);", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testFromReusesImmutableListInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(value).build();")
            .addLine("assertThat(copy.%1$s).isSameAs(value.%1$s);", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromReusesImmutableListInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder().mergeFrom(value).build();")
            .addLine("assertThat(copy.%1$s).isSameAs(value.%1$s);", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromEmptyListDoesNotPreventReuseOfImmutableListInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .from(value)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("    .build();")
            .addLine("assertThat(copy.%1$s).isSameAs(value.%1$s);", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testPropertyClearAfterMergeFromValue() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clearItems()")
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClearAfterMergeFromValue() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).isEmpty();", convention.get())
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
            .addLine("  public abstract %s<%s> %s;",
                Collection.class, elements.type(), convention.get())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
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
            .addLine("  public abstract %s<? extends %s> %s;",
                Collection.class, elements.supertype(), convention.get())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.supertypeExample())
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s, %s).inOrder();",
                convention.get(), elements.example(0), elements.supertypeExample())
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
            .addLine("  public abstract %s<E> %s;", List.class, convention.get())
            .addLine("")
            .addLine("  public static class Builder<E> extends DataType_Builder<E> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType<%1$s> value = new DataType.Builder<%1$s>()", elements.type())
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
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
            .addLine("  public abstract %s<? extends E> %s;", List.class, convention.get())
            .addLine("")
            .addLine("  public static class Builder<E> extends DataType_Builder<E> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType<%1$s> value = new DataType.Builder<%1$s>()", elements.supertype())
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.supertypeExample())
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s, %s).inOrder();",
                convention.get(), elements.example(0), elements.supertypeExample())
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
            .addLine("  public abstract %s<%s> %s;",
                ImmutableList.class, elements.type(), convention.get())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
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
            .addLine("  public abstract %s<? extends %s> %s;",
                ImmutableList.class, elements.supertype(), convention.get())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.supertypeExample())
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s, %s).inOrder();",
                convention.get(), elements.example(0), elements.supertypeExample())
            .build())
        .runTest();
  }

  @Test
  public void testValidation_varargsAdd() {
    thrown.expectMessage(elements.errorMessage());
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(%s, %s);",
                elements.example(0), elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllSpliterator() {
    thrown.expectMessage(elements.errorMessage());
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(Stream.of(%s, %s).spliterator());",
                elements.example(0), elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllStream() {
    thrown.expectMessage(elements.errorMessage());
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(Stream.of(%s, %s));",
                elements.example(0), elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllIterable() {
    thrown.expectMessage(elements.errorMessage());
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(%s.of(%s, %s));",
                ImmutableList.class, elements.example(0), elements.invalidExample())
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
            .addLine("  @JsonProperty(\"stuff\") %s<%s> %s;",
                List.class, elements.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.%s).containsExactly(%s).inOrder();",
                convention.get(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testCompilesWithoutWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(listPropertyType)
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testCanNamePropertyElements() {
    // See also https://github.com/google/FreeBuilder/issues/258
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> %s;",
                List.class, elements.type(), convention.get("elements"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addElements(%s)", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.get("elements"), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testGenericFieldCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %1$s<%1$s<%2$s>> %3$s;",
                List.class, elements.type(), convention.get())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(")
            .addLine("    ImmutableList.of(%s),", elements.examples(0, 1))
            .addLine("    ImmutableList.of(%s));", elements.examples(2, 3))
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testGenericBuildableTypeCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType<T> {")
            .addLine("  public abstract %s<T> %s;", List.class, convention.get())
            .addLine("")
            .addLine("  public static class Builder<T> extends DataType_Builder<T> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("new DataType.Builder<%s>().addItems(%s).build();",
                elements.type(), elements.examples(0, 1))
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testCanOverrideGenericFieldVarargsAdder() {
    // Ensure we remove the final annotation needed to apply @SafeVarargs.
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %1$s<%1$s<%2$s>> %3$s;",
                List.class, elements.type(), convention.get())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder addItems(%s<%s>... items) {",
                List.class, elements.type())
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
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType<T> {")
            .addLine("  public abstract %s<T> %s;", List.class, convention.get())
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

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(ImmutableList.class)
        .addImport(Stream.class);
  }
}
