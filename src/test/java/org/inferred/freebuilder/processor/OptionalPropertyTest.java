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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
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

import javax.tools.JavaFileObject;

/** Behavioral tests for {@code Optional<?>} properties. */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class OptionalPropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}<{1}>, {2}, {3}")
  public static Iterable<Object[]> parameters() {
    List<Class<?>> optionals = Arrays.asList(
        java.util.Optional.class,
        com.google.common.base.Optional.class);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(optionals, TYPES, conventions, features)
        .stream()
        .filter(parameters -> {
          Class<?> optional = (Class<?>) parameters.get(0);
          FeatureSet featureSet = (FeatureSet) parameters.get(3);
          if (optional.equals(com.google.common.base.Optional.class)
              && !featureSet.get(GuavaLibrary.GUAVA).isAvailable()) {
            return false;
          }
          return true;
        })
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final Class<?> optional;
  private final ElementFactory element;
  private final NamingConvention convention;
  private final FeatureSet features;
  private final String empty;

  private final JavaFileObject oneProperty;
  private final JavaFileObject twoProperties;
  private final JavaFileObject validatedProperty;

  public OptionalPropertyTest(
      Class<?> optional,
      ElementFactory element,
      NamingConvention convention,
      FeatureSet features) {
    this.optional = optional;
    this.element = element;
    this.convention = convention;
    this.features = features;
    this.empty = optional.getName().startsWith("com.google") ? "absent" : "empty";

    oneProperty = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract %s<%s> %s;",
            optional, element.type(), convention.get("item"))
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("  public static Builder builder() {")
        .addLine("    return new Builder();")
        .addLine("  }")
        .addLine("}")
        .build();

    twoProperties = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract %s<%s> %s;",
            optional, element.type(), convention.get("item1"))
        .addLine("  public abstract %s<%s> %s;",
            optional, element.type(), convention.get("item2"))
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("  public static Builder builder() {")
        .addLine("    return new Builder();")
        .addLine("  }")
        .addLine("}")
        .build();

    validatedProperty = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract %s<%s> %s;",
            optional, element.type(), convention.get("item"))
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {")
        .addLine("    @Override public Builder %s(%s item) {",
            convention.set("item"), element.unwrappedType())
        .addLine("      if (!(%s)) {", element.validation("item"))
        .addLine("        throw new IllegalArgumentException(\"%s\");", element.errorMessage())
        .addLine("      }")
        .addLine("      return super.%s(item);", convention.set("item"))
        .addLine("    }")
        .addLine("    @Override public Builder clearItem() {")
        .addLine("      throw new UnsupportedOperationException(\"Clearing prohibited\");")
        .addLine("    }")
        .addLine("  }")
        .addLine("")
        .addLine("  public static Builder builder() {")
        .addLine("    return new Builder();")
        .addLine("  }")
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
            .addLine("assertEquals(%s.%s(), value.%s);", optional, empty, convention.get("item"))
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
            .addLine("assertEquals(%s.%s(), builder.%s);",
                optional, empty, convention.get("item"))
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
            .addLine("assertEquals(%s.of(%s), builder.%s);",
                optional, element.example(0), convention.get("item"))
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
            .addLine("assertEquals(%s.of(%s), value.%s);",
                optional, element.example(0), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testSet_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("new DataType.Builder().%s((%s) null);",
                convention.set("item"), element.type())
            .build())
        .runTest();
  }

  @Test
  public void testSet_optionalOf() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s.of(%s))", convention.set("item"),
                optional, element.example(0))
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(%s), value.%s);",
                optional, element.example(0), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testSet_empty() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s.<%s>%s())",
                convention.set("item"), optional, element.type(), empty)
            .addLine("    .build();")
            .addLine("assertEquals(%s.%s(), value.%s);", optional, empty, convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testSet_nullOptional() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("new DataType.Builder().%s((%s<%s>) null);",
                convention.set("item"), optional, element.type())
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_notNull() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("nullableItem"), element.example(0))
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(%s), value.%s);",
                optional, element.example(0), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testSetNullable_null() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(null)", convention.set("nullableItem"))
            .addLine("    .build();")
            .addLine("assertEquals(%s.%s(), value.%s);", optional, empty, convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .clearItem()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.%s(), value.%s);", optional, empty, convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .build();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(%s.of(%s), builder.%s);",
                optional, element.example(0), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .%s(%s);", convention.set("item"), element.example(0))
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(%s.of(%s), builder.%s);",
                optional, element.example(0), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance_emptyOptional() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .build();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .mergeFrom(value);")
            .addLine("assertEquals(%s.of(%s), builder.%s);",
                optional, element.example(0), convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder_emptyOptional() {
    behaviorTester
        .with(new Processor(features))
        .with(oneProperty)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder();")
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .mergeFrom(template);")
            .addLine("assertEquals(%s.of(%s), builder.%s);",
                optional, element.example(0), convention.get("item"))
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
            .addLine("assertEquals(%s.%s(), value.%s);", optional, empty, convention.get("item"))
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
            .addLine("  public abstract %s<%s> %s;",
                optional, element.type(), convention.get("item"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder().%s(%s);", convention.set("item"), element.example(3))
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.of(%s), value.%s);",
                optional, element.example(3), convention.get("item"))
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
            .addLine("  public abstract %s<%s> %s;",
                optional, element.type(), convention.get("item"))
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(%s s) {", element.unwrappedType())
            .addLine("      %s(s);", convention.set("item"))
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder(%s)", element.example(0))
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(%s.%s(), value.%s);", optional, empty, convention.get("item"))
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_optionalOf() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(element.errorMessage());
    behaviorTester
        .with(new Processor(features))
        .with(validatedProperty)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .%s(%s.of(%s));",
                convention.set("item"), optional, element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_nullable() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(element.errorMessage());
    behaviorTester
        .with(new Processor(features))
        .with(validatedProperty)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .%s(%s);", convention.set("nullableItem"), element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testCustomization_empty() {
    thrown.expectMessage("Clearing prohibited");
    behaviorTester
        .with(new Processor(features))
        .with(validatedProperty)
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .%s(%s.<%s>%s());",
                convention.set("item"), optional, element.type(), empty)
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
            .addLine("  public abstract %s<%s> %s;",
                optional, String.class, convention.get("item"))
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
        .with(testBuilder()
            .addLine("DataType.Builder template = DataType.builder()")
            .addLine("    .%s(null);", convention.set("nullableItem"))
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
            .addLine("        DataType.builder().build(),")
            .addLine("        DataType.builder()")
            .addLine("            .%s(%s.<%s>%s())",
                convention.set("item"), optional, element.type(), empty)
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .%s(null)", convention.set("nullableItem"))
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .%s(%s)", convention.set("item"), element.example(0))
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .%s(%s.of(%s))",
                convention.set("item"), optional, element.example(0))
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
            .addLine("DataType empty = DataType.builder()")
            .addLine("    .build();")
            .addLine("DataType present = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", empty.toString());")
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
            .addLine("DataType aa = DataType.builder()")
            .addLine("    .build();")
            .addLine("DataType pa = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .build();")
            .addLine("DataType ap = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .build();")
            .addLine("DataType pp = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{}\", aa.toString());")
            .addLine("assertEquals(\"DataType{item1=\" + %s + \"}\", pa.toString());",
                element.example(0))
            .addLine("assertEquals(\"DataType{item2=\" + %s + \"}\", ap.toString());",
                element.example(1))
            .addLine("assertEquals(\"DataType{item1=\" + %s + \","
                    + " item2=\" + %s + \"}\", pp.toString());",
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
            .addLine("DataType empty = DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("DataType present = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", empty.toString());")
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
            .addLine("DataType aa = DataType.builder()")
            .addLine("    .buildPartial();")
            .addLine("DataType pa = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .buildPartial();")
            .addLine("DataType ap = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .buildPartial();")
            .addLine("DataType pp = DataType.builder()")
            .addLine("    .%s(%s)", convention.set("item1"), element.example(0))
            .addLine("    .%s(%s)", convention.set("item2"), element.example(1))
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{}\", aa.toString());")
            .addLine("assertEquals(\"partial DataType{item1=\" + %s + \"}\", pa.toString());",
                element.example(0))
            .addLine("assertEquals(\"partial DataType{item2=\" + %s + \"}\", ap.toString());",
                element.example(1))
            .addLine("assertEquals(\"partial DataType{item1=\" + %s + \","
                    + " item2=\" + %s + \"}\", pp.toString());",
                element.example(0), element.example(1))
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
              .addLine("  public abstract %s<%s<%s>> %s;",
                      optional, List.class, Number.class, convention.get("items"))
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s.of((%s) 1, 2, 3, 4))",
                convention.set("items"), ImmutableList.class, Number.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s.get()).containsExactly(1, 2, 3, 4).inOrder();",
                convention.get("items"))
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
              .addLine("  public abstract %s<%s<?>> %s;",
                  optional, List.class, convention.get("items"))
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s.of(1, 2, 3, 4))", convention.set("items"), ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s.get()).containsExactly(1, 2, 3, 4).inOrder();",
                convention.get("items"))
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
              .addLine("  public abstract %s<%s<? extends %s>> %s;",
                      optional, List.class, Number.class, convention.get("items"))
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s.of(1, 2, 3, 4))", convention.set("items"), ImmutableList.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s.get()).containsExactly(1, 2, 3, 4).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void testJacksonInteroperability() {
    // See also https://github.com/google/FreeBuilder/issues/68
    Class<? extends Module> module =
        optional.getName().startsWith("com.google") ? GuavaModule.class : Jdk8Module.class;
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("import " + JsonProperty.class.getName() + ";")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public interface DataType {")
            .addLine("  @JsonProperty(\"stuff\") %s<%s> %s;",
                optional, element.type(), convention.get("item"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("item"), element.example(0))
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
            .addLine("    .registerModule(new %s());", module)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertEquals(%s.of(%s), clone.%s);",
                optional, element.example(0), convention.get("item"))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
