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
import static org.inferred.freebuilder.processor.source.feature.GuavaLibrary.GUAVA;
import static org.junit.Assume.assumeTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;
import java.util.Arrays;
import java.util.List;
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
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class BiMapPropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "BiMap<{0}, {1}>, {2}, {3}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA;
    return () ->
        Lists.cartesianProduct(TYPES, TYPES, conventions, features).stream()
            .map(List::toArray)
            .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory keys;
  private final ElementFactory values;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final SourceBuilder biMapPropertyType;
  private final SourceBuilder validatedType;

  public BiMapPropertyTest(
      ElementFactory keys,
      ElementFactory values,
      NamingConvention convention,
      FeatureSet features) {
    this.keys = keys;
    this.values = values;
    this.convention = convention;
    this.features = features;

    biMapPropertyType =
        SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;", BiMap.class, keys.type(), values.type(), convention.get())
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}");

    validatedType =
        SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s, %s> %s;", BiMap.class, keys.type(), values.type(), convention.get())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine(
                "    @Override public Builder forcePutItems(%s key, %s value) {",
                keys.unwrappedType(), values.unwrappedType())
            .addLine(
                "      %s.checkArgument(%s, \"%s\");",
                Preconditions.class, keys.validation("key"), keys.errorMessage("key"))
            .addLine(
                "      %s.checkArgument(%s, \"%s\");",
                Preconditions.class, values.validation("value"), values.errorMessage("value"))
            .addLine("      return super.forcePutItems(key, value);")
            .addLine("    }")
            .addLine("  }")
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
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder().build();")
                .addLine("assertThat(value.%s).isEmpty();", convention.get())
                .build())
        .runTest();
  }

  @Test
  public void testPut() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
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
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .putItems((%s) null, %s);", keys.type(), values.example(0))
                .build())
        .runTest();
  }

  @Test
  public void testPut_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .putItems(%s, (%s) null);", keys.example(0), values.type())
                .build())
        .runTest();
  }

  @Test
  public void testPut_duplicateKey() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(1))
                .addLine("    .build();")
                .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 1))
                .build())
        .runTest();
  }

  @Test
  public void testPut_duplicateValue() {
    thrown.expect(IllegalArgumentException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s);", keys.example(1), values.example(0))
                .build())
        .runTest();
  }

  @Test
  public void testPut_duplicateKeyAndValue() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .build();")
                .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 0))
                .build())
        .runTest();
  }

  @Test
  public void testForcePut() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .forcePutItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .forcePutItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .build();")
                .addLine("assertThat(value.%s)", convention.get())
                .addLine("    .isEqualTo(%s);", exampleMap(0, 0, 1, 1))
                .build())
        .runTest();
  }

  @Test
  public void testForcePut_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .forcePutItems((%s) null, %s);", keys.type(), values.example(0))
                .build())
        .runTest();
  }

  @Test
  public void testForcePut_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .forcePutItems(%s, (%s) null);", keys.example(0), values.type())
                .build())
        .runTest();
  }

  @Test
  public void testForcePut_duplicate() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .forcePutItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .forcePutItems(%s, %s)", keys.example(1), values.example(0))
                .addLine("    .build();")
                .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(1, 0))
                .build())
        .runTest();
  }

  @Test
  public void testPutAll() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putAllItems(%s)", exampleMap(0, 0, 1, 1))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 0, 1, 1))
                .build())
        .runTest();
  }

  @Test
  public void testPutAll_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine(
                    "%1$s<%2$s, %3$s> items = %4$s.create();",
                    BiMap.class, keys.type(), values.type(), HashBiMap.class)
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
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine(
                    "%1$s<%2$s, %3$s> items = %4$s.create();",
                    BiMap.class, keys.type(), values.type(), HashBiMap.class)
                .addLine("items.put(%s, %s);", keys.example(0), values.example(0))
                .addLine("items.put(%s, (%s) null);", keys.example(1), values.type())
                .addLine("new DataType.Builder().putAllItems(items);")
                .build())
        .runTest();
  }

  @Test
  public void testPutAll_duplicateKey() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putAllItems(%s)", exampleMap(0, 0, 1, 1))
                .addLine("    .putAllItems(%s)", exampleMap(0, 2, 3, 3))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);",
                    convention.get(), exampleMap(0, 2, 1, 1, 3, 3))
                .build())
        .runTest();
  }

  @Test
  public void testPutAll_duplicateValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value already present: " + values.exampleToString(0));
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .putAllItems(%s)", exampleMap(0, 0, 1, 1))
                .addLine("    .putAllItems(%s);", exampleMap(2, 0, 3, 3))
                .build())
        .runTest();
  }

  @Test
  public void testRemoveKeyFrom() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(3))
                .addLine("    .removeKeyFromItems(%s)", keys.example(1))
                .addLine("    .putItems(%s, %s)", keys.example(2), values.example(4))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 2, 2, 4))
                .build())
        .runTest();
  }

  @Test
  public void testRemoveValueFrom() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(3))
                .addLine("    .removeValueFromItems(%s)", values.example(3))
                .addLine("    .putItems(%s, %s)", keys.example(2), values.example(4))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 2, 2, 4))
                .build())
        .runTest();
  }

  @Test
  public void testRemoveKeyFrom_missingKey() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(3))
                .addLine("    .removeKeyFromItems(%s)", keys.example(4))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 2, 1, 3))
                .build())
        .runTest();
  }

  @Test
  public void testRemoveValueFrom_missingKey() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(3))
                .addLine("    .removeValueFromItems(%s)", values.example(4))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 2, 1, 3))
                .build())
        .runTest();
  }

  @Test
  public void testRemoveKeyFrom_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(3))
                .addLine("    .removeKeyFromItems((%s) null);", keys.type())
                .build())
        .runTest();
  }

  @Test
  public void testRemoveValueFrom_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(3))
                .addLine("    .removeValueFromItems((%s) null);", values.type())
                .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .clearItems()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(3), values.example(3))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 2, 3, 3))
                .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType.Builder builder = new DataType.Builder();")
                .addLine(
                    "BiMap<%s, %s> itemsView = builder.%s;",
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
  public void testGet_returnsUnmodifiableBiMap() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType.Builder builder = new DataType.Builder();")
                .addLine(
                    "BiMap<%s, %s> itemsView = builder.%s;",
                    keys.type(), values.type(), convention.get())
                .addLine("itemsView.put(%s, %s);", keys.example(0), values.example(0))
                .build())
        .runTest();
  }

  @Test
  public void testFrom_invalidData() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(keys.errorMessage("key"));
    behaviorTester
        .with(validatedType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType() {")
                .addLine(
                    "  @Override public %s<%s, %s> %s {",
                    BiMap.class, keys.type(), values.type(), convention.get())
                .addLine(
                    "    return %s.of(%s, %s, %s, %s);",
                    ImmutableBiMap.class,
                    keys.example(0),
                    values.example(0),
                    keys.invalidExample(),
                    values.example(1))
                .addLine("  }")
                .addLine("};")
                .addLine("DataType.Builder.from(value);")
                .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType template = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .build();")
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .mergeFrom(template)")
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 0, 1, 1))
                .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType.Builder template = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s);", keys.example(1), values.example(1))
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .mergeFrom(template)")
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 0, 1, 1))
                .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .clear()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(3), values.example(3))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 2, 3, 3))
                .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noDefaultFactory() {
    behaviorTester
        .with(
            SourceBuilder.forTesting()
                .addLine("package com.example;")
                .addLine("@%s", FreeBuilder.class)
                .addLine("public interface DataType {")
                .addLine(
                    "  %s<%s, %s> %s;", BiMap.class, keys.type(), values.type(), convention.get())
                .addLine("")
                .addLine("  class Builder extends DataType_Builder {")
                .addLine(
                    "    public Builder(%s key, %s value) {",
                    keys.unwrappedType(), values.unwrappedType())
                .addLine("      putItems(key, value);")
                .addLine("    }")
                .addLine("  }")
                .addLine("}"))
        .with(
            testBuilder()
                .addLine("DataType value =")
                .addLine("    new DataType.Builder(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .clear()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(2))
                .addLine("    .putItems(%s, %s)", keys.example(3), values.example(3))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 2, 3, 3))
                .build())
        .runTest();
  }

  @Test
  public void testToBuilder() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .build()")
                .addLine("    .toBuilder()")
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(2))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 0, 1, 2))
                .build())
        .runTest();
  }

  @Test
  public void testImmutableBiMapProperty() {
    assumeTrue("Guava available", features.get(GUAVA).isAvailable());
    behaviorTester
        .with(
            SourceBuilder.forTesting()
                .addLine("package com.example;")
                .addLine("@%s", FreeBuilder.class)
                .addLine("public interface DataType {")
                .addLine(
                    "  %s<%s, %s> %s;",
                    ImmutableBiMap.class, keys.type(), values.type(), convention.get())
                .addLine("")
                .addLine("  class Builder extends DataType_Builder {}")
                .addLine("}"))
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .build();")
                .addLine(
                    "assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 0, 1, 1))
                .build())
        .runTest();
  }

  @Test
  public void testValidation_put() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(keys.errorMessage("key"));
    behaviorTester
        .with(validatedType)
        .with(
            testBuilder()
                .addLine(
                    "new DataType.Builder().putItems(%s, %s);",
                    keys.invalidExample(), values.example(0))
                .build())
        .runTest();
  }

  @Test
  public void testValidation_putAll() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(keys.errorMessage("key"));
    behaviorTester
        .with(validatedType)
        .with(
            testBuilder()
                .addLine(
                    "new DataType.Builder().putAllItems(ImmutableMap.of(%s, %s, %s, %s));",
                    keys.example(0), values.example(0), keys.invalidExample(), values.example(1))
                .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(biMapPropertyType)
        .with(
            testBuilder()
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
  public void testCompileErrorIfOnlyPutOverridden() {
    behaviorTester
        .with(
            SourceBuilder.forTesting()
                .addLine("package com.example;")
                .addLine("@%s", FreeBuilder.class)
                .addLine("public interface DataType {")
                .addLine(
                    "  %s<%s, %s> %s;", BiMap.class, keys.type(), values.type(), convention.get())
                .addLine("")
                .addLine("  class Builder extends DataType_Builder {")
                .addLine(
                    "    @Override public Builder putItems(%s key, %s value) {",
                    keys.unwrappedType(), values.unwrappedType())
                .addLine(
                    "      %s.checkArgument(%s, \"%s\");",
                    Preconditions.class, keys.validation("key"), keys.errorMessage("key"))
                .addLine(
                    "      %s.checkArgument(%s, \"%s\");",
                    Preconditions.class, values.validation("value"), values.errorMessage("value"))
                .addLine("      return super.putItems(key, value);")
                .addLine("    }")
                .addLine("  }")
                .addLine("}"))
        .failsToCompile()
        .withErrorThat(
            subject ->
                subject.hasMessage(
                    "Overriding putItems will not correctly validate all inputs. "
                        + "Please override forcePutItems."));
  }

  @Test
  public void testJacksonInteroperability() {
    // See also https://github.com/google/FreeBuilder/issues/68
    assumeTrue(keys.isSerializableAsMapKey());
    behaviorTester
        .with(
            SourceBuilder.forTesting()
                .addLine("package com.example;")
                .addLine("import " + JsonProperty.class.getName() + ";")
                .addLine("@%s", FreeBuilder.class)
                .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
                .addLine("public interface DataType {")
                .addLine(
                    "  @JsonProperty(\"stuff\") %s<%s, %s> %s;",
                    ImmutableBiMap.class, keys.type(), values.type(), convention.get())
                .addLine("")
                .addLine("  class Builder extends DataType_Builder {}")
                .addLine("}"))
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
                .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
                .addLine("    .build();")
                .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
                .addLine("    .registerModule(new %s());", GuavaModule.class)
                .addLine("String json = mapper.writeValueAsString(value);")
                .addLine("DataType clone = mapper.readValue(json, DataType.class);")
                .addLine(
                    "assertThat(clone.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 0, 1, 1))
                .build())
        .runTest();
  }

  private String exampleMap(int key, int value) {
    return String.format("ImmutableMap.of(%s, %s)", keys.example(key), values.example(value));
  }

  private String exampleMap(int key1, int value1, int key2, int value2) {
    return String.format(
        "ImmutableMap.of(%s, %s, %s, %s)",
        keys.example(key1), values.example(value1), keys.example(key2), values.example(value2));
  }

  private String exampleMap(int key1, int value1, int key2, int value2, int key3, int value3) {
    return String.format(
        "ImmutableMap.of(%s, %s, %s, %s, %s, %s)",
        keys.example(key1),
        values.example(value1),
        keys.example(key2),
        values.example(value2),
        keys.example(key3),
        values.example(value3));
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(BiMap.class)
        .addImport(ImmutableMap.class);
  }
}
