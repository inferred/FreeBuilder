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

import javax.annotation.Nullable;
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

import com.google.common.testing.EqualsTester;

@RunWith(JUnit4.class)
public class NullablePropertyTest {

  private static final JavaFileObject TWO_NULLABLE_PROPERTIES_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  @%s public abstract %s getItem1();", Nullable.class, String.class)
      .addLine("  @%s public abstract %s getItem2();", Nullable.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject NULLABLE_PROPERTY_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  @%s public abstract %s getItem();", Nullable.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject NULLABLE_INTEGER_AUTO_BUILT_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  @%s public abstract Integer getItem();", Nullable.class)
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
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testConstructor_primitive_defaultAbsent() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_defaultValue() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("assertEquals(null, builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_nonDefaultValue() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .setItem(\"item\");")
            .addLine("assertEquals(\"item\", builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_notNull() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"item\", value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_null() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_notNull() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(5)")
            .addLine("    .build();")
            .addLine("assertEquals((Integer) 5, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_null() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_INTEGER_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .build();")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(\"item\", builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .setItem(\"item\");")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(\"item\", builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.getItem());")
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
            .addLine("  @%s public abstract %s getItem();", Nullable.class, String.class)
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
            .addLine("assertEquals(\"default\", value.getItem());")
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
            .addLine("  @%s public abstract %s getItem();", Nullable.class, String.class)
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
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder().build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setItem(null)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setItem(\"item\")")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setItem(\"item\")")
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_singleField() {
    behaviorTester
        .with(new Processor())
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(TWO_NULLABLE_PROPERTIES_TYPE)
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
        .with(NULLABLE_PROPERTY_AUTO_BUILT_TYPE)
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
        .with(TWO_NULLABLE_PROPERTIES_TYPE)
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
}
