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

import javax.tools.JavaFileObject;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;

/** Behavioral tests for {@code Optional<?>} properties. */
@RunWith(JUnit4.class)
public class OptionalPropertyFactoryTest {

  private static final JavaFileObject TWO_OPTIONAL_PROPERTIES_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<%s> getItem1();", Optional.class, String.class)
      .addLine("  public abstract %s<%s> getItem2();", Optional.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject OPTIONAL_PROPERTY_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<%s> getItem();", Optional.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject OPTIONAL_INTEGER_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<Integer> getItem();", Optional.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  @Rule public final ExpectedException thrown = ExpectedException.none();
  private final BehaviorTester behaviorTester = new BehaviorTester();

  @Test
  public void testConstructor_defaultAbsent() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testConstructor_primitive_defaultAbsent() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_defaultValue() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("assertEquals(%s.absent(), builder.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_nonDefaultValue() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .setItem(\"item\");")
            .addLine("assertEquals(%s.of(\"item\"), builder.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_notNull() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"item\"), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().setItem((String) null);")
            .build())
        .runTest();
  }

  @Test
  public void testSet_optionalOf() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(%s.of(\"item\"))", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"item\"), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_absent() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(%s.<String>absent())", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_nullOptional() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().setItem((%s<String>) null);",
                Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_notNull() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setNullableItem(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"item\"), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_null() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setNullableItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .clearItem()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_notNull() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(5)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(5), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().setItem((Integer) null);")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_optionalOf() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(%s.of(5))", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(5), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_absent() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(%s.<Integer>absent())", Optional.class)
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_nullOptional() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().setItem((%s<Integer>) null);",
                Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_primitive_notNull() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setNullableItem(5)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(5), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_primitive_null() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setNullableItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testClear_primitive() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(5)")
            .addLine("    .clearItem()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .build();")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(%s.of(\"item\"), builder.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .setItem(\"item\");")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(%s.of(\"item\"), builder.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_customDefault() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItem();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder().setItem(\"default\");")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(\"default\"), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_noBuilderFactory() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItem();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(String s) {")
            .addLine("      setItem(s);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.absent(), value.getItem());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_optionalOf() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too long");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItem();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setItem(String item) {")
            .addLine("      %s.checkArgument(item.length() <= 10, \"Item too long\");",
                Preconditions.class)
            .addLine("      return super.setItem(item);")
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
            .addLine("    .setItem(%s.of(\"very long item\"));", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_nullable() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too long");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItem();", Optional.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setItem(String item) {")
            .addLine("      %s.checkArgument(item.length() <= 10, \"Item too long\");",
                Preconditions.class)
            .addLine("      return super.setItem(item);")
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
            .addLine("    .setNullableItem(\"very long item\");")
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_absent() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItem();", Optional.class, String.class)
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
            .addLine("    .setItem(%s.<String>absent());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_null() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<%s> getItem();", Optional.class, String.class)
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
            .addLine("    .setNullableItem(null);", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_optionalOf() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too big");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> getItem();", Optional.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setItem(int item) {")
            .addLine("      %s.checkArgument(item <= 10, \"Item too big\");", Preconditions.class)
            .addLine("      return super.setItem(item);")
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
            .addLine("    .setItem(%s.of(13));", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_nullable() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too big");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> getItem();", Optional.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    @Override public Builder setItem(int item) {")
            .addLine("      %s.checkArgument(item <= 10, \"Item too big\");", Preconditions.class)
            .addLine("      return super.setItem(item);")
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
            .addLine("    .setNullableItem(13);")
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_absent() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> getItem();", Optional.class)
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
            .addLine("    .setItem(%s.<Integer>absent());", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_primitive_null() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract %s<Integer> getItem();", Optional.class)
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
            .addLine("    .setNullableItem(null);", Optional.class)
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder().build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setItem(%s.<String>absent())", Optional.class)
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setNullableItem(null)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setItem(\"item\")")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setItem(%s.of(\"item\"))", Optional.class)
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_singleField() {
    behaviorTester
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType absent = com.example.DataType.builder()")
            .addLine("    .build();")
            .addLine("com.example.DataType present = com.example.DataType.builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", absent.toString());")
            .addLine("assertEquals(\"DataType{item=item}\", present.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_twoFields() {
    behaviorTester
        .with(new Processor())
        .with(TWO_OPTIONAL_PROPERTIES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType aa = com.example.DataType.builder()")
            .addLine("    .build();")
            .addLine("com.example.DataType pa = com.example.DataType.builder()")
            .addLine("    .setItem1(\"x\")")
            .addLine("    .build();")
            .addLine("com.example.DataType ap = com.example.DataType.builder()")
            .addLine("    .setItem2(\"y\")")
            .addLine("    .build();")
            .addLine("com.example.DataType pp = com.example.DataType.builder()")
            .addLine("    .setItem1(\"x\")")
            .addLine("    .setItem2(\"y\")")
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
        .with(new Processor())
        .with(OPTIONAL_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType absent = com.example.DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType present = com.example.DataType.builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", absent.toString());")
            .addLine("assertEquals(\"partial DataType{item=item}\", present.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testPartialToString_twoFields() {
    behaviorTester
        .with(new Processor())
        .with(TWO_OPTIONAL_PROPERTIES_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType aa = com.example.DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType pa = com.example.DataType.builder()")
            .addLine("    .setItem1(\"x\")")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType ap = com.example.DataType.builder()")
            .addLine("    .setItem2(\"y\")")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType pp = com.example.DataType.builder()")
            .addLine("    .setItem1(\"x\")")
            .addLine("    .setItem2(\"y\")")
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
        .with(new Processor())
        .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s<%s<%s>> getItems();",
                      Optional.class, ImmutableList.class, Number.class)
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItems(%s.of((%s) 1, 2, 3, 4))", ImmutableList.class, Number.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().get()).containsExactly(1, 2, 3, 4).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testWildcardHandling_unboundedWildcard() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s<%s<?>> getItems();",
                      Optional.class, ImmutableList.class)
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItems(%s.of(1, 2, 3, 4))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().get()).containsExactly(1, 2, 3, 4).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testWildcardHandling_wildcardWithExtendsBound() {
    behaviorTester
        .with(new Processor())
        .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s<%s<? extends %s>> getItems();",
                      Optional.class, ImmutableList.class, Number.class)
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItems(%s.of(1, 2, 3, 4))", ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().get()).containsExactly(1, 2, 3, 4).inOrder();")
            .build())
        .runTest();
  }
}
