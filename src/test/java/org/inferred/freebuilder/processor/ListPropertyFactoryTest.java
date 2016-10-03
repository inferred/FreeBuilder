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

import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.testing.BehaviorTestRunner;
import org.inferred.freebuilder.processor.util.testing.BehaviorTestRunner.Shared;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.List;

import javax.tools.JavaFileObject;

/** Behavioral tests for {@code List<?>} properties. */
@RunWith(BehaviorTestRunner.class)
public class ListPropertyFactoryTest {

  private static final JavaFileObject LIST_PROPERTY_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<%s> getItems();", List.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(new Processor())
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
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(\"one\", null);")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(\"one\", \"two\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_onlyIteratesOnce() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(new %s(\"one\", \"two\"))", DodgySingleIterable.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
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
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
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
  public void testGetter_returnsLiveView() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.getItems();", List.class)
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
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String> itemsView = builder.getItems();", List.class)
            .addLine("itemsView.add(\"something\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(value.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .addItems(\"one\", \"two\");")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor())
        .with(LIST_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItems();", List.class, String.class)
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
            .addLine("assertThat(value.getItems()).containsExactly(\"three\", \"four\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor())
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
  public void testImmutableListProperty() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItems();", ImmutableList.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
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
  public void testOverrideAdd() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItems();", List.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder addItems(String unused) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(\"zero\")")
            .addLine("    .addItems(\"one\", \"two\")")
            .addLine("    .addAllItems(%s.of(\"three\", \"four\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverrideAdd_primitive() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> getItems();", List.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder addItems(int unused) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(0)")
            .addLine("    .addItems(1, 2)")
            .addLine("    .addAllItems(%s.of(3, 4))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testJacksonInteroperability() {
    // See also https://github.com/google/FreeBuilder/issues/68
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("import " + JsonProperty.class.getName() + ";")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public interface DataType {")
            .addLine("  @JsonProperty(\"stuff\") %s<%s> getItems();", List.class, String.class)
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
            .addLine("assertThat(clone.getItems()).containsExactly(\"one\", \"two\").inOrder();")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
