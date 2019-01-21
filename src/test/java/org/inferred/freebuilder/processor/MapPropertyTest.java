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

import static org.inferred.freebuilder.processor.ElementFactory.TYPES;
import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.junit.Assume.assumeTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MapPropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "Map<{0}, {1}>, {2}, {3}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(TYPES, TYPES, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory keys;
  private final ElementFactory values;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final SourceBuilder mapPropertyType;

  public MapPropertyTest(
      ElementFactory keys,
      ElementFactory values,
      NamingConvention convention,
      FeatureSet features) {
    this.keys = keys;
    this.values = values;
    this.convention = convention;
    this.features = features;

    mapPropertyType = SourceBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s, %s> %s;", Map.class, keys.type(), values.type(), convention.get())
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {}")
        .addLine("}");
  }

  @Before
  public void before() {
    behaviorTester
        .with(new Processor(features))
        .withPermittedPackage(NonComparable.class.getPackage());
  }

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testPut() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .isEqualTo(%s);", exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testPut_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems((%s) null, %s);", keys.type(), values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testPut_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, (%s) null);", keys.example(0), values.type())
            .build())
        .runTest();
  }

  @Test
  public void testPut_duplicate() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testPutAll() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s)", exampleMap(0, 0, 1, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testPutAll_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("%1$s<%2$s, %3$s> items = new %4$s<%2$s, %3$s>();",
                Map.class, keys.type(), values.type(), LinkedHashMap.class)
            .addLine("items.put(%s, %s);", keys.example(0), values.example(0))
            .addLine("items.put((%s) null, %s);", keys.type(), values.example(1))
            .addLine("new DataType.Builder().putAllItems(items);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAll_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("%1$s<%2$s, %3$s> items = new %4$s<%2$s, %3$s>();",
                Map.class, keys.type(), values.type(), LinkedHashMap.class)
            .addLine("items.put(%s, %s);", keys.example(0), values.example(0))
            .addLine("items.put(%s, (%s) null);", keys.example(1), values.type())
            .addLine("new DataType.Builder().putAllItems(items);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAll_duplicate() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s)", exampleMap(0, 0, 1, 1))
            .addLine("    .putAllItems(%s)", exampleMap(0, 2, 3, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 2, 1, 1, 3, 3))
            .build())
        .runTest();
  }

  @Test
  public void testRemove() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .removeItems(%s)", keys.example(1))
            .addLine("    .putItems(%s, %s)", keys.example(2), values.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 2, 2))
            .build())
        .runTest();
  }

  @Test
  public void testRemove_missingKey() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .removeItems(%s)", keys.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testRemove_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .removeItems((%s) null);", keys.type())
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .clearItems()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
            .addLine("    .putItems(%s, %s)", keys.example(3), values.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 2, 3, 3))
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("Map<%s, %s> itemsView = builder.%s;",
                keys.type(), values.type(), convention.get())
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(%s, %s);", keys.example(0), values.example(0))
            .addLine("builder.putItems(%s, %s);", keys.example(1), values.example(1))
            .addLine("assertThat(itemsView).isEqualTo(%s);", exampleMap(0, 0, 1, 1))
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(%s, %s);", keys.example(0), values.example(2))
            .addLine("builder.putItems(%s, %s);", keys.example(3), values.example(3))
            .addLine("assertThat(itemsView).isEqualTo(%s);", exampleMap(0, 2, 3, 3))
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsUnmodifiableMap() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("Map<%s, %s> itemsView = builder.%s;",
                keys.type(), values.type(), convention.get())
            .addLine("itemsView.put(%s, %s);", keys.example(0), values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType template = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mergeFrom(template)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s);", keys.example(1), values.example(1))
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mergeFrom(template)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .clear()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
            .addLine("    .putItems(%s, %s)", keys.example(3), values.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 2, 3, 3))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noDefaultFactory() {
    behaviorTester
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;", Map.class, keys.type(), values.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    public Builder(%s key, %s value) {",
                keys.unwrappedType(), values.unwrappedType())
            .addLine("      putItems(key, value);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value =")
            .addLine("    new DataType.Builder(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .clear()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
            .addLine("    .putItems(%s, %s)", keys.example(3), values.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 2, 3, 3))
            .build())
        .runTest();
  }

  @Test
  public void testImmutableMapProperty() {
    assumeTrue("Guava available", features.get(GUAVA).isAvailable());
    behaviorTester
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;",
                ImmutableMap.class, keys.type(), values.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void testOverridingAdd() {
    behaviorTester
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;", Map.class, keys.type(), values.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(%s key, %s value) {",
                keys.unwrappedType(), values.unwrappedType())
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .putAllItems(%s)", exampleMap(2, 2, 3, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().build(),")
            .addLine("        new DataType.Builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("            .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("            .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testJacksonInteroperability() {
    // See also https://github.com/google/FreeBuilder/issues/68
    assumeTrue(keys.isSerializableAsMapKey());
    behaviorTester
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("import " + JsonProperty.class.getName() + ";")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public interface DataType {")
            .addLine("  @JsonProperty(\"stuff\") %s<%s, %s> %s;",
                Map.class, keys.type(), values.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("%s.out.println(json);", System.class)
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  private String exampleMap(int key, int value) {
    return String.format("ImmutableMap.of(%s, %s)", keys.example(key), values.example(value));
  }

  private String exampleMap(int key1, int value1, int key2, int value2) {
    return String.format("ImmutableMap.of(%s, %s, %s, %s)",
        keys.example(key1), values.example(value1), keys.example(key2), values.example(value2));
  }

  private String exampleMap(int key1, int value1, int key2, int value2, int key3, int value3) {
    return String.format("ImmutableMap.of(%s, %s, %s, %s, %s, %s)",
        keys.example(key1), values.example(value1),
        keys.example(key2), values.example(value2),
        keys.example(key3), values.example(value3));
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(Map.class)
        .addImport(ImmutableMap.class);
  }
}
