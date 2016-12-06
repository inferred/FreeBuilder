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

import java.util.List;
import java.util.Optional;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class RequiredPrefixlessPropertiesTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject REQUIRED_PROPERTIES_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract int propertyA();")
      .addLine("  public abstract boolean propertyB();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject REQUIRED_PROPERTIES_TYPE_NO_TEMPLATE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("import %s;", Optional.class)
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract int propertyA();")
      .addLine("  public abstract boolean propertyB();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    public Builder(Optional<Integer> propertyA, Optional<Boolean> propertyB) {")
      .addLine("      propertyA.ifPresent(this::propertyA);")
      .addLine("      propertyB.ifPresent(this::propertyB);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testCantBuildWithAnUnproperty() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyB]");
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .build();")
            .build())
        .runTest();
  }

  @Test
  public void testCantBuildWithMultipleUnproperties() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyB, propertyD]");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int propertyA();")
            .addLine("  public abstract boolean propertyB();")
            .addLine("  public abstract String propertyC();")
            .addLine("  public abstract int propertyD();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyC(\"\")")
            .addLine("    .build();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(11, builder.propertyA());")
            .addLine("assertTrue(builder.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true);")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(11, builder.propertyA());")
            .addLine("assertTrue(builder.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builderIgnoresUnsetField() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .propertyA(11);")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(11, builder.propertyA());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_noTemplate() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE_NO_TEMPLATE)
        .with(testBuilder()
            .addImport(Optional.class)
            .addLine("DataType.Builder template = new DataType")
            .addLine("    .Builder(Optional.of(11), Optional.empty());")
            .addLine("DataType.Builder builder = new DataType")
            .addLine("    .Builder(Optional.empty(), Optional.empty())")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(11, builder.propertyA());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builderUnpropertyGetterThrows() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("propertyB not set");
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .propertyA(11);")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("builder.propertyB();")
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_ignoresUnsetField() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(11, value.propertyA());")
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_unpropertyGetterThrows() {
    thrown.expect(UnsupportedOperationException.class);
    thrown.expectMessage("propertyB not set");
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .buildPartial();")
            .addLine("value.propertyB();")
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_toString() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{propertyA=11}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_toString_twoPrimitiveProperties() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{propertyA=11, propertyB=true}\",")
            .addLine("    value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testClear_noDefaults() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyA, propertyB]");
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetterThrowsIfValueUnset() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("propertyB not set");
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB();")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }

}
