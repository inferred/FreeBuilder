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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
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

/** Behavioral tests for {@code Optional<?>} properties. */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class JavaUtilOptionalPrefixlessPropertyTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_LAMBDAS;
  }

  private static final JavaFileObject TWO_OPTIONAL_PROPERTIES_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String> item1();", Optional.class)
      .addLine("  public abstract %s<String> item2();", Optional.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject OPTIONAL_PROPERTY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String> item();", Optional.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject OPTIONAL_INTEGER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<Integer> item();", Optional.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testConstructor_defaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testConstructor_primitive_defaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_defaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("assertEquals(%s.empty(), builder.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_nonDefaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .item(\"item\");")
            .addLine("assertEquals(%s.of(\"item\"), builder.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"item\"), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().item((String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testSet_optionalOf() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(%s.of(\"item\"))", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"item\"), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_empty() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(%s.<String>empty())", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_nullOptional() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().item((%s<String>) null);",
                Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .nullableItem(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"item\"), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_null() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .nullableItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .clearItem()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(5)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(5), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().item((Integer) null);")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_optionalOf() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(%s.of(5))", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(5), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_empty() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(%s.<Integer>empty())", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_nullOptional() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().item((%s<Integer>) null);",
                Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_primitive_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .nullableItem(5)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(5), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_primitive_null() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .nullableItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testClear_primitive() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(5)")
            .addLine("    .clearItem()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .build();")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(%s.of(\"item\"), builder.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .item(\"item\");")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(%s.of(\"item\"), builder.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance_emptyOptional() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .build();")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(%s.of(\"item\"), builder.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder_emptyOptional() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder();")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(%s.of(\"item\"), builder.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_customDefault() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> item();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder().item(\"default\");")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"default\"), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noBuilderFactory() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> item();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(String s) {")
            .addLine("      item(s);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.empty(), value.item());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_optionalOf() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too long");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> item();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder item(String item) {")
            .addLine("      %s.checkArgument(item.length() <= 10, \"Item too long\");",
                Preconditions.class)
            .addLine("      return super.item(item);")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .item(%s.of(\"very long item\"));", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_nullable() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too long");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> item();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder item(String item) {")
            .addLine("      %s.checkArgument(item.length() <= 10, \"Item too long\");",
                Preconditions.class)
            .addLine("      return super.item(item);")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .nullableItem(\"very long item\");")
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_empty() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> item();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder clearItem() {")
            .addLine("      throw new UnsupportedOperationException(\"Fooled you!\");")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .item(%s.<String>empty());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_null() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> item();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder clearItem() {")
            .addLine("      throw new UnsupportedOperationException(\"Fooled you!\");")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .nullableItem(null);", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_optionalOf() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too big");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> item();", Optional.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder item(int item) {")
            .addLine("      %s.checkArgument(item <= 10, \"Item too big\");", Preconditions.class)
            .addLine("      return super.item(item);")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .item(%s.of(13));", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_nullable() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too big");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> item();", Optional.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder item(int item) {")
            .addLine("      %s.checkArgument(item <= 10, \"Item too big\");", Preconditions.class)
            .addLine("      return super.item(item);")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .nullableItem(13);")
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_empty() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> item();", Optional.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder clearItem() {")
            .addLine("      throw new UnsupportedOperationException(\"Fooled you!\");")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .item(%s.<Integer>empty());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_null() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> item();", Optional.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder clearItem() {")
            .addLine("      throw new UnsupportedOperationException(\"Fooled you!\");")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .nullableItem(null);", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder().build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .item(%s.<String>empty())", Optional.class)
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .nullableItem(null)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .item(\"item\")")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .item(%s.of(\"item\"))", Optional.class)
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_singleField() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType empty = com.example.DataType.builder()")
            .addLine("    .build();")
            .addLine("com.example.DataType present = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", empty.toString());")
            .addLine("assertEquals(\"DataType{item=item}\", present.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_twoFields() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_OPTIONAL_PROPERTIES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType aa = com.example.DataType.builder()")
            .addLine("    .build();")
            .addLine("com.example.DataType pa = com.example.DataType.builder()")
            .addLine("    .item1(\"x\")")
            .addLine("    .build();")
            .addLine("com.example.DataType ap = com.example.DataType.builder()")
            .addLine("    .item2(\"y\")")
            .addLine("    .build();")
            .addLine("com.example.DataType pp = com.example.DataType.builder()")
            .addLine("    .item1(\"x\")")
            .addLine("    .item2(\"y\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", aa.toString());")
            .addLine("assertEquals(\"DataType{item1=x}\", pa.toString());")
            .addLine("assertEquals(\"DataType{item2=y}\", ap.toString());")
            .addLine("assertEquals(\"DataType{item1=x, item2=y}\", pp.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testPartialToString_singleField() {
    behaviorTester
        .with(new Processor(features))
        .with(OPTIONAL_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType empty = com.example.DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType present = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", empty.toString());")
            .addLine("assertEquals(\"partial DataType{item=item}\", present.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testPartialToString_twoFields() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_OPTIONAL_PROPERTIES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType aa = com.example.DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType pa = com.example.DataType.builder()")
            .addLine("    .item1(\"x\")")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType ap = com.example.DataType.builder()")
            .addLine("    .item2(\"y\")")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType pp = com.example.DataType.builder()")
            .addLine("    .item1(\"x\")")
            .addLine("    .item2(\"y\")")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", aa.toString());")
            .addLine("assertEquals(\"partial DataType{item1=x}\", pa.toString());")
            .addLine("assertEquals(\"partial DataType{item2=y}\", ap.toString());")
            .addLine("assertEquals(\"partial DataType{item1=x, item2=y}\", pp.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testWildcardHandling_noWildcard() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s<%s<%s>> items();",
                      Optional.class, ImmutableList.class, Number.class)
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .items(%s.of((%s) 1, 2, 3, 4))", ImmutableList.class, Number.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items().get()).containsExactly(1, 2, 3, 4).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testWildcardHandling_unboundedWildcard() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s<%s<?>> items();",
                      Optional.class, ImmutableList.class)
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .items(%s.of(1, 2, 3, 4))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items().get()).containsExactly(1, 2, 3, 4).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testWildcardHandling_wildcardWithExtendsBound() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s<%s<? extends %s>> items();",
                      Optional.class, ImmutableList.class, Number.class)
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .items(%s.of(1, 2, 3, 4))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.items().get()).containsExactly(1, 2, 3, 4).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testJacksonInteroperability() {
    // See also https://github.com/google/FreeBuilder/issues/68
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("import " + JsonProperty.class.getName() + ";")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public interface DataType {")
            .addLine("  @JsonProperty(\"stuff\") %s<%s> item();", Optional.class, String.class)
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
            .addLine("    .registerModule(new %s());", Jdk8Module.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertEquals(%s.of(\"item\"), clone.item());", Optional.class)
            .build())
        .runTest();
  }
}
