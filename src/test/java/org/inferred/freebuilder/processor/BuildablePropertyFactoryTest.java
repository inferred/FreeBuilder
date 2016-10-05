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

import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class BuildablePropertyFactoryTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject NO_DEFAULTS_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  @%s", FreeBuilder.class)
      .addLine("  interface Item {")
      .addLine("    String getName();")
      .addLine("    int getPrice();")
      .addLine("")
      .addLine("    class Builder extends DataType_Item_Builder {}")
      .addLine("  }")
      .addLine("")
      .addLine("  Item getItem1();")
      .addLine("  Item getItem2();")
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject DEFAULTS_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  @%s", FreeBuilder.class)
      .addLine("  interface Item {")
      .addLine("    String getName();")
      .addLine("    int getPrice();")
      .addLine("")
      .addLine("    class Builder extends DataType_Item_Builder {")
      .addLine("      public Builder() {")
      .addLine("        setName(\"Air\");")
      .addLine("        setPrice(0);")
      .addLine("      }")
      .addLine("    }")
      .addLine("  }")
      .addLine("")
      .addLine("  Item getItem1();")
      .addLine("  Item getItem2();")
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject NESTED_LIST_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  @%s", FreeBuilder.class)
      .addLine("  interface Item {")
      .addLine("    %s<String> getNames();", List.class)
      .addLine("")
      .addLine("    class Builder extends DataType_Item_Builder {}")
      .addLine("  }")
      .addLine("")
      .addLine("  Item getItem();")
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject PROTOLIKE_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  class Item {")
      .addLine("    public %s<String> getNames() {", ImmutableList.class)
      .addLine("      return names;")
      .addLine("    }")
      .addLine("")
      .addLine("    public static Builder newBuilder() {")
      .addLine("      return new Builder();")
      .addLine("    }")
      .addLine("")
      .addLine("    public static class Builder {")
      .addLine("")
      .addLine("      private final %1$s<String> names = new %1$s<String>();", ArrayList.class)
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
      .addLine("    private final %s<String> names;", ImmutableList.class)
      .addLine("")
      .addLine("    private Item(Iterable<String> names) {")
      .addLine("      this.names = %s.copyOf(names);", ImmutableList.class)
      .addLine("    }")
      .addLine("  }")
      .addLine("")
      .addLine("  Item getItem();")
      .addLine("")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject FREEBUILDERLIKE_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  interface Item {")
      .addLine("    %s<String> getNames();", List.class)
      .addLine("    class Builder extends DataType_Item_Builder {}")
      .addLine("  }")
      .addLine("  Item getItem();")
      .addLine("  class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject FREEBUILDERLIKE_BUILDER_SUPERCLASS = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("class DataType_Item_Builder {")
      .addLine("  private final %s<String> names = new %s<String>();", List.class, ArrayList.class)
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
      .addLine("    names.addAll(item.getNames());")
      .addLine("    return (DataType.Item.Builder) this;")
      .addLine("  }")
      .addLine("  public DataType.Item build() { return new Value(this); }")
      .addLine("  public DataType.Item buildPartial() { return new Value(this); }")
      .addLine("  private class Value implements DataType.Item {")
      .addLine("    private %s<String> names;", ImmutableList.class)
      .addLine("    Value(DataType_Item_Builder builder) {")
      .addLine("      names = %s.copyOf(builder.names);",
          ImmutableList.class)
      .addLine("    }")
      .addLine("    @%s public %s<String> getNames() { return names; }",
          Override.class, ImmutableList.class)
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testBuild_noDefaults() {
    thrown.expect(IllegalStateException.class);
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder().build();")
            .build())
        .runTest();
  }

  @Test
  public void testBuild_defaults() {
    behaviorTester
        .with(new Processor(features))
        .with(DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
            .addLine("assertEquals(\"Air\", value.getItem1().getName());")
            .addLine("assertEquals(0, value.getItem1().getPrice());")
            .addLine("assertEquals(\"Air\", value.getItem2().getName());")
            .addLine("assertEquals(0, value.getItem2().getPrice());")
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartial() {
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .buildPartial();")
            .addLine("value.getItem1();")
            .build())
        .runTest();
  }

  @Test
  public void testBuildPartialAndGet() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .buildPartial()")
            .addLine("    .getItem1()")
            .addLine("    .getName();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToValue() {
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Foo\")")
            .addLine("        .setPrice(1)")
            .addLine("        .build())")
            .addLine("    .setItem2(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Bar\")")
            .addLine("        .setPrice(2)")
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(\"Foo\", value.getItem1().getName());")
            .addLine("assertEquals(1, value.getItem1().getPrice());")
            .addLine("assertEquals(\"Bar\", value.getItem2().getName());")
            .addLine("assertEquals(2, value.getItem2().getPrice());")
            .build())
        .runTest();
  }

  @Test
  public void testSetToValue_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(NESTED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToValue_protolike() {
    behaviorTester
        .with(new Processor(features))
        .with(PROTOLIKE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.setItem(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToValue_freebuilderlike() {
    behaviorTester
        .with(new Processor(features))
        .with(FREEBUILDERLIKE_TYPE)
        .with(FREEBUILDERLIKE_BUILDER_SUPERCLASS)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_valuesSet() {
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Foo\")")
            .addLine("        .setPrice(1))")
            .addLine("    .setItem2(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Bar\")")
            .addLine("        .setPrice(2))")
            .addLine("    .build();")
            .addLine("assertEquals(\"Foo\", value.getItem1().getName());")
            .addLine("assertEquals(1, value.getItem1().getPrice());")
            .addLine("assertEquals(\"Bar\", value.getItem2().getName());")
            .addLine("assertEquals(2, value.getItem2().getPrice());")
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(NESTED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\"));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\"));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_protolike() {
    behaviorTester
        .with(new Processor(features))
        .with(PROTOLIKE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\"));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.setItem(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\"));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_freebuilderlike() {
    behaviorTester
        .with(new Processor(features))
        .with(FREEBUILDERLIKE_TYPE)
        .with(FREEBUILDERLIKE_BUILDER_SUPERCLASS)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\"));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\"));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testSetToBuilder_missingValue() {
    thrown.expect(IllegalStateException.class);
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Foo\"));")
            .build())
        .runTest();
  }

  @Test
  public void testMutateMethod() {
    assumeTrue("Environment has lambdas", features.get(FUNCTION_PACKAGE).consumer().isPresent());
    behaviorTester
        .with(new Processor(features))
        .with(DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .mutateItem1(b -> b")
            .addLine("        .setName(\"Bananas\")")
            .addLine("        .setPrice(5))")
            .addLine("    .mutateItem2(b -> b")
            .addLine("        .setName(\"Pears\")")
            .addLine("        .setPrice(15))")
            .addLine("    .build();")
            .addLine("assertEquals(\"Bananas\", value.getItem1().getName());")
            .addLine("assertEquals(5, value.getItem1().getPrice());")
            .addLine("assertEquals(\"Pears\", value.getItem2().getName());")
            .addLine("assertEquals(15, value.getItem2().getPrice());")
            .build())
        .runTest();
  }

  @Test
  public void testGetBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.getItem1Builder().setName(\"Foo\");")
            .addLine("assertEquals(\"Foo\", builder.build().getItem1().getName());")
            .build())
        .runTest();
  }

  @Test
  public void testGetBuilder_protolike() {
    behaviorTester
        .with(new Processor(features))
        .with(PROTOLIKE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.getItemBuilder().addNames(\"Foo\");")
            .addLine("assertThat(builder.build().getItem().getNames()).containsExactly(\"Foo\");")
            .build())
        .runTest();
  }

  @Test
  public void testGetBuilder_freebuilderlike() {
    behaviorTester
        .with(new Processor(features))
        .with(FREEBUILDERLIKE_TYPE)
        .with(FREEBUILDERLIKE_BUILDER_SUPERCLASS)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.getItemBuilder().addNames(\"Foo\");")
            .addLine("assertThat(builder.build().getItem().getNames()).containsExactly(\"Foo\");")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Foo\")")
            .addLine("        .setPrice(1)")
            .addLine("        .build())")
            .addLine("    .setItem2(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Bar\")")
            .addLine("        .setPrice(2)")
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().getItem1().getName());")
            .addLine("assertEquals(1, builder.build().getItem1().getPrice());")
            .addLine("assertEquals(\"Bar\", builder.build().getItem2().getName());")
            .addLine("assertEquals(2, builder.build().getItem2().getPrice());")
            .addLine("com.example.DataType.Builder partialBuilder =")
            .addLine("    new com.example.DataType.Builder();")
            .addLine("partialBuilder.getItem1Builder().setName(\"Baz\");")
            .addLine("partialBuilder.getItem2Builder().setPrice(3);")
            .addLine("builder.mergeFrom(partialBuilder);")
            .addLine("assertEquals(\"Baz\", builder.build().getItem1().getName());")
            .addLine("assertEquals(1, builder.build().getItem1().getPrice());")
            .addLine("assertEquals(\"Bar\", builder.build().getItem2().getName());")
            .addLine("assertEquals(3, builder.build().getItem2().getPrice());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(NESTED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build()));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\", \"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_protolike() {
    behaviorTester
        .with(new Processor(features))
        .with(PROTOLIKE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(com.example.DataType.Item.newBuilder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build()));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\", \"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromBuilder_freebuilderlike() {
    behaviorTester
        .with(new Processor(features))
        .with(FREEBUILDERLIKE_TYPE)
        .with(FREEBUILDERLIKE_BUILDER_SUPERCLASS)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build()));")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\", \"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue() {
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Foo\")")
            .addLine("        .setPrice(1)")
            .addLine("        .build())")
            .addLine("    .setItem2(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Bar\")")
            .addLine("        .setPrice(2)")
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().getItem1().getName());")
            .addLine("assertEquals(1, builder.build().getItem1().getPrice());")
            .addLine("assertEquals(\"Bar\", builder.build().getItem2().getName());")
            .addLine("assertEquals(2, builder.build().getItem2().getPrice());")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Cheese\")")
            .addLine("        .setPrice(3)")
            .addLine("        .build())")
            .addLine("    .setItem2(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Ham\")")
            .addLine("        .setPrice(4)")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertEquals(\"Cheese\", builder.build().getItem1().getName());")
            .addLine("assertEquals(3, builder.build().getItem1().getPrice());")
            .addLine("assertEquals(\"Ham\", builder.build().getItem2().getName());")
            .addLine("assertEquals(4, builder.build().getItem2().getPrice());")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(NESTED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\", \"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromValue_protolike() {
    behaviorTester
        .with(new Processor(features))
        .with(PROTOLIKE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(com.example.DataType.Item.newBuilder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\", \"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Foo\")")
            .addLine("        .setPrice(1)")
            .addLine("        .build())")
            .addLine("    .setItem2(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Bar\")")
            .addLine("        .setPrice(2)")
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().getItem1().getName());")
            .addLine("assertEquals(1, builder.build().getItem1().getPrice());")
            .addLine("assertEquals(\"Bar\", builder.build().getItem2().getName());")
            .addLine("assertEquals(2, builder.build().getItem2().getPrice());")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem1(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Cheese\")")
            .addLine("        .setPrice(3)")
            .addLine("        .build())")
            .addLine("    .setItem2(new com.example.DataType.Item.Builder()")
            .addLine("        .setName(\"Ham\")")
            .addLine("        .setPrice(4)")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertEquals(\"Cheese\", builder.build().getItem1().getName());")
            .addLine("assertEquals(3, builder.build().getItem1().getPrice());")
            .addLine("assertEquals(\"Ham\", builder.build().getItem2().getName());")
            .addLine("assertEquals(4, builder.build().getItem2().getPrice());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_nestedList() {
    behaviorTester
        .with(new Processor(features))
        .with(NESTED_LIST_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_protolike() {
    behaviorTester
        .with(new Processor(features))
        .with(PROTOLIKE_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(com.example.DataType.Item.newBuilder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear_freebuilderlike() {
    behaviorTester
        .with(new Processor(features))
        .with(FREEBUILDERLIKE_TYPE)
        .with(FREEBUILDERLIKE_BUILDER_SUPERCLASS)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
            .addLine("builder.setItem(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .setItem(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().getItem().getNames())")
            .addLine("    .containsExactly(\"Cheese\", \"Ham\").inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testGenericChildProperty() {
    // Raised in issue #183
    behaviorTester
        .with(new Processor())
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
            .addLine("    PIdentityDefinition<T, U> getIdentity();")
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
            .addLine("  interface Value {")
            .addLine("    String getName();")
            .addLine("")
            .addLine("    class Builder {")
            .addLine("      private static class ValueImpl implements Value {")
            .addLine("        private final String name;")
            .addLine("        private ValueImpl(String name) { this.name = name; }")
            .addLine("        @Override public String getName() { return name; }")
            .addLine("      }")
            .addLine("      private String name;")
            .addLine("      public Builder setName(String name) {")
            .addLine("        this.name = name;")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder mergeFrom(Value value) {")
            .addLine("        this.name = value.getName();")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder clear() { return this; }")
            .addLine("      public Value build() { return new ValueImpl(name); }")
            .addLine("      public Value buildPartial() { return new ValueImpl(name); }")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  Value getValue();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setValue(new com.example.DataType.Value.Builder()")
            .addLine("        .setName(\"thingy\"))")
            .addLine("    .build();")
            .addLine("assertEquals(\"thingy\", value.getValue().getName());")
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
            .addLine("  interface Template {")
            .addLine("    String getName();")
            .addLine("")
            .addLine("    class Builder {")
            .addLine("      private static class TemplateImpl implements Template {")
            .addLine("        private final String name;")
            .addLine("        private TemplateImpl(String name) { this.name = name; }")
            .addLine("        @Override public String getName() { return name; }")
            .addLine("      }")
            .addLine("      private String name;")
            .addLine("      public Builder setName(String name) {")
            .addLine("        this.name = name;")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder mergeFrom(Template value) {")
            .addLine("        this.name = value.getName();")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder clear() { return this; }")
            .addLine("      public Template build() { return new TemplateImpl(name); }")
            .addLine("      public Template buildPartial() { return new TemplateImpl(name); }")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  Template getTemplate();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setTemplate(new com.example.DataType.Template.Builder()")
            .addLine("        .setName(\"thingy\"))")
            .addLine("    .build();")
            .addLine("assertEquals(\"thingy\", value.getTemplate().getName());")
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
            .addLine("    String getName();")
            .addLine("    int getPrice();")
            .addLine("")
            .addLine("    class Builder extends DataType_Item_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  @JsonProperty(\"one\") Item getItem1();")
            .addLine("  @JsonProperty(\"two\") Item getItem2();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setItem1(new DataType.Item.Builder()")
            .addLine("        .setName(\"Foo\")")
            .addLine("        .setPrice(1)")
            .addLine("        .build())")
            .addLine("    .setItem2(new DataType.Item.Builder()")
            .addLine("        .setName(\"Bar\")")
            .addLine("        .setPrice(2)")
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertEquals(\"Foo\", clone.getItem1().getName());")
            .addLine("assertEquals(1, clone.getItem1().getPrice());")
            .addLine("assertEquals(\"Bar\", clone.getItem2().getName());")
            .addLine("assertEquals(2, clone.getItem2().getPrice());")
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
            .addLine("  public abstract %s<String> getNames();", List.class)
            .addLine("  static class Builder extends Item_Builder {}")
            .addLine("}")
            .build())
        .with(new SourceBuilder()
            .addLine("package com.example.bar;")
            .addLine("import com.example.foo.Item;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  Item getItem();")
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
            .addLine("    names.addAll(item.getNames());")
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
            .addLine("    @%s public %s<String> getNames() { return names; }",
                Override.class, ImmutableList.class)
            .addLine("  }")
            .addLine("}")
            .build())
        .compiles();
  }
}
