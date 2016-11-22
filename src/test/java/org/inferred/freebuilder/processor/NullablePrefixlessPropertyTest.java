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

import com.google.common.testing.EqualsTester;

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

import javax.annotation.Nullable;
import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class NullablePrefixlessPropertyTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject TWO_NULLABLE_PROPERTIES_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  @%s public abstract %s item1();", Nullable.class, String.class)
      .addLine("  @%s public abstract %s item2();", Nullable.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject NULLABLE_PROPERTY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  @%s public abstract %s item();", Nullable.class, String.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject NULLABLE_INTEGER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  @%s public abstract Integer item();", Nullable.class)
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
  public void testConstructor_defaultAbsent() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(null, value.item());")
            .build())
        .runTest();
  }

  @Test
  public void testConstructor_primitive_defaultAbsent() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(null, value.item());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_defaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("assertEquals(null, builder.item());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_nonDefaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .item(\"item\");")
            .addLine("assertEquals(\"item\", builder.item());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"item\", value.item());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_null() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(null)")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.item());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(5)")
            .addLine("    .build();")
            .addLine("assertEquals((Integer) 5, value.item());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_null() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(null)")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.item());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .build();")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(\"item\", builder.item());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
            .addLine("    .item(\"item\");")
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(\"item\", builder.item());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.item());")
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
            .addLine("  @%s public abstract %s item();", Nullable.class, String.class)
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
            .addLine("assertEquals(\"default\", value.item());")
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
            .addLine("  @%s public abstract %s item();", Nullable.class, String.class)
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
            .addLine("assertEquals(null, value.item());")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder().build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .item(null)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .item(\"item\")")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .item(\"item\")")
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_singleField() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType absent = com.example.DataType.builder()")
            .addLine("    .build();")
            .addLine("com.example.DataType present = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", absent.toString());")
            .addLine("assertEquals(\"DataType{item=item}\", present.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_twoFields() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_NULLABLE_PROPERTIES_TYPE)
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
        .with(NULLABLE_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType absent = com.example.DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType present = com.example.DataType.builder()")
            .addLine("    .item(\"item\")")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", absent.toString());")
            .addLine("assertEquals(\"partial DataType{item=item}\", present.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testPartialToString_twoFields() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_NULLABLE_PROPERTIES_TYPE)
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
}
