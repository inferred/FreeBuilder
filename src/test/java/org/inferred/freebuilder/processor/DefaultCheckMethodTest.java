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

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.tools.JavaFileObject;

@RunWith(JUnit4.class)
public class DefaultCheckMethodTest {

  private static final JavaFileObject CHECKED_PRIMITIVE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  int getValue();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    static void checkValue(int value) {")
      .addLine("      %s.checkArgument(value >= 0, \"value must be nonnegative\");",
          Preconditions.class)
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject CHECKED_STRING = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  String getValue();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    static void checkValue(String value) {")
      .addLine("      if (value == null) throw new AssertionError(\"null passed to checkValue\");")
      .addLine("      %s.checkArgument(!value.isEmpty(), \"value must not be empty\");",
          Preconditions.class)
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final BehaviorTester behaviorTester = new BehaviorTester();

  @Test
  public void primitiveValueCheckedWithCustomValidation() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value must be nonnegative");
    behaviorTester
        .with(new Processor())
        .with(CHECKED_PRIMITIVE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().setValue(-3);")
            .build())
        .runTest();
  }

  @Test
  public void referenceCheckedWithCustomValidation() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value must not be empty");
    behaviorTester
        .with(new Processor())
        .with(CHECKED_STRING)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().setValue(\"\");")
            .build())
        .runTest();
  }

  @Test
  public void customValidationNeverPassedNull() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(CHECKED_STRING)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().setValue(null);")
            .build())
        .runTest();
  }

}
