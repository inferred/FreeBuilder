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
import org.inferred.freebuilder.processor.util.testing.CompilationException;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(JUnit4.class)
public class ListMutateMethodTest {

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
      .addLine("    @Override void checkProperties(int element) {")
      .addLine("      %s.checkArgument(element >= 0, \"elements must be non-negative\");",
          Preconditions.class)
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject INCORRECTLY_CHECKED_LIST_TYPE = new SourceBuilder()
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

  private static final JavaFileObject DOUBLE_CHECKED_LIST_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperties();", List.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override void checkProperties(int element) {")
      .addLine("      %s.checkArgument(element >= 0, \"elements must be non-negative\");",
          Preconditions.class)
      .addLine("    }")
      .addLine("    @Override public Builder addProperties(int element) {")
      .addLine("      %s.checkArgument(element >= 0, \"elements must be non-negative\");",
          Preconditions.class)
      .addLine("      return super.addProperties(element);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final BehaviorTester behaviorTester = new BehaviorTester();

  @Test
  public void mutateAndAddModifiesUnderlyingPropertyWhenUnchecked() {
    behaviorTester
        .with(new Processor())
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
        .with(new Processor())
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
        .with(new Processor())
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().mutateProperties(map -> map.add(-3));")
            .build())
        .runTest();
  }

  @Test
  public void warningIssuedWhenIncorrectMethodOverridden() {
    behaviorTester
        .with(new Processor())
        .with(INCORRECTLY_CHECKED_LIST_TYPE)
        .compiles()
        .withWarning("Overriding add methods on @FreeBuilder types is deprecated; please override "
            + "checkProperties instead");
  }

  @Test
  public void mutateMethodDoesNotExistWhenIncorrectMethodOverridden() {
    thrown.expect(CompilationException.class);
    thrown.expectMessage("cannot find symbol");
    behaviorTester
        .with(new Processor())
        .with(INCORRECTLY_CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mutateProperties(map -> map.add(11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateMethodExistsWhenBothMethodsOverridden() {
    behaviorTester
        .with(new Processor())
        .with(DOUBLE_CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mutateProperties(map -> map.add(11))")
            .addLine("    .build();")
            .addLine("assertThat(value.getProperties()).containsExactly(11);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetModifiesUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor())
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
        .with(new Processor())
        .with(CHECKED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .addProperties(1, 2, 3)")
            .addLine("    .mutateProperties(map -> map.set(1, -3));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReadsFromUnderlyingPropertyWhenChecked() {
    behaviorTester
        .with(new Processor())
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
        .with(new Processor())
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
        .with(new Processor())
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
        .with(new Processor())
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
        .with(new Processor())
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
