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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MapMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "Map<{0}, {1}>, checked={2}, {3}, {4}")
  public static Iterable<Object[]> parameters() {
    List<Boolean> checked = ImmutableList.of(false, true);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(TYPES, TYPES, checked, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory keys;
  private final ElementFactory values;
  private final boolean checked;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final CompilationUnitBuilder mapPropertyType;

  public MapMutateMethodTest(
      ElementFactory keys,
      ElementFactory values,
      boolean checked,
      NamingConvention convention,
      FeatureSet features) {
    this.keys = keys;
    this.values = values;
    this.checked = checked;
    this.convention = convention;
    this.features = features;

    mapPropertyType = CompilationUnitBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s, %s> %s;", Map.class, keys.type(), values.type(), convention.get())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {");
    if (checked) {
      mapPropertyType
          .addLine("    @Override public Builder putItems(%s key, %s value) {",
              keys.unwrappedType(), values.unwrappedType())
          .addLine("      if (!(%s)) {", keys.validation("key"))
          .addLine("        throw new IllegalArgumentException(\"key %s\");", keys.errorMessage())
          .addLine("      }")
          .addLine("      if (!(%s)) {", values.validation("value"))
          .addLine("        throw new IllegalArgumentException(\"value %s\");",
              values.errorMessage())
          .addLine("      }")
          .addLine("      return super.putItems(key, value);")
          .addLine("    }");
    }
    mapPropertyType
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
  public void putModifiesUnderlyingProperty() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.put(%s, %s))",
                keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void putChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("key " + keys.errorMessage());
    }
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.put(%s, %s));",
                keys.invalidExample(), values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void iterateEntrySetFindsContainedEntry() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> {")
            .addLine("      Map.Entry<%s, %s> entry = items.entrySet().iterator().next();",
                keys.type(), values.type())
            .addLine("      assertThat(entry.getKey()).isEqualTo(%s);", keys.example(0))
            .addLine("      assertThat(entry.getValue()).isEqualTo(%s);", values.example(0))
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void callRemoveOnEntrySetIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> {")
            .addLine("        Iterator<Map.Entry<%s, %s>> i = items.entrySet().iterator();",
                keys.type(), values.type())
            .addLine("        i.next();")
            .addLine("        i.remove();")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(1, 1))
            .build())
        .runTest();
  }

  @Test
  public void entrySetIteratorRemainsUsableAfterCallingRemove() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> {")
            .addLine("        Iterator<Map.Entry<%s, %s>> i = items.entrySet().iterator();",
                keys.type(), values.type())
            .addLine("        Map.Entry<%s, %s> entry = i.next();", keys.type(), values.type())
            .addLine("        assertThat(entry.getKey()).isEqualTo(%s);", keys.example(0))
            .addLine("        assertThat(entry.getValue()).isEqualTo(%s);", values.example(0))
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        i.remove();")
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(%s);", keys.example(1))
            .addLine("        assertThat(entry.getValue()).isEqualTo(%s);", values.example(1))
            .addLine("        assertThat(i.hasNext()).isFalse();")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnEntryChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("value " + values.errorMessage());
    }
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.entrySet().iterator().next().setValue(%s));",
                values.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnEntryModifiesUnderlyingProperty() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.entrySet().iterator().next().setValue(%s))",
                values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void entryRemainsUsableAfterCallingSetValue() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> {")
            .addLine("        Iterator<Map.Entry<%s, %s>> i = items.entrySet().iterator();",
                keys.type(), values.type())
            .addLine("        Map.Entry<%s, %s> entry = i.next();", keys.type(), values.type())
            .addLine("        entry.setValue(%s);", values.example(2))
            .addLine("        assertThat(entry.getKey()).isEqualTo(%s);", keys.example(0))
            .addLine("        assertThat(entry.getValue()).isEqualTo(%s);", values.example(2))
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void entrySetIteratorRemainsUsableAfterCallingSetValue() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> {")
            .addLine("        Iterator<Map.Entry<%s, %s>> i = items.entrySet().iterator();",
                keys.type(), values.type())
            .addLine("        Map.Entry<%s, %s> entry = i.next();", keys.type(), values.type())
            .addLine("        entry.setValue(%s);", values.example(2))
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(%s);", keys.example(1))
            .addLine("        assertThat(entry.getValue()).isEqualTo(%s);", values.example(1))
            .addLine("        assertThat(i.hasNext()).isFalse();")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void getReturnsContainedValue() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("List<%s> values = new ArrayList<>();", values.type())
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> values.add(items.get(%s)))", keys.example(0))
            .addLine("    .mutateItems(items -> values.add(items.get(%s)));", keys.example(1))
            .addLine("assertThat(values).containsExactly(%s, null).inOrder();", values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void containsKeyFindsContainedKey() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("List<Boolean> containsKeys = new ArrayList<>();")
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> containsKeys.add(items.containsKey(%s)))",
                keys.example(0))
            .addLine("    .mutateItems(items -> containsKeys.add(items.containsKey(%s)));",
                keys.example(1))
            .addLine("assertThat(containsKeys).containsExactly(true, false).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void removeModifiesUnderlyingProperty() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> items.remove(%s))", keys.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleMap(1, 1))
            .build())
        .runTest();
  }

  @Test
  public void clearModifiesUnderlyingProperty() {
    behaviorTester
        .with(mapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(%s::clear)", Map.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomFunctionalInterface() {
    CompilationUnitBuilder customMutatorType = CompilationUnitBuilder.forTesting();
    for (String line : mapPropertyType.toString().split("\n")) {
      if (line.contains("extends DataType_Builder")) {
        int insertIndex = line.indexOf('{') + 1;
        customMutatorType
            .addLine("%s", line.substring(0, insertIndex))
            .addLine("    public interface Mutator {")
            .addLine("      void mutate(%s<%s, %s> multimap);",
                Map.class, keys.type(), values.type())
            .addLine("    }")
            .addLine("    @Override public Builder mutateItems(Mutator mutator) {")
            .addLine("      return super.mutateItems(mutator);")
            .addLine("    }")
            .addLine("%s", line.substring(insertIndex));
      } else {
        customMutatorType.addLine("%s", line);
      }
    }

    behaviorTester
        .with(customMutatorType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.put(%s, %s))",
                keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
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

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(ArrayList.class)
        .addImport(Iterator.class)
        .addImport(List.class)
        .addImport(Map.class)
        .addImport(ImmutableMap.class);
  }
}
