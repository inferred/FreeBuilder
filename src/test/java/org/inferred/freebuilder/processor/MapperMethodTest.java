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
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.annotation.Nullable;
import javax.tools.JavaFileObject;

@RunWith(JUnit4.class)
public class MapperMethodTest {

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

  private static final JavaFileObject NULLABLE_INTEGER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  @%s Integer getProperty();", Nullable.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject J8_OPTIONAL_INTEGER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperty();", java.util.Optional.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject GUAVA_OPTIONAL_INTEGER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  %s<Integer> getProperty();", com.google.common.base.Optional.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final BehaviorTester behaviorTester = new BehaviorTester();

  @Test
  public void mapReplacesValueToBeReturnedFromGetterForRequiredProperty() {
    behaviorTester
        .with(new Processor())
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
        .with(new Processor())
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
  public void mapReplacesValueToBeReturnedFromGetterForNullableProperty() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_TYPE)
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
  public void mapReplacesValueToBeReturnedFromGetterForJ8OptionalProperty() {
    behaviorTester
        .with(new Processor())
        .with(J8_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, (int) value.getProperty().get());")
            .build())
        .runTest();
  }

  @Test
  public void mapReplacesValueToBeReturnedFromGetterForGuavaOptionalProperty() {
    behaviorTester
        .with(new Processor())
        .with(GUAVA_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> a + 3)")
            .addLine("    .build();")
            .addLine("assertEquals(14, (int) value.getProperty().get());")
            .build())
        .runTest();
  }

  @Test
  public void mapDelegatesToSetterForValidationForRequiredProperty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor())
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
        .with(new Processor())
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
  public void mapDelegatesToSetterForValidationForNullableProperty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor())
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
  public void mapDelegatesToSetterForValidationForJ8OptionalProperty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<Integer> getProperty();", java.util.Optional.class)
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
  public void mapDelegatesToSetterForValidationForGuavaOptionalProperty() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("property must be non-negative");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<Integer> getProperty();", com.google.common.base.Optional.class)
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
  public void mapThrowsNpeIfMapperIsNullForRequiredProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
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
        .with(new Processor())
        .with(DEFAULT_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForNullableProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForJ8OptionalProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(J8_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForGuavaOptionalProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(GUAVA_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForUnsetRequiredProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(REQUIRED_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForUnsetNullableProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForEmptyJ8OptionalProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(J8_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullForAbsentGuavaOptionalProperty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(GUAVA_OPTIONAL_INTEGER_TYPE)
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
        .with(new Processor())
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
        .with(new Processor())
        .with(DEFAULT_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> null);")
            .build())
        .runTest();
  }

  @Test
  public void mapAllowsNullReturnForNullableProperty() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_TYPE)
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
  public void mapAllowsNullReturnForJ8OptionalProperty() {
    behaviorTester
        .with(new Processor())
        .with(J8_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> null)")
            .addLine("    .build();")
            .addLine("assertFalse(value.getProperty().isPresent());")
            .build())
        .runTest();
  }

  @Test
  public void mapAllowsNullReturnForGuavaOptionalProperty() {
    behaviorTester
        .with(new Processor())
        .with(GUAVA_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setProperty(11)")
            .addLine("    .mapProperty(a -> null)")
            .addLine("    .build();")
            .addLine("assertFalse(value.getProperty().isPresent());")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsIllegalStateExceptionIfRequiredPropertyIsUnset() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("property not set");
    behaviorTester
        .with(new Processor())
        .with(REQUIRED_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> 14);")
            .build())
        .runTest();
  }

  @Test
  public void mapSkipsMapperIfNullablePropertyIsUnset() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().mapProperty(a -> {")
            .addLine("  throw new AssertionError(\"shouldn't hit this\");")
            .addLine("});")
            .build())
        .runTest();
  }

  @Test
  public void mapSkipsMapperIfJ8OptionalPropertyIsEmpty() {
    behaviorTester
        .with(new Processor())
        .with(J8_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> { fail(\"mapper called\"); return null; })")
            .addLine("    .build();")
            .addLine("assertFalse(value.getProperty().isPresent());")
            .build())
        .runTest();
  }

  @Test
  public void mapSkipsMapperIfGuavaOptionalPropertyIsAbsent() {
    behaviorTester
        .with(new Processor())
        .with(GUAVA_OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mapProperty(a -> { fail(\"mapper called\"); return null; })")
            .addLine("    .build();")
            .addLine("assertFalse(value.getProperty().isPresent());")
            .build())
        .runTest();
  }

}
