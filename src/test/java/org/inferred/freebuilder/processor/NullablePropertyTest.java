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

import static org.inferred.freebuilder.processor.ElementFactory.TYPES;

import com.google.common.collect.Lists;
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
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;
import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class NullablePropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "@Nullable {0}, {1}, {2}")
  public static Iterable<Object[]> parameters() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(TYPES, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory element;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject oneProperty;
  private final JavaFileObject twoProperties;

  public NullablePropertyTest(
      ElementFactory element,
      NamingConvention convention,
      FeatureSet features) {
    this.element = element;
    this.convention = convention;
    this.features = features;

    oneProperty = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  @%s %s %s;", Nullable.class, element.type(), convention.get("item"))
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("}")
        .build();

    twoProperties = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  @%s %s %s;", Nullable.class, element.type(), convention.get("item1"))
        .addLine("  @%s %s %s;", Nullable.class, element.type(), convention.get("item2"))
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {}")
        .addLine("}")
        .build();
  }

  @Test
  public void testConstructor_defaultAbsent() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertEquals(null, value.%s);", convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_defaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("assertEquals(null, builder.%s);", convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetter_nonDefaultValue() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .%s(%s);", convention.set("item"), element.example(0))
            .addLine("assertEquals(%s, (%s) builder.%s);",
                element.example(0), element.unwrappedType(), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testSet_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .build();")
            .addLine("assertEquals(%s, (%s) value.%s);",
                element.example(0), element.unwrappedType(), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testSet_null() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(null)", convention.set("item"))
            .addLine("    .build();")
            .addLine("assertEquals(null, value.%s);", convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .build();")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(%s, (%s) builder.%s);",
                element.example(0), element.unwrappedType(), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .%s(%s);", convention.set("item"), element.example(0))
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(%s, (%s) builder.%s);",
                element.example(0), element.unwrappedType(), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.%s);", convention.get("item"))
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
            .addLine("public interface DataType {")
            .addLine("  @%s %s %s;", Nullable.class, element.type(), convention.get("item"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    public Builder() {")
            .addLine("      %s(%s);", convention.set("item"), element.example(0))
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(1))
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s, (%s) value.%s);",
                element.example(0), element.unwrappedType(), convention.get("item"))
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
            .addLine("public interface DataType {")
            .addLine("  @%s %s %s;", Nullable.class, element.type(), convention.get("item"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {")
            .addLine("    public Builder(%s s) {", element.type())
            .addLine("      %s(s);", convention.set("item"))
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder(%s)", element.example(0))
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(null, value.%s);", convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .%s(null)", convention.set("item"))
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .%s(%s)", convention.set("item"), element.example(0))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .%s(%s)", convention.set("item"), element.example(0))
            .addLine("            .build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_singleField() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType absent = new DataType.Builder()")
            .addLine("    .build();")
            .addLine("DataType present = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", absent.toString());")
            .addLine("assertEquals(\"DataType{item=\" + %s + \"}\", present.toString());",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testValueToString_twoFields() {
    behaviorTester
        .with(new Processor(features))
        .with(twoProperties)
        .with(testBuilder()
            .addLine("DataType aa = new DataType.Builder()")
            .addLine("    .build();")
            .addLine("DataType pa = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .build();")
            .addLine("DataType ap = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .build();")
            .addLine("DataType pp = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", aa.toString());")
            .addLine("assertEquals(\"DataType{item1=\" + %s + \"}\", pa.toString());",
                element.example(0))
            .addLine("assertEquals(\"DataType{item2=\" + %s + \"}\", ap.toString());",
                element.example(1))
            .addLine("assertEquals(\"DataType{item1=\" + %s + \", item2=\" + %s + \"}\","
                    + " pp.toString());",
                element.example(0), element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void testPartialToString_singleField() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType absent = new DataType.Builder()")
            .addLine("    .buildPartial();")
            .addLine("DataType present = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", absent.toString());")
            .addLine("assertEquals(\"partial DataType{item=\" + %s + \"}\", present.toString());",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testPartialToString_twoFields() {
    behaviorTester
        .with(new Processor(features))
        .with(twoProperties)
        .with(testBuilder()
            .addLine("DataType aa = new DataType.Builder()")
            .addLine("    .buildPartial();")
            .addLine("DataType pa = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .buildPartial();")
            .addLine("DataType ap = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .buildPartial();")
            .addLine("DataType pp = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", aa.toString());")
            .addLine("assertEquals(\"partial DataType{item1=\" + %s + \"}\", pa.toString());",
                element.example(0))
            .addLine("assertEquals(\"partial DataType{item2=\" + %s + \"}\", ap.toString());",
                element.example(1))
            .addLine("assertEquals(\"partial DataType{item1=\" + %s + \", item2=\" + %s + \"}\","
                    + " pp.toString());",
                element.example(0), element.example(1))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
