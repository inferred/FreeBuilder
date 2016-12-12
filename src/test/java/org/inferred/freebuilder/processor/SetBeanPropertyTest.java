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

import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.junit.Assume.assumeTrue;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.testing.EqualsTester;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTestRunner.Shared;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.CompilationException;
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
public class SetBeanPropertyTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject SET_PROPERTY_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<String> getItems();", Set.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject SET_PRIMITIVES_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getItems();", Set.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final String STRING_VALIDATION_ERROR_MESSAGE = "Cannot add empty string";

  private static final JavaFileObject VALIDATED_STRINGS = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<%s> getItems();", Set.class, String.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder addItems(String unused) {")
      .addLine("      %s.checkArgument(!unused.isEmpty(), \"%s\");",
          Preconditions.class, STRING_VALIDATION_ERROR_MESSAGE)
      .addLine("      return super.addItems(unused);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final String INT_VALIDATION_ERROR_MESSAGE = "Items must be non-negative";

  private static final JavaFileObject VALIDATED_INTS = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getItems();", Set.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder addItems(int element) {")
      .addLine("      %s.checkArgument(element >= 0, \"%s\");",
          Preconditions.class, INT_VALIDATION_ERROR_MESSAGE)
      .addLine("      return super.addItems(element);")
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
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(\"one\", null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"two\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"one\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_iteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(new %s(\"one\", \"two\"))", DodgyStringIterable.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
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
  public void testRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .removeItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .removeItems((String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_missingElement() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .removeItems(\"three\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testClear_noElements() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .clearItems()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testClear_twoElements() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clearItems()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testDefaultEmpty_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(1, 2).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
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
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(1)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(1).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(1, 2).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
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
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
        .addLine("DataType value = new DataType.Builder()")
        .addLine("    .addItems(1, 1)")
        .addLine("    .build();")
        .addLine("assertThat(value.getItems()).containsExactly(1).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 2))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(1, 2).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
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
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 1))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(1).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .removeItems(1)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(2);")
            .build())
        .runTest();
  }

  @Test
  public void testClear_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PRIMITIVES_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .clearItems()")
            .addLine("    .addItems(3, 4)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(3, 4).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.getItems();", Set.class)
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
  public void testGet_returnsUnmodifiableSet() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.getItems();", Set.class)
            .addLine("itemsView.add(\"anything\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.build().getItems())")
            .addLine("    .containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\");")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.build().getItems())")
            .addLine("    .containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clear()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"three\", \"four\").inOrder();")
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
            .addLine("  public abstract %s<%s> getItems();", Set.class, String.class)
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
            .addLine("assertThat(value.getItems()).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testImmutableSetProperty() {
    assumeTrue("Guava available", features.get(GUAVA).isAvailable());
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<String> getItems();", ImmutableSet.class)
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
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testValidation_varargsAdd() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(STRING_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_STRINGS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(\"one\", \"\");")
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAll() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(STRING_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_STRINGS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(%s.of(\"three\", \"\"));",
                ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_varargsAdd() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(INT_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_INTS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(1, -2);")
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_addAll() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(INT_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_INTS)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(%s.of(3, -4));",
                ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().build(),")
            .addLine("        new DataType.Builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(\"one\", \"two\")")
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(\"one\", \"two\")")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(\"one\")")
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(\"one\")")
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
            .addLine("  @JsonProperty(\"stuff\") %s<%s> getItems();", Set.class, String.class)
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
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testFromReusesImmutableSetInstance() {
    assumeTrue(features.get(GUAVA).isAvailable());
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(value).build();")
            .addLine("assertThat(copy.getItems()).isSameAs(value.getItems());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromReusesImmutableSetInstance() {
    assumeTrue(features.get(GUAVA).isAvailable());
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder().mergeFrom(value).build();")
            .addLine("assertThat(copy.getItems()).isSameAs(value.getItems());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromEmptySetDoesNotPreventReuseOfImmutableSetInstance() {
    assumeTrue(features.get(GUAVA).isAvailable());
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .from(value)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("    .build();")
            .addLine("assertThat(copy.getItems()).isSameAs(value.getItems());")
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndAdd() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .addItems(\"three\")")
            .addLine("    .build();")
            .addLine("assertThat(copy.getItems())")
            .addLine("    .containsExactly(\"one\", \"two\", \"three\")")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .removeItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(copy.getItems()).containsExactly(\"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndClear() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clearItems()")
            .addLine("    .build();")
            .addLine("assertThat(copy.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndClearAll() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertThat(copy.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeInvalidData() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(STRING_VALIDATION_ERROR_MESSAGE);
    behaviorTester
        .with(new Processor(features))
        .with(VALIDATED_STRINGS)
        .with(testBuilder()
            .addLine("DataType value = new DataType() {")
            .addLine("  @Override public Set<String> getItems() {")
            .addLine("    return ImmutableSet.of(\"foo\", \"\");")
            .addLine("  }")
            .addLine("};")
            .addLine("DataType.Builder.from(value);")
            .build())
        .runTest();
  }

  @Test
  public void testMergeCombinesSets() {
    behaviorTester
        .with(new Processor(features))
        .with(SET_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType data1 = new DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType data2 = new DataType.Builder()")
            .addLine("    .addItems(\"three\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(data2).mergeFrom(data1).build();")
            .addLine("assertThat(copy.getItems())")
            .addLine("    .containsExactly(\"three\", \"two\", \"one\")")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport(Set.class)
        .addImport(ImmutableSet.class)
        .addImport("com.example.DataType");
  }
}
