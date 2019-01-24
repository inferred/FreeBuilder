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
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.testing.EqualsTester;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.FeatureSets;
import org.inferred.freebuilder.processor.NamingConvention;
import org.inferred.freebuilder.processor.Processor;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.testing.BehaviorTester;
import org.inferred.freebuilder.processor.source.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.source.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.source.testing.TestBuilder;
import org.junit.Before;
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
import java.util.stream.Stream;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MultisetPropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "Multiset<{0}>, {1}, {2}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA;
    return () -> Lists
        .cartesianProduct(TYPES, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory element;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final SourceBuilder dataType;

  public MultisetPropertyTest(
      ElementFactory element,
      NamingConvention convention,
      FeatureSet features) {
    this.element = element;
    this.convention = convention;
    this.features = features;
    dataType = SourceBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s> %s;",
            Multiset.class, element.type(), convention.get("items"))
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {}")
        .addLine("}");
  }

  @Before
  public void setup() {
    behaviorTester.withPermittedPackage(element.type().getPackage());
  }

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.example(0))
            .addLine("    .addItems(%s)", element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", element.example(0))
            .addLine("    .addItems((%s) null);", element.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.example(0))
            .addLine("    .addItems(%s)", element.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0))
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(%s, (%s) null);",
                element.example(0), element.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.examples(0, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s))", ImmutableList.class, element.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s, null));", ImmutableList.class, element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s))", ImmutableList.class, element.examples(0, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_iteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(new %s<%s>(%s))",
                DodgyIterable.class, element.type(), element.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
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
  public void testAddAllStream() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s))", Stream.class, element.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s, null));", Stream.class, element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s))", Stream.class, element.examples(0, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s).spliterator())",
                 Stream.class, element.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s, null).spliterator());",
                Stream.class, element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllSpliterator_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s).spliterator())",
                 Stream.class, element.examples(0, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0))
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToItems(%s, 3)", element.example(0))
            .addLine("    .addCopiesToItems(%s, 2)", element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToItems(%s, 3)", element.example(0))
            .addLine("    .addCopiesToItems((%s) null, 2);", element.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_negativeOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("count cannot be negative but was: -2");
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToItems(%s, 3)", element.example(0))
            .addLine("    .addCopiesToItems(%s, -2);", element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToItems(%s, 3)", element.example(0))
            .addLine("    .addCopiesToItems(%s, 2)", element.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0, 0, 0, 0))
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.examples(0, 2))
            .addLine("    .setCountOfItems(%s, 3)", element.example(0))
            .addLine("    .setCountOfItems(%s, 2)", element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 0, 0, 2, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf_toZero() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.examples(0, 1))
            .addLine("    .setCountOfItems(%s, 0)", element.example(1))
            .addLine("    .addItems(%s)", element.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 2, 3))
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
            .addLine("    .addItems(%s)", element.examples(0, 1))
            .addLine("    .clearItems()")
            .addLine("    .addItems(%s)", element.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(2, 3))
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
            .addLine("%s<%s> itemsView = builder.%s;",
                Multiset.class, element.type(), convention.get("items"))
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(%s);", element.examples(0, 1))
            .addLine("assertThat(itemsView).iteratesAs(%s);", element.examples(0, 1))
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(%s);", element.examples(2, 3))
            .addLine("assertThat(itemsView).iteratesAs(%s);", element.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsUnmodifiableSet() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<%s> itemsView = builder.%s;",
                Multiset.class, element.type(), convention.get("items"))
            .addLine("itemsView.add(%s);", element.example(0))
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
            .addLine("    .addItems(%s)", element.examples(0, 1))
            .addLine("    .build();")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.build().%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
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
            .addLine("    .addItems(%s);", element.examples(0, 1))
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.build().%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
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
            .addLine("  %s<%s> %s;", Multiset.class, element.type(), convention.get())
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value1 = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.examples(0, 1))
            .addLine("    .buildPartial();")
            .addLine("DataType value2 = value1.toBuilder()")
            .addLine("    .addItems(%s)", element.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value2.%s).iteratesAs(%s);",
                convention.get(), element.examples(0, 1, 2))
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
            .addLine("    .addItems(%s)", element.examples(0, 1))
            .addLine("    .clear()")
            .addLine("    .addItems(%s)", element.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noBuilderFactory() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> %s;",
                Multiset.class, element.type(), convention.get("items"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(%s... items) {", element.unwrappedType())
            .addLine("      addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder(%s)", element.example(0))
            .addLine("    .clear()")
            .addLine("    .addItems(%s)", element.examples(1, 2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(1, 2))
            .build())
        .runTest();
  }

  @Test
  public void testImmutableSetProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s> %s;",
                ImmutableMultiset.class, element.type(), convention.get("items"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.example(0))
            .addLine("    .addItems(%s)", element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testOverridingSetCount() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> %s;",
                Multiset.class, element.type(), convention.get("items"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setCountOfItems(%s unused, int unused2) {",
                element.unwrappedType())
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.example(0))
            .addLine("    .addItems(%s)", element.examples(1))
            .addLine("    .addAllItems(%s.of(%s))", ImmutableList.class, element.examples(2, 3))
            .addLine("    .addCopiesToItems(%s, 3)", element.example(4))
            .addLine("    .setCountOfItems(%s, 3)", element.example(5))
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
            .addLine("        new DataType.Builder().addItems(%s).build(),", element.examples(0, 1))
            .addLine("        new DataType.Builder().addItems(%s).build())", element.examples(0, 1))
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().addItems(%s).build(),", element.examples(0))
            .addLine("        new DataType.Builder().addItems(%s).build())", element.examples(0))
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().addItems(%s).build(),", element.examples(0, 0))
            .addLine("        new DataType.Builder().addItems(%s).build())", element.examples(0, 0))
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
            .addLine("import " + JsonProperty.class.getName() + ";")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public interface DataType {")
            .addLine("  @JsonProperty(\"stuff\") %s<%s> %s;",
                Multiset.class, element.type(), convention.get("items"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", element.example(0))
            .addLine("    .addItems(%s)", element.example(1))
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
            .addLine("    .registerModule(new %s());", GuavaModule.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.%s).iteratesAs(%s);",
                convention.get("items"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testGenericFieldCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s<%s>> %s;",
                Multiset.class, List.class, element.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(")
            .addLine("    %s.asList(%s),", Arrays.class, element.examples(0, 1))
            .addLine("    %s.asList(%s));", Arrays.class, element.examples(2, 3))
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testGenericBuildableTypeCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType<T> {")
            .addLine("  %s<T> %s;", Multiset.class, convention.get())
            .addLine("")
            .addLine("  class Builder<T> extends DataType_Builder<T> {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("new DataType.Builder<%s>().addItems(%s)",
                element.type(), element.examples(0, 1))
            .addLine("    .build();")
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testCanOverrideGenericFieldVarargsAdder() {
    // Ensure we remove the final annotation needed to apply @SafeVarargs.
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s<%s>> %s;",
                Multiset.class, List.class, element.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder addItems(%s<%s>... items) {",
                List.class, element.type())
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testCanOverrideGenericBuildableVarargsAdder() {
    // Ensure we remove the final annotation needed to apply @SafeVarargs.
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType<T> {")
            .addLine("  %s<T> %s;", Multiset.class, convention.get())
            .addLine("")
            .addLine("  class Builder<T> extends DataType_Builder<T> {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder<T> addItems(T... items) {")
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .compiles()
        .withNoWarnings();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
