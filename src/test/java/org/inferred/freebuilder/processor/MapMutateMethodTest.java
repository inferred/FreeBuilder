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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

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
import java.util.Map;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MapMutateMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_LAMBDAS;
  }

  private static final JavaFileObject UNCHECKED_SET_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer, String> getProperties();", Map.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject CHECKED_SET_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("import static %s.checkArgument;", Preconditions.class)
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer, String> getProperties();", Map.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder putProperties(int key, String value) {")
      .addLine("      checkArgument(key >= 0, \"key must be non-negative\");")
      .addLine("      checkArgument(!value.startsWith(\"-\"), \"value must not start with '-'\");")
      .addLine("      return super.putProperties(key, value);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void putModifiesUnderlyingPropertyWhenUnchecked() {
    behaviorTester
        .with(new Processor(features))
        .with(UNCHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .mutateProperties(map -> map.put(11, \"eleven\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEqualTo(%s.of(", ImmutableMap.class)
            .addLine("    5, \"five\", 11, \"eleven\"));")
            .build())
        .runTest();
  }

  @Test
  public void putChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("key must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mutateProperties(map -> map.put(-1, \"minus one\"));")
            .build())
        .runTest();
  }

  @Test
  public void putModifiesUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .mutateProperties(map -> map.put(11, \"eleven\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEqualTo(%s.of(", ImmutableMap.class)
            .addLine("    5, \"five\", 11, \"eleven\"));")
            .build())
        .runTest();
  }

  @Test
  public void iterateEntrySetFindsContainedEntry() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .mutateProperties(map -> {")
            .addLine("      %s<Integer, String> entry = map.entrySet().iterator().next();",
                Map.Entry.class)
            .addLine("      assertThat(entry.getKey()).isEqualTo(5);")
            .addLine("      assertThat(entry.getValue()).isEqualTo(\"five\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void callRemoveOnEntrySetIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .putProperties(6, \"six\")")
            .addLine("    .mutateProperties(map -> {")
            .addLine("        %s<%s<Integer, String>> i = map.entrySet().iterator();",
                Iterator.class, Map.Entry.class)
            .addLine("        i.next();")
            .addLine("        i.remove();")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEqualTo(%s.of(", ImmutableMap.class)
            .addLine("    6, \"six\"));")
            .build())
        .runTest();
  }

  @Test
  public void entrySetIteratorRemainsUsableAfterCallingRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .putProperties(6, \"six\")")
            .addLine("    .mutateProperties(map -> {")
            .addLine("        %s<%s<Integer, String>> i = map.entrySet().iterator();",
                Iterator.class, Map.Entry.class)
            .addLine("        %s<Integer, String> entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(5);")
            .addLine("        assertThat(entry.getValue()).isEqualTo(\"five\");")
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        i.remove();")
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(6);")
            .addLine("        assertThat(entry.getValue()).isEqualTo(\"six\");")
            .addLine("        assertThat(i.hasNext()).isFalse();")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnEntryChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value must not start with '-'");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .mutateProperties(map ->")
            .addLine("        map.entrySet().iterator().next().setValue(\"-five-\"));")
            .build())
        .runTest();
  }

  @Test
  public void callSetValueOnEntryModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .mutateProperties(map ->")
            .addLine("        map.entrySet().iterator().next().setValue(\"cinco\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEqualTo(%s.of(", ImmutableMap.class)
            .addLine("    5, \"cinco\"));")
            .build())
        .runTest();
  }

  @Test
  public void entryRemainsUsableAfterCallingSetValue() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .putProperties(6, \"six\")")
            .addLine("    .mutateProperties(map -> {")
            .addLine("        %s<%s<Integer, String>> i = map.entrySet().iterator();",
                Iterator.class, Map.Entry.class)
            .addLine("        %s<Integer, String> entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(5);")
            .addLine("        assertThat(entry.getValue()).isEqualTo(\"five\");")
            .addLine("        entry.setValue(\"cinco\");")
            .addLine("        assertThat(entry.getKey()).isEqualTo(5);")
            .addLine("        assertThat(entry.getValue()).isEqualTo(\"cinco\");")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void entrySetIteratorRemainsUsableAfterCallingSetValue() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .putProperties(6, \"six\")")
            .addLine("    .mutateProperties(map -> {")
            .addLine("        %s<%s<Integer, String>> i = map.entrySet().iterator();",
                Iterator.class, Map.Entry.class)
            .addLine("        %s<Integer, String> entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(5);")
            .addLine("        assertThat(entry.getValue()).isEqualTo(\"five\");")
            .addLine("        entry.setValue(\"cinco\");")
            .addLine("        assertThat(i.hasNext()).isTrue();")
            .addLine("        entry = i.next();", Map.Entry.class)
            .addLine("        assertThat(entry.getKey()).isEqualTo(6);")
            .addLine("        assertThat(entry.getValue()).isEqualTo(\"six\");")
            .addLine("        assertThat(i.hasNext()).isFalse();")
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void getReturnsContainedValue() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .mutateProperties(map -> assertThat(map.get(5)).isEqualTo(\"five\"))")
            .addLine("    .mutateProperties(map -> assertThat(map.get(3)).isEqualTo(null));")
            .build())
        .runTest();
  }

  @Test
  public void containsKeyFindsContainedKey() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .mutateProperties(map -> assertThat(map.containsKey(5)).isTrue())")
            .addLine("    .mutateProperties(map -> assertThat(map.containsKey(3)).isFalse());")
            .build())
        .runTest();
  }

  @Test
  public void removeModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .putProperties(6, \"six\")")
            .addLine("    .mutateProperties(map -> map.remove(5))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEqualTo(%s.of(", ImmutableMap.class)
            .addLine("    6, \"six\"));")
            .build())
        .runTest();
  }

  @Test
  public void clearModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .putProperties(5, \"five\")")
            .addLine("    .putProperties(6, \"six\")")
            .addLine("    .mutateProperties(%s::clear)", Map.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).isEmpty();")
            .build())
        .runTest();
  }

}
