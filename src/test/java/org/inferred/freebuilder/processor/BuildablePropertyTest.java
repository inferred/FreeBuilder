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

import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class BuildablePropertyTest {

  public enum BuildableType {
    /** Use FreeBuilder to generate the buildable type. This tests we pick up the annotation. */
    FREEBUILDER("FreeBuilder-annotated", "new DataType.Item.Builder()"),
    /** Use a buildable type with a Proto-like API. */
    PROTO_LIKE("Proto-like", "DataType.Item.newBuilder()"),
    /** Use a type with a FreeBuilder-like API. This tests we work on pre-generated code. */
    FREEBUILDER_LIKE("FreeBuilder-like", "new DataType.Item.Builder()");

    private String displayName;
    private final String newBuilder;

    BuildableType(String displayName, String newBuilder) {
      this.displayName = displayName;
      this.newBuilder = newBuilder;
    }

    public String newBuilder() {
      return newBuilder;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}, {1}, {2}")
  public static Iterable<Object[]> parameters() {
    List<BuildableType> buildableTypes = Arrays.asList(BuildableType.values());
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(buildableTypes, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final BuildableType buildableType;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject noDefaultsType;
  private final JavaFileObject defaultsType;
  private final JavaFileObject nestedListType;

  public BuildablePropertyTest(
      BuildableType buildableType,
      NamingConvention convention,
      FeatureSet features) {
    this.buildableType = buildableType;
    this.convention = convention;
    this.features = features;

    noDefaultsType = generateBuildableType(convention, false);
    defaultsType = generateBuildableType(convention, true);
    nestedListType = generateNestedListType(buildableType);
  }

  private static JavaFileObject generateBuildableType(
      NamingConvention convention,
      boolean hasDefaults) {
    SourceBuilder code = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  @%s", FreeBuilder.class)
        .addLine("  interface Item {")
        .addLine("    String %s;", convention.getter("name"))
        .addLine("    int %s;", convention.getter("price"))
        .addLine("")
        .addLine("    class Builder extends DataType_Item_Builder {");
    if (hasDefaults) {
      code.addLine("      public Builder() {")
          .addLine("        %s(\"Air\");", convention.setter("name"))
          .addLine("        %s(0);", convention.setter("price"))
          .addLine("      }");
    }
    return code
        .addLine("    }")
        .addLine("  }")
        .addLine("")
        .addLine("  Item %s;", convention.getter("item1"))
        .addLine("  Item %s;", convention.getter("item2"))
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {}")
        .addLine("}")
        .build();
  }

  private static JavaFileObject generateNestedListType(BuildableType buildableType) {
    switch (buildableType) {
      case FREEBUILDER:
        return new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item {")
            .addLine("    %s<String> names();", List.class)
            .addLine("")
            .addLine("    class Builder extends DataType_Item_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  Item item();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build();

      case PROTO_LIKE:
        return new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  class Item {")
            .addLine("    public %s<String> names() {", List.class)
            .addLine("      return names;")
            .addLine("    }")
            .addLine("")
            .addLine("    public static Builder newBuilder() {")
            .addLine("      return new Builder();")
            .addLine("    }")
            .addLine("")
            .addLine("    public static class Builder {")
            .addLine("")
            .addLine("      private final %1$s<String> names = new %1$s<String>();",
                ArrayList.class)
            .addLine("")
            .addLine("      public Builder addNames(String... names) {")
            .addLine("        for (String name : names) {")
            .addLine("          this.names.add(name);")
            .addLine("        }")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("")
            .addLine("      public Builder clear() {")
            .addLine("        names.clear();")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("")
            .addLine("      public Builder mergeFrom(Item item) {")
            .addLine("        names.addAll(item.names);")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("")
            .addLine("      public Item build() {")
            .addLine("        return new Item(names);")
            .addLine("      }")
            .addLine("")
            .addLine("      public Item buildPartial() {")
            .addLine("        return new Item(names);")
            .addLine("      }")
            .addLine("")
            .addLine("      private Builder() {}")
            .addLine("    }")
            .addLine("")
            .addLine("    private final %s<String> names;", List.class)
            .addLine("")
            .addLine("    private Item(%s<String> names) {", List.class)
            .addLine("      this.names = %s.unmodifiableList(new %s<String>(names));",
                Collections.class, ArrayList.class)
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  Item item();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build();

      case FREEBUILDER_LIKE:
        return new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  interface Item {")
            .addLine("    %s<String> names();", List.class)
            .addLine("    class Builder extends DataType_Item_Builder {}")
            .addLine("  }")
            .addLine("  Item item();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .addLine("")
            .addLine("class DataType_Item_Builder {")
            .addLine("  private final %s<String> names = new %s<String>();",
                List.class, ArrayList.class)
            .addLine("  public DataType.Item.Builder addNames(String... names) {")
            .addLine("    for (String name : names) {")
            .addLine("      this.names.add(name);")
            .addLine("    }")
            .addLine("    return (DataType.Item.Builder) this;")
            .addLine("  }")
            .addLine("  public DataType.Item.Builder clear() {")
            .addLine("    names.clear();")
            .addLine("    return (DataType.Item.Builder) this;")
            .addLine("  }")
            .addLine("  public DataType.Item.Builder mergeFrom(DataType.Item.Builder builder) {")
            .addLine("    names.addAll(((DataType_Item_Builder) builder).names);")
            .addLine("    return (DataType.Item.Builder) this;")
            .addLine("  }")
            .addLine("  public DataType.Item.Builder mergeFrom(DataType.Item item) {")
            .addLine("    names.addAll(item.names());")
            .addLine("    return (DataType.Item.Builder) this;")
            .addLine("  }")
            .addLine("  public DataType.Item build() { return new Value(this); }")
            .addLine("  public DataType.Item buildPartial() { return new Value(this); }")
            .addLine("  private class Value implements DataType.Item {")
            .addLine("    private %s<String> names;", List.class)
            .addLine("    Value(DataType_Item_Builder builder) {")
            .addLine("      names = %s.unmodifiableList(new %s<String>(builder.names));",
                Collections.class, ArrayList.class)
            .addLine("    }")
            .addLine("    @%s public %s<String> names() { return names; }",
                Override.class, List.class)
            .addLine("  }")
            .addLine("}")
            .build();
    }

    throw new AssertionError("Unrecognized buildable type " + buildableType.name());
  }

  @Test
  public void testBuild_noDefaults() {
    thrown.expect(IllegalStateException.class);
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("new DataType.Builder().build();")
            .build())
        .runTest();
  }

  @Test
  public void testBuild_defaults() {
    behaviorTester
        .with(new Processor(features))
        .with(defaultsType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertEquals(\"Air\", value.%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(0, value.%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Air\", value.%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(0, value.%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartial() {
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .buildPartial();")
            .addLine("value.%s;", convention.getter("item1"))
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartialAndGet() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .buildPartial()")
            .addLine("    .%s", convention.getter("item1"))
            .addLine("    .%s;", convention.getter("name"))
            .build())
        .runTest();
  }

  @Test
  public void testSetToValue() {
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Foo\")", convention.setter("name"))
            .addLine("        .%s(1)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item2"))
            .addLine("        .%s(\"Bar\")", convention.setter("name"))
            .addLine("        .%s(2)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(\"Foo\", value.%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(1, value.%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Bar\", value.%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(2, value.%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void testSetToValue_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(nestedListType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("builder.item(%s", buildableType.newBuilder())
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(%s", buildableType.newBuilder())
            .addLine("    .addNames(\"Cheese\", \"Ham\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_valuesSet() {
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Foo\")", convention.setter("name"))
            .addLine("        .%s(1))", convention.setter("price"))
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item2"))
            .addLine("        .%s(\"Bar\")", convention.setter("name"))
            .addLine("        .%s(2))", convention.setter("price"))
            .addLine("    .build();")
            .addLine("assertEquals(\"Foo\", value.%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(1, value.%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Bar\", value.%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(2, value.%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(nestedListType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("builder.item(%s", buildableType.newBuilder())
            .addLine("    .addNames(\"Foo\", \"Bar\"));")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(%s", buildableType.newBuilder())
            .addLine("    .addNames(\"Cheese\", \"Ham\"));")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_missingValue() {
    thrown.expect(IllegalStateException.class);
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Foo\"));", convention.setter("name"))
            .build())
        .runTest();
  }

  @Test
  public void testMutateMethod() {
    assumeTrue("Environment has lambdas", features.get(FUNCTION_PACKAGE).consumer().isPresent());
    behaviorTester
        .with(new Processor(features))
        .with(defaultsType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItem1(b -> b")
            .addLine("        .%s(\"Bananas\")", convention.setter("name"))
            .addLine("        .%s(5))", convention.setter("price"))
            .addLine("    .mutateItem2(b -> b")
            .addLine("        .%s(\"Pears\")", convention.setter("name"))
            .addLine("        .%s(15))", convention.setter("price"))
            .addLine("    .build();")
            .addLine("assertEquals(\"Bananas\", value.%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(5, value.%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Pears\", value.%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(15, value.%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void testGetBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(defaultsType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("builder.%s.%s(\"Foo\");",
                convention.getter("item1Builder"), convention.setter("name"))
            .addLine("assertEquals(\"Foo\", builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .build())
        .runTest();
  }

  @Test
  public void testGetBuilder_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(nestedListType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("builder.itemBuilder().addNames(\"Foo\");")
            .addLine("assertThat(builder.build().item().names()).containsExactly(\"Foo\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Foo\")", convention.setter("name"))
            .addLine("        .%s(1)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item2"))
            .addLine("        .%s(\"Bar\")", convention.setter("name"))
            .addLine("        .%s(2)", convention.setter("price"))
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(1, builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Bar\", builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(2, builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .addLine("DataType.Builder partialBuilder =")
            .addLine("    new DataType.Builder();")
            .addLine("partialBuilder.%s.%s(\"Baz\");",
                convention.getter("item1Builder"), convention.setter("name"))
            .addLine("partialBuilder.%s.%s(3);",
                convention.getter("item2Builder"), convention.setter("price"))
            .addLine("builder.mergeFrom(partialBuilder);")
            .addLine("assertEquals(\"Baz\", builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(1, builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Bar\", builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(3, builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(nestedListType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("builder.item(%s", buildableType.newBuilder())
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new DataType.Builder()")
            .addLine("    .item(%s", buildableType.newBuilder())
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build()));")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\", \"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue() {
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Foo\")", convention.setter("name"))
            .addLine("        .%s(1)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item2"))
            .addLine("        .%s(\"Bar\")", convention.setter("name"))
            .addLine("        .%s(2)", convention.setter("price"))
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(1, builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Bar\", builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(2, builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .addLine("builder.mergeFrom(new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Cheese\")", convention.setter("name"))
            .addLine("        .%s(3)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item2"))
            .addLine("        .%s(\"Ham\")", convention.setter("name"))
            .addLine("        .%s(4)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertEquals(\"Cheese\", builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(3, builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Ham\", builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(4, builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(nestedListType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("builder.item(%s", buildableType.newBuilder())
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new DataType.Builder()")
            .addLine("    .item(%s", buildableType.newBuilder())
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\", \"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial() {
    assumeTrue(features.get(SOURCE_LEVEL).hasLambdas());
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item {")
            .addLine("    String %s;", convention.getter("name"))
            .addLine("    int %s;", convention.getter("price"))
            .addLine("")
            .addLine("    Builder toBuilder();")
            .addLine("    class Builder extends DataType_Item_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  Item %s;", convention.getter("item1"))
            .addLine("  Item %s;", convention.getter("item2"))
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value1 = new DataType.Builder()")
            .addLine("    .mutateItem1($ -> $")
            .addLine("        .%s(\"Foo\"))", convention.setter("name"))
            .addLine("    .mutateItem2($ -> $")
            .addLine("        .%s(2))", convention.setter("price"))
            .addLine("    .buildPartial();")
            .addLine("DataType value2 = value1.toBuilder()")
            .addLine("    .mutateItem2($ -> $")
            .addLine("        .%s(\"Bar\"))", convention.setter("name"))
            .addLine("    .build();")
            .addLine("DataType expected = new DataType.Builder()")
            .addLine("    .mutateItem1($ -> $")
            .addLine("        .%s(\"Foo\"))", convention.setter("name"))
            .addLine("    .mutateItem2($ -> $")
            .addLine("        .%s(\"Bar\")", convention.setter("name"))
            .addLine("        .%s(2))", convention.setter("price"))
            .addLine("    .buildPartial();")
            .addLine("assertEquals(expected, value2);")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(noDefaultsType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Foo\")", convention.setter("name"))
            .addLine("        .%s(1)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item2"))
            .addLine("        .%s(\"Bar\")", convention.setter("name"))
            .addLine("        .%s(2)", convention.setter("price"))
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(1, builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Bar\", builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(2, builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .addLine("builder.clear().mergeFrom(new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item1"))
            .addLine("        .%s(\"Cheese\")", convention.setter("name"))
            .addLine("        .%s(3)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .%s(new DataType.Item.Builder()", convention.setter("item2"))
            .addLine("        .%s(\"Ham\")", convention.setter("name"))
            .addLine("        .%s(4)", convention.setter("price"))
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertEquals(\"Cheese\", builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(3, builder.build().%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Ham\", builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(4, builder.build().%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(nestedListType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("builder.item(%s", buildableType.newBuilder())
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.clear().mergeFrom(new DataType.Builder()")
            .addLine("    .item(%s", buildableType.newBuilder())
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGenericChildProperty() {
    // Raised in issue #183
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface PIdentityDefinition<T, U> {")
            .addLine("    class Builder<T, U> extends PIdentityDefinition_Builder<T, U> {}")
            .addLine("}")
            .build())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface PAccess<T, U> {")
            .addLine("    class Builder<T, U> extends PAccess_Builder<T, U> {}")
            .addLine("")
            .addLine("    PIdentityDefinition<T, U> %s;", convention.getter("identity"))
            .addLine("}")
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testIssue68_nameCollisionForValue() {
    // mergeFrom(DataType value) must resolve the name collision on "value"
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Value {")
            .addLine("    String %s;", convention.getter("name"))
            .addLine("")
            .addLine("    class Builder extends DataType_Value_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  Value %s;", convention.getter("value"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(new DataType.Value.Builder()", convention.setter("value"))
            .addLine("        .%s(\"thingy\"))", convention.setter("name"))
            .addLine("    .build();")
            .addLine("assertEquals(\"thingy\", value.%s.%s);",
                convention.getter("value"), convention.getter("name"))
            .build())
        .runTest();
  }

  @Test
  public void testIssue68_nameCollisionForTemplate() {
    // mergeFrom(DataType.Template template) must resolve the name collision on "template"
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Template {")
            .addLine("    String %s;", convention.getter("name"))
            .addLine("")
            .addLine("    class Builder extends DataType_Template_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  Template %s;", convention.getter("template"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(new DataType.Template.Builder()", convention.setter("template"))
            .addLine("        .%s(\"thingy\"))", convention.setter("name"))
            .addLine("    .build();")
            .addLine("assertEquals(\"thingy\", value.%s.%s);",
                convention.getter("template"), convention.getter("name"))
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
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  @%s(builder = Item.Builder.class)", JsonDeserialize.class)
            .addLine("  interface Item {")
            .addLine("    String %s;", convention.getter("name"))
            .addLine("    int %s;", convention.getter("price"))
            .addLine("")
            .addLine("    class Builder extends DataType_Item_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  @JsonProperty(\"one\") Item %s;", convention.getter("item1"))
            .addLine("  @JsonProperty(\"two\") Item %s;", convention.getter("item2"))
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(new DataType.Item.Builder().%s(\"Foo\").%s(1).build())",
                convention.setter("item1"), convention.setter("name"), convention.setter("price"))
            .addLine("    .%s(new DataType.Item.Builder().%s(\"Bar\").%s(2).build())",
                convention.setter("item2"), convention.setter("name"), convention.setter("price"))
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertEquals(\"Foo\", clone.%s.%s);",
                convention.getter("item1"), convention.getter("name"))
            .addLine("assertEquals(1, clone.%s.%s);",
                convention.getter("item1"), convention.getter("price"))
            .addLine("assertEquals(\"Bar\", clone.%s.%s);",
                convention.getter("item2"), convention.getter("name"))
            .addLine("assertEquals(2, clone.%s.%s);",
                convention.getter("item2"), convention.getter("price"))
            .build())
        .runTest();
  }

  @Test
  public void hiddenBuilderNotIllegallyReferenced() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example.foo;")
            .addLine("public abstract class Item {")
            .addLine("  public abstract %s<String> %s;", List.class, convention.getter("names"))
            .addLine("  static class Builder extends Item_Builder {}")
            .addLine("}")
            .build())
        .with(new SourceBuilder()
            .addLine("package com.example.bar;")
            .addLine("import com.example.foo.Item;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  Item %s;", convention.getter("item1"))
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new SourceBuilder()
            .addLine("package com.example.foo;")
            .addLine("class Item_Builder {")
            .addLine("  private final %s<String> names = new %s<String>();",
                List.class, ArrayList.class)
            .addLine("  public Item.Builder addNames(String... names) {")
            .addLine("    for (String name : names) {")
            .addLine("      this.names.add(name);")
            .addLine("    }")
            .addLine("    return (Item.Builder) this;")
            .addLine("  }")
            .addLine("  public Item.Builder clear() {")
            .addLine("    names.clear();")
            .addLine("    return (Item.Builder) this;")
            .addLine("  }")
            .addLine("  public Item.Builder mergeFrom(Item.Builder builder) {")
            .addLine("    names.addAll(((Item_Builder) builder).names);")
            .addLine("    return (Item.Builder) this;")
            .addLine("  }")
            .addLine("  public Item.Builder mergeFrom(Item item) {")
            .addLine("    names.addAll(item.%s);", convention.getter("names"))
            .addLine("    return (Item.Builder) this;")
            .addLine("  }")
            .addLine("  public Item build() { return new Value(this); }")
            .addLine("  public Item buildPartial() { return new Value(this); }")
            .addLine("  private class Value extends Item {")
            .addLine("    private %s<String> names;", ImmutableList.class)
            .addLine("    Value(Item_Builder builder) {")
            .addLine("      names = %s.copyOf(builder.names);",
                ImmutableList.class)
            .addLine("    }")
            .addLine("    @%s public %s<String> %s { return names; }",
                Override.class, ImmutableList.class, convention.getter("names"))
            .addLine("  }")
            .addLine("}")
            .build())
        .compiles();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
