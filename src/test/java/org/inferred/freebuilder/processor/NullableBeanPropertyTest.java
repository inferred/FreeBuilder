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

import com.google.common.testing.EqualsTester;

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
public class NullableBeanPropertyTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

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

  private static final JavaFileObject NULLABLE_PROPERTY_TYPE = new SourceBuilder()
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

  private static final JavaFileObject NULLABLE_INTEGER_TYPE = new SourceBuilder()
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

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testConstructor_defaultAbsent() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testConstructor_primitive_defaultAbsent() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_defaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("assertEquals(null, builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_nonDefaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .setItem(\"item\");")
            .addLine("assertEquals(\"item\", builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"item\", value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_null() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setItem(5)")
            .addLine("    .build();")
            .addLine("assertEquals((Integer) 5, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testSet_primitive_null() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_INTEGER_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setItem(null)")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setItem(\"item\")")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(\"item\", builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .setItem(\"item\");")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(\"item\", builder.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
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
        .with(new Processor(features))
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
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
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
        .with(new Processor(features))
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
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder(\"item\")")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.getItem());")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder().build(),")
            .addLine("        DataType.builder()")
            .addLine("            .setItem(null)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setItem(\"item\")")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .setItem(\"item\")")
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
        .with(testBuilder()
            .addLine("DataType absent = DataType.builder()")
            .addLine("    .build();")
            .addLine("DataType present = DataType.builder()")
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
        .with(new Processor(features))
        .with(TWO_NULLABLE_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType aa = DataType.builder()")
            .addLine("    .build();")
            .addLine("DataType pa = DataType.builder()")
            .addLine("    .setItem1(\"x\")")
            .addLine("    .build();")
            .addLine("DataType ap = DataType.builder()")
            .addLine("    .setItem2(\"y\")")
            .addLine("    .build();")
            .addLine("DataType pp = DataType.builder()")
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
        .with(new Processor(features))
        .with(NULLABLE_PROPERTY_TYPE)
        .with(testBuilder()
            .addLine("DataType absent = DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("DataType present = DataType.builder()")
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
        .with(new Processor(features))
        .with(TWO_NULLABLE_PROPERTIES_TYPE)
        .with(testBuilder()
            .addLine("DataType aa = DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("DataType pa = DataType.builder()")
            .addLine("    .setItem1(\"x\")")
            .addLine("    .buildPartial();")
            .addLine("DataType ap = DataType.builder()")
            .addLine("    .setItem2(\"y\")")
            .addLine("    .buildPartial();")
            .addLine("DataType pp = DataType.builder()")
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

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
