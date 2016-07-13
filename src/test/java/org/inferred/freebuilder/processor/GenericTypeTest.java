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
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTestRunner.Shared;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.List;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class GenericTypeTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  @Parameter public FeatureSet features;

  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testGenericInterface() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType<A, B> {")
            .addLine("  A getPropertyA();")
            .addLine("  B getPropertyB();")
            .addLine("")
            .addLine("  public static class Builder<A, B> extends DataType_Builder<A, B> {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType<Integer, Boolean> value =")
            .addLine("    new com.example.DataType.Builder<Integer, Boolean>()")
            .addLine("        .setPropertyA(11)")
            .addLine("        .setPropertyB(true)")
            .addLine("        .build();")
            .addLine("assertEquals(11, (int) value.getPropertyA());")
            .addLine("assertTrue(value.getPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testGenericInterface_compilesWithoutWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType<A, B> {")
            .addLine("  A getPropertyA();")
            .addLine("  B getPropertyB();")
            .addLine("")
            .addLine("  public static class Builder<A, B> extends DataType_Builder<A, B> {}")
            .addLine("}")
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testBoundedParameters() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType<A extends Number, B extends Number> {")
            .addLine("  A getPropertyA();")
            .addLine("  B getPropertyB();")
            .addLine("")
            .addLine("  public static class Builder<A extends Number, B extends Number>")
            .addLine("      extends DataType_Builder<A, B> {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType<Integer, Double> value =")
            .addLine("    new com.example.DataType.Builder<Integer, Double>()")
            .addLine("        .setPropertyA(11)")
            .addLine("        .setPropertyB(3.2)")
            .addLine("        .build();")
            .addLine("assertEquals(11, (int) value.getPropertyA());")
            .addLine("assertEquals(3.2, (double) value.getPropertyB(), 0.001);")
            .build())
        .runTest();
  }

}
