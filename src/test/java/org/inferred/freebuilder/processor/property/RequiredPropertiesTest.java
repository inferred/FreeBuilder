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

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class RequiredPropertiesTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}, {1}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () ->
        Lists.cartesianProduct(conventions, features).stream().map(List::toArray).iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final NamingConvention convention;
  private final FeatureSet features;

  private final SourceBuilder requiredPropertiesType;

  public RequiredPropertiesTest(NamingConvention convention, FeatureSet features) {
    this.convention = convention;
    this.features = features;

    requiredPropertiesType =
        SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int %s;", convention.get("propertyA"))
            .addLine("  public abstract boolean %s;", convention.is("propertyB"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}");
  }

  @Test
  public void testCantBuildWithAnUnsetProperty() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyB]");
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .build();")
                .build())
        .runTest();
  }

  @Test
  public void testCantBuildWithMultipleUnsetProperties() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyB, propertyD]");
    behaviorTester
        .with(new Processor(features))
        .with(
            SourceBuilder.forTesting()
                .addLine("package com.example;")
                .addLine("@%s", FreeBuilder.class)
                .addLine("public abstract class DataType {")
                .addLine("  public abstract int %s;", convention.get("propertyA"))
                .addLine("  public abstract boolean %s;", convention.is("propertyB"))
                .addLine("  public abstract String %s;", convention.get("propertyC"))
                .addLine("  public abstract int %s;", convention.get("propertyD"))
                .addLine("")
                .addLine("  public static class Builder extends DataType_Builder {}")
                .addLine("}"))
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .%s(\"\")", convention.set("propertyC"))
                .addLine("    .build();")
                .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .%s(true)", convention.set("propertyB"))
                .addLine("    .build();")
                .addLine("DataType.Builder builder = new DataType.Builder()")
                .addLine("    .mergeFrom(value);")
                .addLine("assertEquals(11, builder.%s);", convention.get("propertyA"))
                .addLine("assertTrue(builder.%s);", convention.is("propertyB"))
                .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType.Builder template = new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .%s(true);", convention.set("propertyB"))
                .addLine("DataType.Builder builder = new DataType.Builder()")
                .addLine("    .mergeFrom(template);")
                .addLine("assertEquals(11, builder.%s);", convention.get("propertyA"))
                .addLine("assertTrue(builder.%s);", convention.is("propertyB"))
                .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builderIgnoresUnsetField() {
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType.Builder template = new DataType.Builder()")
                .addLine("    .%s(11);", convention.set("propertyA"))
                .addLine("DataType.Builder builder = new DataType.Builder()")
                .addLine("    .mergeFrom(template);")
                .addLine("assertEquals(11, builder.%s);", convention.get("propertyA"))
                .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_noTemplate() {
    behaviorTester
        .with(new Processor(features))
        .with(
            SourceBuilder.forTesting()
                .addLine("package com.example;")
                .addLine("@%s", FreeBuilder.class)
                .addLine("public abstract class DataType {")
                .addLine("  public abstract int %s;", convention.get("propertyA"))
                .addLine("  public abstract boolean %s;", convention.is("propertyB"))
                .addLine("")
                .addLine("  public static class Builder extends DataType_Builder {")
                .addLine("    public Builder(Integer propertyA, Boolean propertyB) {")
                .addLine("      if (propertyA != null) {")
                .addLine("        this.%s(propertyA);", convention.set("propertyA"))
                .addLine("      }")
                .addLine("      if (propertyB != null) {")
                .addLine("        this.%s(propertyB);", convention.set("propertyB"))
                .addLine("      }")
                .addLine("    }")
                .addLine("  }")
                .addLine("}"))
        .with(
            testBuilder()
                .addLine("DataType.Builder template = new DataType")
                .addLine("    .Builder(11, null);")
                .addLine("DataType.Builder builder = new DataType")
                .addLine("    .Builder(null, null)")
                .addLine("    .mergeFrom(template);")
                .addLine("assertEquals(11, builder.%s);", convention.get("propertyA"))
                .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builderUnsetPropertyGetterThrows() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("propertyB not set");
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType.Builder template = new DataType.Builder()")
                .addLine("    .%s(11);", convention.set("propertyA"))
                .addLine("DataType.Builder builder = new DataType.Builder()")
                .addLine("    .mergeFrom(template);")
                .addLine("builder.%s;", convention.is("propertyB"))
                .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_ignoresUnsetField() {
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .buildPartial();")
                .addLine("assertEquals(11, value.%s);", convention.get("propertyA"))
                .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_unsetPropertyGetterThrows() {
    thrown.expect(UnsupportedOperationException.class);
    thrown.expectMessage("propertyB not set");
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .buildPartial();")
                .addLine("value.%s;", convention.is("propertyB"))
                .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_toString() {
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .buildPartial();")
                .addLine("assertEquals(\"partial DataType{propertyA=11}\", value.toString());")
                .build())
        .runTest();
  }

  @Test
  public void testBuildPartial_toString_twoPrimitiveProperties() {
    behaviorTester
        .with(new Processor(features))
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("DataType value = new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .%s(true)", convention.set("propertyB"))
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
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .%s(true)", convention.set("propertyB"))
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
        .with(requiredPropertiesType)
        .with(
            testBuilder()
                .addLine("new DataType.Builder()")
                .addLine("    .%s(11)", convention.set("propertyA"))
                .addLine("    .%s;", convention.is("propertyB"))
                .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
