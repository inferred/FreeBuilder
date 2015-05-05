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

import java.util.Iterator;

import javax.tools.JavaFileObject;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.CompilationException;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.google.common.testing.EqualsTester;

@RunWith(JUnit4.class)
public class MultisetPropertyFactoryTest {

  private static final JavaFileObject MULTISET_PROPERTY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<%s> getItems();", Multiset.class, String.class)
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
      .addLine("  public abstract %s<Integer> getItems();", Multiset.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final BehaviorTester behaviorTester = new BehaviorTester();

  @Test
  public void testDefaultEmpty() {
    behaviorTester
    .with(new Processor())
    .with(MULTISET_PROPERTY_TYPE)
    .with(new TestBuilder()
        .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
        .addLine("assertThat(value.getItems()).isEmpty();")
        .build())
    .runTest();
  }

  @Test
  public void testAddSingleElement() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems((String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_duplicate() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().addItems(\"one\", null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_duplicate() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
        .addLine("com.example.DataType value = new com.example.DataType.Builder()")
        .addLine("    .addItems(\"one\", \"one\")")
        .addLine("    .build();")
        .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"two\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", null));", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_duplicate() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"one\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_iteratesOnce() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addAllItems(new %s(\"one\", \"two\"))", DodgyStringIterable.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"two\");")
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
  public void testAddCopies() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems(\"two\", 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(")
            .addLine("    \"one\", \"one\", \"one\", \"two\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems((String) null, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_negativeOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems(\"two\", -2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_duplicate() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(\"one\", 3)")
            .addLine("    .addCopiesToItems(\"one\", 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(")
            .addLine("    \"one\", \"one\", \"one\", \"one\", \"one\");")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\", \"three\")")
            .addLine("    .setCountOfItems(\"one\", 3)")
            .addLine("    .setCountOfItems(\"two\", 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(")
            .addLine("    \"one\", \"one\", \"one\", \"two\", \"two\", \"three\");")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf_toZero() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .setCountOfItems(\"two\", 0)")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clearItems()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testDefaultEmpty_primitive() {
    behaviorTester
    .with(new Processor())
    .with(MULTISET_PRIMITIVES_TYPE)
    .with(new TestBuilder()
        .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
        .addLine("assertThat(value.getItems()).isEmpty();")
        .build())
    .runTest();
  }

  @Test
  public void testAddSingleElement_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems((Integer) null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_duplicate_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(1)")
            .addLine("    .addItems(1)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().addItems(1, null);")
            .build());
    thrown.expect(CompilationException.class);
    behaviorTester.runTest();
  }

  @Test
  public void testAddVarargs_duplicate_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
        .addLine("com.example.DataType value = new com.example.DataType.Builder()")
        .addLine("    .addItems(1, 1)")
        .addLine("    .build();")
        .addLine("assertThat(value.getItems()).iteratesAs(1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 2))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, null));", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_duplicate_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 1))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems(2, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 1, 1, 2, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_null_primitive() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems((Integer) null, 2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_negativeOccurrences_primitive() {
    thrown.expect(IllegalArgumentException.class);
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems(2, -2);")
            .build())
        .runTest();
  }

  @Test
  public void testAddCopies_duplicate_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addCopiesToItems(1, 3)")
            .addLine("    .addCopiesToItems(1, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 1, 1, 1, 1);")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(1, 2, 3)")
            .addLine("    .setCountOfItems(1, 3)")
            .addLine("    .setCountOfItems(2, 2)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 1, 1, 2, 2, 3);")
            .build())
        .runTest();
  }

  @Test
  public void testSetCountOf_toZero_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .setCountOfItems(2, 0)")
            .addLine("    .addItems(3, 4)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(1, 3, 4);")
            .build())
        .runTest();
  }

  @Test
  public void testClear_primitive() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PRIMITIVES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(1, 2)")
            .addLine("    .clearItems()")
            .addLine("    .addItems(3, 4)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(3, 4);")
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("%s<String> itemsView = builder.getItems();", Multiset.class)
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
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("%s<String> itemsView = builder.getItems();", Multiset.class)
            .addLine("itemsView.add(\"anything\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.build().getItems()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\");")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.build().getItems()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .clear()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noBuilderFactory() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItems();", Multiset.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(String... items) {")
            .addLine("      addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder(\"hello\")")
            .addLine("    .clear()")
            .addLine("    .addItems(\"three\", \"four\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"three\", \"four\");")
            .build())
        .runTest();
  }

  @Test
  public void testImmutableSetProperty() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItems();", ImmutableMultiset.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"one\")")
            .addLine("    .addItems(\"two\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).iteratesAs(\"one\", \"two\");")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItems();", Multiset.class, String.class)
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
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(\"zero\")")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .addAllItems(%s.of(\"three\", \"four\"))", ImmutableList.class)
            .addLine("    .addCopiesToItems(\"seven\", 3)")
            .addLine("    .setCountOfItems(\"eight\", 3)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd_primitive() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> getItems();", Multiset.class)
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
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addItems(0)")
            .addLine("    .addItems(1, 2)")
            .addLine("    .addAllItems(%s.of(3, 4))", ImmutableList.class)
            .addLine("    .addCopiesToItems(7, 3)")
            .addLine("    .setCountOfItems(8, 3)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor())
        .with(MULTISET_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder().build(),")
            .addLine("        com.example.DataType.builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .addItems(\"one\", \"two\")")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .addItems(\"one\", \"two\")")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .addItems(\"one\")")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .addItems(\"one\")")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .addItems(\"one\", \"one\")")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .addItems(\"one\", \"one\")")
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }
}
