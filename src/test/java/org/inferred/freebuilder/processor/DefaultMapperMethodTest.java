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

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class DefaultMapperMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_LAMBDAS;
  }

  private static final JavaFileObject REQUIRED_INTEGER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  int getProperty();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject DEFAULT_INTEGER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  int getProperty();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    public Builder() {")
      .addLine("      setProperty(11);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void mapReplacesValueToBeReturnedFromGetterForRequiredProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, value.getProperty());")
            .build())
        .runTest();
  }

  @Test
  public void mapReplacesValueToBeReturnedFromGetterForDefaultProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(DEFAULT_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, value.getProperty());")
            .build())
        .runTest();
  }

  @Test
  public void mapDelegatesToSetterForValidationForRequiredProperty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  int getProperty();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setProperty(int property) {")
            .addLine("      %s.checkArgument(property >= 0, \"property must be non-negative\");",
                Preconditions.class)
            .addLine("      return super.setProperty(property);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> -3);")
            .build())
        .runTest();
  }

  @Test
  public void mapDelegatesToSetterForValidationForDefaultProperty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  int getProperty();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder() {")
            .addLine("      setProperty(11);")
            .addLine("    }")
            .addLine("")
            .addLine("    @Override public Builder setProperty(int property) {")
            .addLine("      %s.checkArgument(property >= 0, \"property must be non-negative\");",
                Preconditions.class)
            .addLine("      return super.setProperty(property);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> -3);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForRequiredProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForDefaultProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(DEFAULT_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForUnsetRequiredProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperReturnsNullForRequiredProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperReturnsNullForDefaultProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(DEFAULT_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsIllegalStateExceptionIfRequiredPropertyIsUnset() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("property not set");
    behaviorTester
        .with(new Processor(features))
        .with(REQUIRED_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> 14);")
            .build())
        .runTest();
  }
}
