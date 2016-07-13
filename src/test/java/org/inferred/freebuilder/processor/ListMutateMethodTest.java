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
public class ListMutateMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_LAMBDAS;
  }

  private static final JavaFileObject UNCHECKED_LIST_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperties();", List.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject CHECKED_LIST_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperties();", List.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder addProperties(int element) {")
      .addLine("      %s.checkArgument(element >= 0, \"elements must be non-negative\");",
          Preconditions.class)
      .addLine("      return super.addProperties(element);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  /** Simple type that substitutes passed-in objects, in this case by interning strings. */
  private static final JavaFileObject INTERNED_STRINGS_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<String> getProperties();", List.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder addProperties(String element) {")
      .addLine("      return super.addProperties(element.intern());")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void mutateAndAddModifiesUnderlyingPropertyWhenUnchecked() {
    behaviorTester
        .with(new Processor(features))
        .with(UNCHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mutateProperties(map -> map.add(11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddModifiesUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mutateProperties(map -> map.add(11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().mutateProperties(map -> map.add(-3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_STRINGS_TYPE)
        .with(new TestBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mutateProperties(map -> map.add(s))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties().get(0)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex0ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.add(0, 11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(11, 1, 2, 3);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex1ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.add(1, 11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(1, 11, 2, 3);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex2ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.add(2, 11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(1, 2, 11, 3);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndex3ModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.add(3, 11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(1, 2, 3, 11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.add(2, -3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_STRINGS_TYPE)
        .with(new TestBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(\"one\", \"two\", \"three\")")
            .addLine("    .mutateProperties(map -> map.add(2, s))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties().get(2)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetModifiesUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.set(1, 11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(1, 11, 3).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("elements must be non-negative");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.set(1, -3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetAtIndexKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_STRINGS_TYPE)
        .with(new TestBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(\"one\", \"two\", \"three\")")
            .addLine("    .mutateProperties(map -> map.set(2, s))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties().get(2)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReadsFromUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> assertThat(map.size()).isEqualTo(3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndGetReadsFromUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> assertThat(map.get(1)).isEqualTo(2));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndRemoveModifiesUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.remove(1))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(1, 3).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearModifiesUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.clear())")
            .addLine("    .addProperties(4)")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(4);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearSubListModifiesUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3, 4, 5, 6, 7)")
            .addLine("    .mutateProperties(map -> map.subList(1, 5).clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(1, 6, 7).inOrder();")
            .build())
        .runTest();
  }

}
