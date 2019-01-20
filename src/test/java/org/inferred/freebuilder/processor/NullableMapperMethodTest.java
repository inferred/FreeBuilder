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

import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
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
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class NullableMapperMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}, {1}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final NamingConvention convention;
  private final FeatureSet features;
  private final CompilationUnitBuilder nullableIntegerType;

  public NullableMapperMethodTest(NamingConvention convention, FeatureSet features) {
    this.convention = convention;
    this.features = features;
    nullableIntegerType = CompilationUnitBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  @%s Integer %s;", Nullable.class, convention.get("property"))
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("}");
  }

  @Test
  public void replacesValueToBeReturnedFromGetter() {
    behaviorTester
        .with(new Processor(features))
        .with(nullableIntegerType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("property"))
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, (int) value.%s);", convention.get("property"))
            .build())
        .runTest();
  }

  @Test
  public void throwsNpeIfMapperIsNull() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(nullableIntegerType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("property"))
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void throwsNpeIfMapperIsNullForUnsetNullableProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(nullableIntegerType)
        .with(testBuilder().addLine("new DataType.Builder().mapProperty(null);").build())
        .runTest();
  }

  @Test
  public void allowsNullReturn() {
    behaviorTester
        .with(new Processor(features))
        .with(nullableIntegerType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("property"))
            .addLine("    .mapProperty(a -> null)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isNull();", convention.get("property"))
            .build())
        .runTest();
  }

  @Test
  public void skipsMapperIfNullablePropertyIsUnset() {
    behaviorTester
        .with(new Processor(features))
        .with(nullableIntegerType)
        .with(testBuilder()
            .addLine("new DataType.Builder().mapProperty(a -> {")
            .addLine("  throw new AssertionError(\"shouldn't hit this\");")
            .addLine("});")
            .build())
        .runTest();
  }

  @Test
  public void delegatesToSetterForValidation() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s Integer %s;", Nullable.class, convention.get("property"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder %s(@%s Integer property) {",
                convention.set("property"), Nullable.class)
            .addLine("      if (property != null && property < 0) {")
            .addLine("        throw new IllegalArgumentException(")
            .addLine("              \"property must be non-negative\");")
            .addLine("      }")
            .addLine("      return super.%s(property);", convention.set("property"))
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("property"))
            .addLine("    .mapProperty(a -> -3);")
            .build())
        .runTest();
  }

  @Test
  public void mapCanAcceptGenericFunctionalInterface() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s Integer %s;", Nullable.class, convention.get("property"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder mapProperty(%s<Integer> mapper) {",
                UnaryOperator.class)
            .addLine("      return super.mapProperty(mapper);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("property"))
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, (int) value.%s);", convention.get("property"))
            .build())
        .runTest();
  }

  @Test
  public void mapCanAcceptPrimitiveFunctionalInterface() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s Integer %s;", Nullable.class, convention.get("property"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder mapProperty(%s mapper) {",
                IntUnaryOperator.class)
            .addLine("      return super.mapProperty(mapper);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(11)", convention.set("property"))
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, (int) value.%s);", convention.get("property"))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
