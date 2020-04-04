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

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class BiMapMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "BiMap<{0}, {1}>, checked={2}, {3}, {4}")
  public static Iterable<Object[]> parameters() {
    List<Boolean> checked = ImmutableList.of(false, true);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA;
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

  private final SourceBuilder bimapPropertyType;

  public BiMapMutateMethodTest(
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

    bimapPropertyType = SourceBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s, %s> %s;", BiMap.class, keys.type(), values.type(), convention.get())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {");
    if (checked) {
      bimapPropertyType
          .addLine("    @Override public Builder forcePutItems(%s key, %s value) {",
              keys.unwrappedType(), values.unwrappedType())
          .addLine("      if (!(%s)) {", keys.validation("key"))
          .addLine("        throw new IllegalArgumentException(\"key %s\");", keys.errorMessage())
          .addLine("      }")
          .addLine("      if (!(%s)) {", values.validation("value"))
          .addLine("        throw new IllegalArgumentException(\"value %s\");",
              values.errorMessage())
          .addLine("      }")
          .addLine("      return super.forcePutItems(key, value);")
          .addLine("    }");
    }
    bimapPropertyType
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
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.put(%s, %s))",
                keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 0, 1, 1))
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
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.put(%s, %s));",
                keys.invalidExample(), values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void putReplacesDuplicateKey() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.put(%s, %s))",
                keys.example(0), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void putRejectsDuplicateValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value already present: " + values.exampleToString(0));
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.put(%s, %s));",
                keys.example(1), values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void inversePutModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().put(%s, %s))",
                values.example(1), keys.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void inversePutChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("key " + keys.errorMessage());
    }
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.inverse().put(%s, %s));",
                values.example(0), keys.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void inversePutReplacesDuplicateValue() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().put(%s, %s))",
                values.example(0), keys.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(1, 0))
            .build())
        .runTest();
  }

  @Test
  public void inversePutRejectsDuplicateKey() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value already present: " + keys.exampleToString(0));
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().put(%s, %s));",
                values.example(1), keys.example(0))
            .build())
        .runTest();
  }

  @Test
  public void forcePutModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.forcePut(%s, %s))",
                keys.example(1), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void forcePutChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("key " + keys.errorMessage());
    }
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.forcePut(%s, %s));",
                keys.invalidExample(), values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void forcePutReplacesDuplicateKey() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.forcePut(%s, %s))",
                keys.example(0), values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void forcePutReplacesDuplicateValue() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.forcePut(%s, %s))",
                keys.example(1), values.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(1, 0))
            .build())
        .runTest();
  }

  @Test
  public void inverseForcePutModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().forcePut(%s, %s))",
                values.example(1), keys.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void inverseForcePutChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("key " + keys.errorMessage());
    }
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.inverse().forcePut(%s, %s));",
                values.example(0), keys.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void inverseForcePutReplacesDuplicateValue() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().forcePut(%s, %s))",
                values.example(0), keys.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(1, 0))
            .build())
        .runTest();
  }

  @Test
  public void inverseForcePutReplacesDuplicateKey() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().forcePut(%s, %s))",
                values.example(1), keys.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void putAllModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.putAll(%s))", exampleBiMap(1, 1, 2, 2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 0, 1, 1, 2, 2))
            .build())
        .runTest();
  }

  @Test
  public void putAllChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("key " + keys.errorMessage());
    }
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.putAll(%s.of(%s, %s)));",
                ImmutableMap.class, keys.invalidExample(), values.example(0))
            .build())
        .runTest();
  }

  @Test
  public void putAllReplacesDuplicateKey() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.putAll(%s))", exampleBiMap(0, 1, 2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 1, 2, 3))
            .build())
        .runTest();
  }

  @Test
  public void putAllRejectsDuplicateValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value already present: " + values.exampleToString(0));
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.putAll(%s));", exampleBiMap(1, 0, 2, 3))
            .build())
        .runTest();
  }

  @Test
  public void inversePutAllModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().putAll(%s))",
                exampleInverseBiMap(1, 1, 2, 2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(0, 0, 1, 1, 2, 2))
            .build())
        .runTest();
  }

  @Test
  public void inversePutAllChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("key " + keys.errorMessage());
    }
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.inverse().putAll(%s.of(%s, %s)));",
                ImmutableMap.class, values.example(0), keys.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void inversePutAllReplacesDuplicateValue() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().putAll(%s))",
                exampleInverseBiMap(0, 1, 2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);",
                convention.get(), exampleBiMap(1, 0, 3, 2))
            .build())
        .runTest();
  }

  @Test
  public void inversePutAllRejectsDuplicateKey() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value already present: " + keys.exampleToString(0));
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.inverse().putAll(%s));",
                exampleInverseBiMap(1, 0, 2, 3))
            .build())
        .runTest();
  }

  @Test
  public void iterateEntrySetFindsContainedEntry() {
    behaviorTester
        .with(bimapPropertyType)
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
  public void iterateInverseEntrySetFindsContainedEntry() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> {")
            .addLine("      Map.Entry<%s, %s> entry =", values.type(), keys.type())
            .addLine("          items.inverse().entrySet().iterator().next();")
            .addLine("      assertThat(entry.getKey()).isEqualTo(%s);", values.example(0))
            .addLine("      assertThat(entry.getValue()).isEqualTo(%s);", keys.example(0))
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void callRemoveOnEntrySetIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
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
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleBiMap(1, 1))
            .build())
        .runTest();
  }

  @Test
  public void callRemoveOnInverseEntrySetIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> {")
            .addLine("        Iterator<Map.Entry<%s, %s>> i =", values.type(), keys.type())
            .addLine("            items.inverse().entrySet().iterator();")
            .addLine("        i.next();")
            .addLine("        i.remove();")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleBiMap(1, 1))
            .build())
        .runTest();
  }

  @Test
  public void entrySetIteratorRemainsUsableAfterCallingRemove() {
    behaviorTester
        .with(bimapPropertyType)
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
  public void inverseEntrySetIteratorRemainsUsableAfterCallingRemove() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> {")
            .addLine("        Iterator<Map.Entry<%s, %s>> i =", values.type(), keys.type())
            .addLine("            items.inverse().entrySet().iterator();")
            .addLine("        Map.Entry<%s, %s> entry = i.next();", values.type(), keys.type())
            .addLine("        assertThat(entry.getKey()).isEqualTo(%s);", values.example(0))
            .addLine("        assertThat(entry.getValue()).isEqualTo(%s);", keys.example(0))
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        i.remove();")
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(%s);", values.example(1))
            .addLine("        assertThat(entry.getValue()).isEqualTo(%s);", keys.example(1))
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
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.entrySet().iterator().next().setValue(%s));",
                values.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnEntryChecksDuplicateValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value already present: " + values.exampleToString(1));
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> items.entrySet().iterator().next().setValue(%s));",
                values.example(1))
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnEntryModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items.entrySet().iterator().next().setValue(%s))",
                values.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleBiMap(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnInverseEntryChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage("key " + keys.errorMessage());
    }
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items")
            .addLine("        .inverse().entrySet().iterator().next().setValue(%s));",
                keys.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnInverseEntryChecksDuplicateValue() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value already present: " + keys.exampleToString(1));
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> items")
            .addLine("        .inverse().entrySet().iterator().next().setValue(%s));",
                keys.example(1))
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnInverseEntryModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> items")
            .addLine("        .inverse().entrySet().iterator().next().setValue(%s))",
                keys.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleBiMap(1, 0))
            .build())
        .runTest();
  }

  @Test
  public void entryRemainsUsableAfterCallingSetValue() {
    behaviorTester
        .with(bimapPropertyType)
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
  public void inverseEntryRemainsUsableAfterCallingSetValue() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> {")
            .addLine("        Iterator<Map.Entry<%s, %s>> i =", values.type(), keys.type())
            .addLine("            items.inverse().entrySet().iterator();")
            .addLine("        Map.Entry<%s, %s> entry = i.next();", values.type(), keys.type())
            .addLine("        entry.setValue(%s);", keys.example(2))
            .addLine("        assertThat(entry.getKey()).isEqualTo(%s);", values.example(0))
            .addLine("        assertThat(entry.getValue()).isEqualTo(%s);", keys.example(2))
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void getReturnsContainedValue() {
    behaviorTester
        .with(bimapPropertyType)
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
  public void inverseGetReturnsContainedKey() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("List<%s> keys = new ArrayList<>();", keys.type())
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> keys.add(items.inverse().get(%s)))",
                values.example(0))
            .addLine("    .mutateItems(items -> keys.add(items.inverse().get(%s)));",
                values.example(1))
            .addLine("assertThat(keys).containsExactly(%s, null).inOrder();", keys.example(0))
            .build())
        .runTest();
  }

  @Test
  public void containsKeyFindsContainedKey() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("List<Boolean> results = new ArrayList<>();")
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> results.add(items.containsKey(%s)))",
                keys.example(0))
            .addLine("    .mutateItems(items -> results.add(items.containsKey(%s)));",
                keys.example(1))
            .addLine("assertThat(results).containsExactly(true, false).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void inverseContainsKeyFindsContainedValue() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("List<Boolean> results = new ArrayList<>();")
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .mutateItems(items -> results.add(items.inverse().containsKey(%s)))",
                values.example(0))
            .addLine("    .mutateItems(items -> results.add(items.inverse().containsKey(%s)));",
                values.example(1))
            .addLine("assertThat(results).containsExactly(true, false).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void removeModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> items.remove(%s))", keys.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleBiMap(1, 1))
            .build())
        .runTest();
  }

  @Test
  public void inverseRemoveModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> items.inverse().remove(%s))", values.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEqualTo(%s);", convention.get(), exampleBiMap(1, 1))
            .build())
        .runTest();
  }

  @Test
  public void clearModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(%s::clear)", BiMap.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void inverseClearModifiesUnderlyingProperty() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> items.inverse().clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.get())
            .build())
        .runTest();
  }

  @Test
  public void valuesReturnsLiveViewOfValuesInMap() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("%s<%s<%s>> values = new %s<>();",
                List.class, Set.class, values.type(), ArrayList.class)
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> values.add(items.values()));")
            .addLine("assertThat(values.get(0)).containsExactly(%s);", values.examples(0, 1))
            .addLine("builder.putItems(%s, %s);", keys.example(2), values.example(2))
            .addLine("assertThat(values.get(0)).containsExactly(%s);", values.examples(0, 1, 2))
            .build())
        .runTest();
  }

  @Test
  public void inverseValuesReturnsLiveViewOfKeysInMap() {
    behaviorTester
        .with(bimapPropertyType)
        .with(testBuilder()
            .addLine("%s<%s<%s>> keys = new %s<>();",
                List.class, Set.class, keys.type(), ArrayList.class)
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", keys.example(0), values.example(0))
            .addLine("    .putItems(%s, %s)", keys.example(1), values.example(1))
            .addLine("    .mutateItems(items -> keys.add(items.inverse().values()));")
            .addLine("assertThat(keys.get(0)).containsExactly(%s);", keys.examples(0, 1))
            .addLine("builder.putItems(%s, %s);", keys.example(2), values.example(2))
            .addLine("assertThat(keys.get(0)).containsExactly(%s);", keys.examples(0, 1, 2))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomFunctionalInterface() {
    SourceBuilder customMutatorType = SourceBuilder.forTesting();
    for (String line : bimapPropertyType.toString().split("\n")) {
      if (line.contains("extends DataType_Builder")) {
        int insertIndex = line.indexOf('{') + 1;
        customMutatorType
            .addLine("%s", line.substring(0, insertIndex))
            .addLine("    public interface Mutator {")
            .addLine("      void mutate(%s<%s, %s> multiBiMap);",
                BiMap.class, keys.type(), values.type())
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
                convention.get(), exampleBiMap(0, 0, 1, 1))
            .build())
        .runTest();
  }

  private String exampleBiMap(int key, int value) {
    return String.format("ImmutableBiMap.of(%s, %s)", keys.example(key), values.example(value));
  }

  private String exampleBiMap(int key1, int value1, int key2, int value2) {
    return String.format("ImmutableBiMap.of(%s, %s, %s, %s)",
        keys.example(key1), values.example(value1), keys.example(key2), values.example(value2));
  }

  private String exampleInverseBiMap(int value1, int key1, int value2, int key2) {
    return String.format("ImmutableBiMap.of(%s, %s, %s, %s)",
        values.example(value1), keys.example(key1), values.example(value2), keys.example(key2));
  }

  private String exampleBiMap(int key1, int value1, int key2, int value2, int key3, int value3) {
    return String.format("ImmutableBiMap.of(%s, %s, %s, %s, %s, %s)",
        keys.example(key1), values.example(value1),
        keys.example(key2), values.example(value2),
        keys.example(key3), values.example(value3));
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(ArrayList.class)
        .addImport(Iterator.class)
        .addImport(List.class)
        .addImport(Map.class)
        .addImport(BiMap.class)
        .addImport(ImmutableBiMap.class);
  }
}
