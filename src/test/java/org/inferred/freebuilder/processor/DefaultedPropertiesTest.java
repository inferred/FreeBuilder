/*
 * Copyright 2016 Google Inc. All rights reserved.
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
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Arrays;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class DefaultedPropertiesTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}, optimized={1}, {2}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<Boolean> optimized = ImmutableList.of(false, true);
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(conventions, optimized, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject dataType;

  public DefaultedPropertiesTest(
      NamingConvention convention,
      boolean optimized,
      FeatureSet features) {
    this.convention = convention;
    this.features = features;

    SourceBuilder dataTypeBuilder = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  int %s;", convention.get("propertyA"))
        .addLine("  boolean %s;", convention.is("propertyB"))
        .addLine("  String %s;", convention.get("propertyC"))
        .addLine("  double %s;", convention.get("propertyD"))
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {")
        .addLine("    public Builder() {");
    if (!optimized) {
      // Test that defaults work correctly when we cannot detect them at compile-time.
      dataTypeBuilder.addLine("if (true) {  // Disable optimization in javac");
    }
    dataTypeBuilder
        .addLine("      %s(0);", convention.set("propertyA"))
        .addLine("      %s(false);", convention.set("propertyB"))
        .addLine("      %s(\"default\");", convention.set("propertyC"))
        .addLine("      %s(Double.NaN);", convention.set("propertyD"));
    if (!optimized) {
      dataTypeBuilder.addLine("}");
    }
    dataType = dataTypeBuilder
        .addLine("    }")
        .addLine("  }")
        .addLine("}")
        .build();
  }

  @Test
  public void testMergeFromBuilder_defaultsDoNotOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("propertyA"))
            .addLine("    .%s(true)", convention.set("propertyB"))
            .addLine("    .%s(\"hello\")", convention.set("propertyC"))
            .addLine("    .%s(3.2)", convention.set("propertyD"))
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.%s);", convention.get("propertyA"))
            .addLine("assertTrue(value.%s);", convention.is("propertyB"))
            .addLine("assertEquals(\"hello\", value.%s);", convention.get("propertyC"))
            .addLine("assertEquals(3.2, value.%s, 0.0001);", convention.get("propertyD"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_defaultsDoNotOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("propertyA"))
            .addLine("    .%s(true)", convention.set("propertyB"))
            .addLine("    .%s(\"hello\")", convention.set("propertyC"))
            .addLine("    .%s(3.2)", convention.set("propertyD"))
            .addLine("    .mergeFrom(new DataType.Builder().build())")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.%s);", convention.get("propertyA"))
            .addLine("assertTrue(value.%s);", convention.is("propertyB"))
            .addLine("assertEquals(\"hello\", value.%s);", convention.get("propertyC"))
            .addLine("assertEquals(3.2, value.%s, 0.0001);", convention.get("propertyD"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nonDefaultsUsed() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(true)", convention.set("propertyB"))
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .%s(13)", convention.set("propertyA"))
            .addLine("        .%s(\"hello\")", convention.set("propertyC"))
            .addLine("        .%s(3.2))", convention.set("propertyD"))
            .addLine("    .build();")
            .addLine("assertEquals(13, value.%s);", convention.get("propertyA"))
            .addLine("assertTrue(value.%s);", convention.is("propertyB"))
            .addLine("assertEquals(\"hello\", value.%s);", convention.get("propertyC"))
            .addLine("assertEquals(3.2, value.%s, 0.0001);", convention.get("propertyD"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nonDefaultsUsed() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(true)", convention.set("propertyB"))
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .%s(13)", convention.set("propertyA"))
            .addLine("        .%s(\"hello\")", convention.set("propertyC"))
            .addLine("        .%s(3.2)", convention.set("propertyD"))
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.%s);", convention.get("propertyA"))
            .addLine("assertTrue(value.%s);", convention.is("propertyB"))
            .addLine("assertEquals(\"hello\", value.%s);", convention.get("propertyC"))
            .addLine("assertEquals(3.2, value.%s, 0.0001);", convention.get("propertyD"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nonDefaultsOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("propertyA"))
            .addLine("    .%s(true)", convention.set("propertyB"))
            .addLine("    .%s(\"hello\")", convention.set("propertyC"))
            .addLine("    .%s(1.9)", convention.set("propertyD"))
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .%s(13)", convention.set("propertyA"))
            .addLine("        .%s(\"goodbye\")", convention.set("propertyC"))
            .addLine("        .%s(3.2))", convention.set("propertyD"))
            .addLine("    .build();")
            .addLine("assertEquals(13, value.%s);", convention.get("propertyA"))
            .addLine("assertTrue(value.%s);", convention.is("propertyB"))
            .addLine("assertEquals(\"goodbye\", value.%s);", convention.get("propertyC"))
            .addLine("assertEquals(3.2, value.%s, 0.0001);", convention.get("propertyD"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nonDefaultsOverride() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("propertyA"))
            .addLine("    .%s(true)", convention.set("propertyB"))
            .addLine("    .%s(\"hello\")", convention.set("propertyC"))
            .addLine("    .%s(1.9)", convention.set("propertyD"))
            .addLine("    .mergeFrom(new DataType.Builder()")
            .addLine("        .%s(13)", convention.set("propertyA"))
            .addLine("        .%s(\"goodbye\")", convention.set("propertyC"))
            .addLine("        .%s(3.2)", convention.set("propertyD"))
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(13, value.%s);", convention.get("propertyA"))
            .addLine("assertTrue(value.%s);", convention.is("propertyB"))
            .addLine("assertEquals(\"goodbye\", value.%s);", convention.get("propertyC"))
            .addLine("assertEquals(3.2, value.%s, 0.0001);", convention.get("propertyD"))
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("propertyA"))
            .addLine("    .%s(true)", convention.set("propertyB"))
            .addLine("    .%s(\"hello\")", convention.set("propertyC"))
            .addLine("    .%s(1.9)", convention.set("propertyD"))
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(0, value.%s);", convention.get("propertyA"))
            .addLine("assertFalse(value.%s);", convention.is("propertyB"))
            .addLine("assertEquals(\"default\", value.%s);", convention.get("propertyC"))
            .addLine("assertEquals(Double.NaN, value.%s, 0.0001);", convention.get("propertyD"))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
