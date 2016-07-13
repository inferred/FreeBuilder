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
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.testing.EqualsTester;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
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

import java.util.Iterator;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SetMultimapPropertyFactoryTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_GUAVA;
  }

  private static final JavaFileObject MULTIMAP_PROPERTY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String, String> getItems();", SetMultimap.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject MULTIMAP_PRIMITIVE_KEY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<Integer, String> getItems();", SetMultimap.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject MULTIMAP_PRIMITIVE_VALUE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String, Character> getItems();", SetMultimap.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject MULTIMAP_PRIMITIVES = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<Integer, Character> getItems();", SetMultimap.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
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
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("%s<String, String> foo = value.getItems();", SetMultimap.class)
            .addLine("assertThat(foo).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testDefaultEmpty_primitives() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVES)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("%s<Integer, Character> foo = value.getItems();", SetMultimap.class)
            .addLine("assertThat(foo).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testPut() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPut_primitiveKey() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_KEY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(1, \"A\")")
            .addLine("    .putItems(2, \"B\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, \"A\")")
            .addLine("    .and(2, \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPut_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_VALUE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", 'A')")
            .addLine("    .putItems(\"two\", 'B')")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", 'A')")
            .addLine("    .and(\"two\", 'B')")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPut_primitives() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVES)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(1, 'A')")
            .addLine("    .putItems(2, 'B')")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, 'A')")
            .addLine("    .and(2, 'B')")
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
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .putItems((String) null, \"B\");")
            .build())
        .runTest();
  }

  @Test
  public void testPut_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .putItems(\"two\", (String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testPut_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", %s.of(\"A\", \"B\"))", ImmutableList.class)
            .addLine("    .putAllItems(\"two\", %s.of(\"Blue\"))", ImmutableSet.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"one\", \"B\")")
            .addLine("    .and(\"two\", \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_primitiveKey() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_KEY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(1, %s.of(\"A\", \"B\"))", ImmutableList.class)
            .addLine("    .putAllItems(2, %s.of(\"Blue\"))", ImmutableSet.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, \"A\")")
            .addLine("    .and(1, \"B\")")
            .addLine("    .and(2, \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_VALUE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", %s.of('A\', 'B'))", ImmutableList.class)
            .addLine("    .putAllItems(\"two\", %s.of('C'))", ImmutableSet.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", 'A')")
            .addLine("    .and(\"one\", 'B')")
            .addLine("    .and(\"two\", 'C')")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_primitives() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVES)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(1, %s.of('A', 'B'))", ImmutableList.class)
            .addLine("    .putAllItems(2, %s.of('C'))", ImmutableSet.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, 'A')")
            .addLine("    .and(1, 'B')")
            .addLine("    .and(2, 'C')")
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
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putAllItems(null, %s.of(\"A\", \"B\"));", ImmutableList.class)
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", %s.newArrayList(\"A\", null));", Lists.class)
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", %s.of(\"A\", \"A\"))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllIterable_iteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", new %s(\"A\", \"B\"))", DodgyStringIterable.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"one\", \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
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
  public void testPutAllMultimap() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", \"A\",")
            .addLine("        \"one\", \"B\",")
            .addLine("        \"two\", \"Blue\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"one\", \"B\")")
            .addLine("    .and(\"two\", \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_primitiveKey() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_KEY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        1, \"A\",")
            .addLine("        1, \"B\",")
            .addLine("        2, \"Blue\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, \"A\")")
            .addLine("    .and(1, \"B\")")
            .addLine("    .and(2, \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_VALUE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", 'A',")
            .addLine("        \"one\", 'B',")
            .addLine("        \"two\", 'C'))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", 'A')")
            .addLine("    .and(\"one\", 'B')")
            .addLine("    .and(\"two\", 'C')")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_primitives() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVES)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        1, 'A',")
            .addLine("        1, 'B',")
            .addLine("        2, 'C'))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, 'A')")
            .addLine("    .and(1, 'B')")
            .addLine("    .and(2, 'C')")
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
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("%1$s<String, String> values = %1$s.create();", LinkedHashMultimap.class)
            .addLine("values.put(null, \"A\");")
            .addLine("new DataType.Builder().putAllItems(values);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("%1$s<String, String> values = %1$s.create();", LinkedHashMultimap.class)
            .addLine("values.put(\"one\", null);")
            .addLine("new DataType.Builder().putAllItems(values);")
            .build())
        .runTest();
  }

  @Test
  public void testPutAllMultimap_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", \"A\",")
            .addLine("        \"one\", \"A\",")
            .addLine("        \"two\", \"Blue\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", \"A\",")
            .addLine("        \"one\", \"Blue\",")
            .addLine("        \"two\", \"Blue\"))")
            .addLine("    .removeItems(\"one\", \"Blue\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_doesNotThrowIfEntryNotPresent() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", \"A\",")
            .addLine("        \"two\", \"Blue\"))")
            .addLine("    .removeItems(\"one\", \"Blue\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_primitiveKey() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_KEY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        1, \"A\",")
            .addLine("        1, \"Blue\",")
            .addLine("        2, \"Blue\"))")
            .addLine("    .removeItems(1, \"Blue\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, \"A\")")
            .addLine("    .and(2, \"Blue\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_VALUE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", \'A\',")
            .addLine("        \"one\", \'B\',")
            .addLine("        \"two\", \'B\'))")
            .addLine("    .removeItems(\"one\", \'B\')")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \'A\')")
            .addLine("    .and(\"two\", \'B\')")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_primitives() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVES)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        1, \'A\',")
            .addLine("        1, \'B\',")
            .addLine("        2, \'B\'))")
            .addLine("    .removeItems(1, \'B\')")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(1, \'A\')")
            .addLine("    .and(2, \'B\')")
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
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .removeItems((String) null, \"A\");")
            .build())
        .runTest();
  }

  @Test
  public void testRemove_nullValue() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .removeItems(\"one\", (String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", \"A\",")
            .addLine("        \"one\", \"Blue\",")
            .addLine("        \"two\", \"Blue\"))")
            .addLine("    .removeAllItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"two\", \"Blue\")")
            .addLine("    .andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll_doesNotThrowIfKeyNotPresent() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"two\", \"Blue\"))")
            .addLine("    .removeAllItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"two\", \"Blue\")")
            .addLine("    .andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll_primitiveKey() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_KEY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        1, \"A\",")
            .addLine("        1, \"Blue\",")
            .addLine("        2, \"Blue\"))")
            .addLine("    .removeAllItems(1)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(2, \"Blue\")")
            .addLine("    .andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVE_VALUE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        \"one\", \'A\',")
            .addLine("        \"one\", \'B\',")
            .addLine("        \"two\", \'B\'))")
            .addLine("    .removeAllItems(\"one\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"two\", \'B\')")
            .addLine("    .andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll_primitives() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PRIMITIVES)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s.of(", ImmutableMultimap.class)
            .addLine("        1, \'A\',")
            .addLine("        1, \'B\',")
            .addLine("        2, \'B\'))")
            .addLine("    .removeAllItems(1)")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(2, \'B\')")
            .addLine("    .andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testRemoveAll_nullKey() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .removeAllItems((String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .clearItems()")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .putItems(\"one\", \"C\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"two\", \"B\")")
            .addLine("    .and(\"one\", \"C\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String, String> itemsView = builder.getItems();", SetMultimap.class)
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(\"one\", \"A\");")
            .addLine("assertThat(itemsView).contains(\"one\", \"A\").andNothingElse();")
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.putItems(\"three\", \"C\");")
            .addLine("assertThat(itemsView).contains(\"three\", \"C\").andNothingElse();")
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsUnmodifiableSetMultimap() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<String, String> itemsView = builder.getItems();", SetMultimap.class)
            .addLine("itemsView.put(\"anything\", \"anything\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.build().getItems())")
            .addLine("    .contains(\"two\", \"B\")")
            .addLine("    .and(\"one\", \"A\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .putItems(\"one\", \"A\");")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.build().getItems())")
            .addLine("    .contains(\"two\", \"B\")")
            .addLine("    .and(\"one\", \"A\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .clear()")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .putItems(\"one\", \"C\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"two\", \"B\")")
            .addLine("    .and(\"one\", \"C\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testImmutableSetMultimapProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<String, String> getItems();", ImmutableSetMultimap.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingPut() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<String, String> getItems();", SetMultimap.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(String unused, String unused2) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"zero\", \"A\")")
            .addLine("    .putAllItems(\"one\", %s.of(\"B\", \"C\"))", ImmutableList.class)
            .addLine("    .putAllItems(%s.of(\"three\", \"D\"))", ImmutableMultimap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingPut_primitiveKey() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer, String> getItems();", SetMultimap.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(int unused, String unused2) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(0, \"A\")")
            .addLine("    .putAllItems(1, %s.of(\"B\", \"C\"))", ImmutableList.class)
            .addLine("    .putAllItems(%s.of(3, \"D\"))", ImmutableMultimap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingPut_primitiveValue() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer, Character> getItems();", SetMultimap.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(int unused, char unused2) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(0, 'A')")
            .addLine("    .putAllItems(1, %s.of('B', 'C'))", ImmutableList.class)
            .addLine("    .putAllItems(%s.of(3, 'D'))", ImmutableMultimap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testOverridingPut_primitives() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer, Character> getItems();", SetMultimap.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder putItems(int unused, char unused2) {")
            .addLine("      return this;")
            .addLine("    }")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(0, 'A')")
            .addLine("    .putAllItems(1, %s.of('B', 'C'))", ImmutableList.class)
            .addLine("    .putAllItems(%s.of(3, 'D'))", ImmutableMultimap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems()).isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(MULTIMAP_PROPERTY)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder().build(),")
            .addLine("        DataType.builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .putItems(\"one\", \"A\")")
            .addLine("            .putItems(\"two\", \"B\")")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .putItems(\"two\", \"B\")")
            .addLine("            .putItems(\"one\", \"A\")")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .putItems(\"one\", \"A\")")
            .addLine("            .putItems(\"one\", \"B\")")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .putItems(\"one\", \"B\")")
            .addLine("            .putItems(\"one\", \"A\")")
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
            .addLine("  @JsonProperty(\"stuff\") %s<String, String> getItems();", SetMultimap.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .putItems(\"two\", \"B\")")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
            .addLine("    .registerModule(new %s());", GuavaModule.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(clone.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"B\")")
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
