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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.List;

import javax.annotation.Nullable;
import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class NullableMapperMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_LAMBDAS;
  }

  private static final JavaFileObject NULLABLE_INTEGER_BEAN_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  @%s Integer getProperty();", Nullable.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject NULLABLE_INTEGER_PREFIXLESS_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  @%s Integer property();", Nullable.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void replacesValueToBeReturnedFromGetter_bean() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_BEAN_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, (int) value.getProperty());")
            .build())
        .runTest();
  }

  @Test
  public void delegatesToSetterForValidation_bean() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s Integer getProperty();", Nullable.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setProperty(@%s Integer property) {",
                Nullable.class)
            .addLine("      %s.checkArgument(property == null || property >= 0,",
                Preconditions.class)
            .addLine("              \"property must be non-negative\");")
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
  public void throwsNpeIfMapperIsNull_bean() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_BEAN_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void throwsNpeIfMapperIsNullForUnsetNullableProperty_bean() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_BEAN_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void allowsNullReturn_bean() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_BEAN_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> null)")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperty()).isNull();")
            .build())
        .runTest();
  }

  @Test
  public void skipsMapperIfNullablePropertyIsUnset_bean() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_BEAN_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().mapProperty(a -> {")
            .addLine("  throw new AssertionError(\"shouldn't hit this\");")
            .addLine("});")
            .build())
        .runTest();
  }

  @Test
  public void replacesValueToBeReturnedFromGetter_prefixless() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_PREFIXLESS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .property(11)")
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, (int) value.property());")
            .build())
        .runTest();
  }

  @Test
  public void delegatesToSetterForValidation_prefixless() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s Integer property();", Nullable.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder property(@%s Integer property) {",
                Nullable.class)
            .addLine("      %s.checkArgument(property == null || property >= 0,",
                Preconditions.class)
            .addLine("              \"property must be non-negative\");")
            .addLine("      return super.property(property);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .property(11)")
            .addLine("    .mapProperty(a -> -3);")
            .build())
        .runTest();
  }

  @Test
  public void throwsNpeIfMapperIsNull_prefixless() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_PREFIXLESS_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .property(11)")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void throwsNpeIfMapperIsNullForUnsetNullableProperty_prefixless() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_PREFIXLESS_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void allowsNullReturn_prefixless() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_PREFIXLESS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .property(11)")
            .addLine("    .mapProperty(a -> null)")
            .addLine("    .build();")
            .addLine("assertThat(value.property()).isNull();")
            .build())
        .runTest();
  }

  @Test
  public void skipsMapperIfNullablePropertyIsUnset_prefixless() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_PREFIXLESS_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().mapProperty(a -> {")
            .addLine("  throw new AssertionError(\"shouldn't hit this\");")
            .addLine("});")
            .build())
        .runTest();
  }
}
