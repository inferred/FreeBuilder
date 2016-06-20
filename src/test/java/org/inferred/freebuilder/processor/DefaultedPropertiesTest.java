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
public class DefaultedPropertiesTest {

  private static final JavaFileObject DEFAULTED_PROPERTIES_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  int getPropertyA();")
      .addLine("  boolean isPropertyB();")
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {")
      .addLine("    public Builder() {")
      .addLine("      setPropertyA(0);")
      .addLine("      setPropertyB(false);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final BehaviorTester behaviorTester = new BehaviorTester();

  @Test
  public void testMergeFromBuilder_defaultsDoNotOverride() {
    behaviorTester
        .with(new Processor())
        .with(DEFAULTED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_defaultsDoNotOverride() {
    behaviorTester
        .with(new Processor())
        .with(DEFAULTED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder().build())")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nonDefaultsUsed() {
    behaviorTester
        .with(new Processor())
        .with(DEFAULTED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("        .setPropertyA(13)")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nonDefaultsUsed() {
    behaviorTester
        .with(new Processor())
        .with(DEFAULTED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .setPropertyA(13)")
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nonDefaultsOverride() {
    behaviorTester
        .with(new Processor())
        .with(DEFAULTED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("        .setPropertyA(13)")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nonDefaultsOverride() {
    behaviorTester
        .with(new Processor())
        .with(DEFAULTED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .setPropertyA(13)")
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor())
        .with(DEFAULTED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(0, value.getPropertyA());")
            .addLine("assertFalse(value.isPropertyB());")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }

}
