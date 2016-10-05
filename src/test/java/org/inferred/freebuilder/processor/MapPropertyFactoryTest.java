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

import com.google.common.collect.ImmutableMap;
import com.google.common.testing.EqualsTester;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaFileObject;

/** Behavioral tests for {@code List<?>} properties. */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MapPropertyFactoryTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject MAP_PROPERTY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<String, Object> getItems();", Map.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject PRIMITIVE_KEY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer, String> getItems();", Map.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject PRIMITIVE_VALUE_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<String, Double> getItems();", Map.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject PRIMITIVE_KEY_VALUE_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer, Double> getItems();", Map.class)
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testPut_nonNullKey_nonNullValue() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testPut_primitiveKey_nonNullValue() {
    behaviorTester
        .with(new Processor(features))
        .with(PRIMITIVE_KEY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(2, \"bar\")")
            .addLine("    .putItems(5, \"baz\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(2, \"bar\", 5,  \"baz\"));", ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testPut_nullKey_nonNullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putItems(null, \"baz\");")
            .build())
        .runTest();
  }

  @Test
  public void testPut_nonNullKey_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(PRIMITIVE_VALUE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", 1.2)")
            .addLine("    .putItems(\"three\", 3.0)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", 1.2, \"three\", 3.0));", ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testPut_primitiveKey_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(PRIMITIVE_KEY_VALUE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(2, 1.4142)")
            .addLine("    .putItems(3, 1.7321)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(2, 1.4142, 3, 1.7321));", ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testPut_nullKey_primitiveValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(PRIMITIVE_VALUE_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putItems(null, 1.2);")
            .build())
        .runTest();
  }

  @Test
  public void testPut_nonNullKey_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", null);")
            .build())
        .runTest();
  }

  @Test
  public void testPut_primitiveKey_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(PRIMITIVE_KEY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putItems(2, null);")
            .build())
        .runTest();
  }

  @Test
  public void testPut_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"bar\", \"bam\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"bam\"));", ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testPutAll_noNulls() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putAllItems(%s.of(\"bar\", \"baz\", \"three\", 3))",
                ImmutableMap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testPutAll_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("%s<String, Object> items = new %s<String, Object>();",
                Map.class, LinkedHashMap.class)
            .addLine("items.put(\"bar\", \"baz\");")
            .addLine("items.put(null, 3);")
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putAllItems(items);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAll_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("%s<String, Object> items = new %s<String, Object>();",
                Map.class, LinkedHashMap.class)
            .addLine("items.put(\"bar\", \"baz\");")
            .addLine("items.put(\"three\", null);")
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putAllItems(items);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAll_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putAllItems(%s.of(\"bar\", \"bam\", \"three\", 3))",
                ImmutableMap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"bam\", \"three\", 3));", ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .removeItems(\"bar\")")
            .addLine("    .putItems(\"bar\", \"bam\")")
            .addLine("    .putItems(\"four\", 4.0)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"three\", 3, \"bar\", \"bam\", \"four\", 4.0));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testRemove_missingKey() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .removeItems(\"baz\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));", ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testRemove_primitiveKeyType() {
    behaviorTester
        .with(new Processor(features))
        .with(PRIMITIVE_KEY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(1, \"baz\")")
            .addLine("    .putItems(2, \"3\")")
            .addLine("    .removeItems(1)")
            .addLine("    .putItems(1, \"bam\")")
            .addLine("    .putItems(3, \"4.0\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(2, \"3\", 1, \"bam\", 3, \"4.0\"));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testRemove_primitiveKeyType_missingKey() {
    behaviorTester
        .with(new Processor(features))
        .with(PRIMITIVE_KEY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(1, \"baz\")")
            .addLine("    .putItems(2, \"3\")")
            .addLine("    .removeItems(3)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(1, \"baz\", 2, \"3\"));", ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testRemove_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .removeItems(null);")
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .clearItems()")
            .addLine("    .putItems(\"bar\", \"bam\")")
            .addLine("    .putItems(\"four\", 4.0)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"bam\", \"four\", 4.0));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("%s<String, Object> itemsView = builder.getItems();", Map.class)
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(\"bar\", \"baz\");")
            .addLine("builder.putItems(\"three\", 3);")
            .addLine("assertThat(itemsView).isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));",
                ImmutableMap.class)
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(\"bar\", \"bam\");")
            .addLine("builder.putItems(\"four\", 4.0);")
            .addLine("assertThat(itemsView).isEqualTo(%s.of(\"bar\", \"bam\", \"four\", 4.0));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsUnmodifiableMap() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("%s<String, Object> itemsView = builder.getItems();", Map.class)
            .addLine("itemsView.put(\"anyKey\", \"anyValue\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType template = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .build();")
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mergeFrom(template)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3);")
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mergeFrom(template)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .clear()")
            .addLine("    .putItems(\"bar\", \"bam\")")
            .addLine("    .putItems(\"four\", 4.0)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"bam\", \"four\", 4.0));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noDefaultFactory() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<String, Object> getItems();", Map.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    public Builder(String s, Object o) {")
            .addLine("      putItems(s, o);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value =")
            .addLine("    new com.example.DataType.Builder(\"bar\", \"baz\")")
            .addLine("        .putItems(\"three\", 3)")
            .addLine("        .clear()")
            .addLine("        .putItems(\"bar\", \"bam\")")
            .addLine("        .putItems(\"four\", 4.0)")
            .addLine("        .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"bam\", \"four\", 4.0));",
                ImmutableMap.class)
            .build())
        .runTest();
  }

  @Test
  public void testImmutableMapProperty() {
    assumeTrue("Guava available", features.get(GUAVA).isAvailable());
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<String, Object> getItems();", ImmutableMap.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));",
                ImmutableMap.class)
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
            .addLine("public interface DataType {")
            .addLine("  %s<String, Object> getItems();", Map.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(String key, Object value) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .putAllItems(%s.of(\"bacon\", \"baz\", \"five\", 5.0))",
                ImmutableMap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd_primitiveKey() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<Integer, Object> getItems();", Map.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(int key, Object value) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(1, \"baz\")")
            .addLine("    .putItems(3, 3)")
            .addLine("    .putAllItems(%s.of(6, \"baz\", 10, 5.0))",
                ImmutableMap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<String, Double> getItems();", Map.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(String key, double value) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(\"bar\", 1.1)")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .putAllItems(%s.of(\"bacon\", 3.4, \"five\", 5.0))",
                ImmutableMap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd_primitiveKeyAndValue() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<Integer, Double> getItems();", Map.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(int key, double value) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putItems(1, 1.1)")
            .addLine("    .putItems(3, 3)")
            .addLine("    .putAllItems(%s.of(6, 3.4, 10, 5.0))",
                ImmutableMap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(MAP_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        new com.example.DataType.Builder().build(),")
            .addLine("        new com.example.DataType.Builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new com.example.DataType.Builder()")
            .addLine("            .putItems(\"a\", \"b\")")
            .addLine("            .build(),")
            .addLine("        new com.example.DataType.Builder()")
            .addLine("            .putItems(\"a\", \"b\")")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new com.example.DataType.Builder()")
            .addLine("            .putItems(\"a\", \"b\")")
            .addLine("            .putItems(\"c\", \"d\")")
            .addLine("            .build(),")
            .addLine("        new com.example.DataType.Builder()")
            .addLine("            .putItems(\"a\", \"b\")")
            .addLine("            .putItems(\"c\", \"d\")")
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
            .addLine("  @JsonProperty(\"stuff\") %s<String, Object> getItems();", Map.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"bar\", \"baz\")")
            .addLine("    .putItems(\"three\", 3)")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.getItems())")
            .addLine("    .isEqualTo(%s.of(\"bar\", \"baz\", \"three\", 3));",
                ImmutableMap.class)
            .build())
        .runTest();
  }
}
