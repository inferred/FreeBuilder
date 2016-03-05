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
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

import javax.tools.JavaFileObject;

@RunWith(JUnit4.class)
public class MapMutateMethodTest {

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
      .addLine("      return super.putProperties(key, value);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final BehaviorTester behaviorTester = new BehaviorTester();

  @Test
  public void mutateAndPutModifiesUnderlyingProperty() {
    com.google.common.collect.ImmutableMap.of(5, "five", 11, "eleven");
    behaviorTester
        .with(new Processor())
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
  public void mutateAndPutDelegatesToPutMethodForValidation() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("key must be non-negative");
    behaviorTester
        .with(new Processor())
        .with(CHECKED_SET_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mutateProperties(map -> map.put(-1, \"minus one\"));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndIterateEntrySetFindsContainedEntry() {
    behaviorTester
        .with(new Processor())
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

}
