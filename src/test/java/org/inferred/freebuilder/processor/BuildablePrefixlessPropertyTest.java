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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class BuildablePrefixlessPropertyTest {

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
      .addLine("    String name();")
      .addLine("    int price();")
      .addLine("")
      .addLine("    class Builder extends DataType_Item_Builder {}")
      .addLine("  }")
      .addLine("")
      .addLine("  Item item1();")
      .addLine("  Item item2();")
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
      .addLine("    String name();")
      .addLine("    int price();")
      .addLine("")
      .addLine("    class Builder extends DataType_Item_Builder {")
      .addLine("      public Builder() {")
      .addLine("        name(\"Air\");")
      .addLine("        price(0);")
      .addLine("      }")
      .addLine("    }")
      .addLine("  }")
      .addLine("")
      .addLine("  Item item1();")
      .addLine("  Item item2();")
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

  private static final JavaFileObject PROTOLIKE_TYPE = new SourceBuilder()
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

  private static final JavaFileObject FREEBUILDERLIKE_TYPE = new SourceBuilder()
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
      .addLine("    @%s public %s<String> names() { return names; }", Override.class, List.class)
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
            .addLine("assertEquals(\"Air\", value.item1().name());")
            .addLine("assertEquals(0, value.item1().price());")
            .addLine("assertEquals(\"Air\", value.item2().name());")
            .addLine("assertEquals(0, value.item2().price());")
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
            .addLine("value.item1();")
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
            .addLine("    .item1()")
            .addLine("    .name();")
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
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Foo\")")
            .addLine("        .price(1)")
            .addLine("        .build())")
            .addLine("    .item2(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Bar\")")
            .addLine("        .price(2)")
            .addLine("        .build())")
            .addLine("    .build();")
            .addLine("assertEquals(\"Foo\", value.item1().name());")
            .addLine("assertEquals(1, value.item1().price());")
            .addLine("assertEquals(\"Bar\", value.item2().name());")
            .addLine("assertEquals(2, value.item2().price());")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
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
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Foo\")")
            .addLine("        .price(1))")
            .addLine("    .item2(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Bar\")")
            .addLine("        .price(2))")
            .addLine("    .build();")
            .addLine("assertEquals(\"Foo\", value.item1().name());")
            .addLine("assertEquals(1, value.item1().price());")
            .addLine("assertEquals(\"Bar\", value.item2().name());")
            .addLine("assertEquals(2, value.item2().price());")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\"));")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\"));")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\"));")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Cheese\", \"Ham\"));")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\"));")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
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
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Foo\"));")
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
            .addLine("        .name(\"Bananas\")")
            .addLine("        .price(5))")
            .addLine("    .mutateItem2(b -> b")
            .addLine("        .name(\"Pears\")")
            .addLine("        .price(15))")
            .addLine("    .build();")
            .addLine("assertEquals(\"Bananas\", value.item1().name());")
            .addLine("assertEquals(5, value.item1().price());")
            .addLine("assertEquals(\"Pears\", value.item2().name());")
            .addLine("assertEquals(15, value.item2().price());")
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
            .addLine("builder.item1Builder().name(\"Foo\");")
            .addLine("assertEquals(\"Foo\", builder.build().item1().name());")
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
            .addLine("builder.itemBuilder().addNames(\"Foo\");")
            .addLine("assertThat(builder.build().item().names()).containsExactly(\"Foo\");")
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
            .addLine("builder.itemBuilder().addNames(\"Foo\");")
            .addLine("assertThat(builder.build().item().names()).containsExactly(\"Foo\");")
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
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Foo\")")
            .addLine("        .price(1)")
            .addLine("        .build())")
            .addLine("    .item2(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Bar\")")
            .addLine("        .price(2)")
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().item1().name());")
            .addLine("assertEquals(1, builder.build().item1().price());")
            .addLine("assertEquals(\"Bar\", builder.build().item2().name());")
            .addLine("assertEquals(2, builder.build().item2().price());")
            .addLine("com.example.DataType.Builder partialBuilder =")
            .addLine("    new com.example.DataType.Builder();")
            .addLine("partialBuilder.item1Builder().name(\"Baz\");")
            .addLine("partialBuilder.item2Builder().price(3);")
            .addLine("builder.mergeFrom(partialBuilder);")
            .addLine("assertEquals(\"Baz\", builder.build().item1().name());")
            .addLine("assertEquals(1, builder.build().item1().price());")
            .addLine("assertEquals(\"Bar\", builder.build().item2().name());")
            .addLine("assertEquals(3, builder.build().item2().price());")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build()));")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(com.example.DataType.Item.newBuilder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build()));")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(new com.example.DataType.Item.Builder()")
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
        .with(NO_DEFAULTS_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Foo\")")
            .addLine("        .price(1)")
            .addLine("        .build())")
            .addLine("    .item2(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Bar\")")
            .addLine("        .price(2)")
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().item1().name());")
            .addLine("assertEquals(1, builder.build().item1().price());")
            .addLine("assertEquals(\"Bar\", builder.build().item2().name());")
            .addLine("assertEquals(2, builder.build().item2().price());")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Cheese\")")
            .addLine("        .price(3)")
            .addLine("        .build())")
            .addLine("    .item2(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Ham\")")
            .addLine("        .price(4)")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertEquals(\"Cheese\", builder.build().item1().name());")
            .addLine("assertEquals(3, builder.build().item1().price());")
            .addLine("assertEquals(\"Ham\", builder.build().item2().name());")
            .addLine("assertEquals(4, builder.build().item2().price());")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(com.example.DataType.Item.newBuilder()")
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
            .addLine("    String name();")
            .addLine("    int price();")
            .addLine("")
            .addLine("    Builder toBuilder();")
            .addLine("    class Builder extends DataType_Item_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  Item item1();")
            .addLine("  Item item2();")
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value1 = new com.example.DataType.Builder()")
            .addLine("    .mutateItem1($ -> $")
            .addLine("        .name(\"Foo\"))")
            .addLine("    .mutateItem2($ -> $")
            .addLine("        .price(2))")
            .addLine("    .buildPartial();")
            .addLine("com.example.DataType value2 = value1.toBuilder()")
            .addLine("    .mutateItem2($ -> $")
            .addLine("        .name(\"Bar\"))")
            .addLine("    .build();")
            .addLine("com.example.DataType expected = new com.example.DataType.Builder()")
            .addLine("    .mutateItem1($ -> $")
            .addLine("        .name(\"Foo\"))")
            .addLine("    .mutateItem2($ -> $")
            .addLine("        .name(\"Bar\")")
            .addLine("        .price(2))")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(expected, value2);")
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
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Foo\")")
            .addLine("        .price(1)")
            .addLine("        .build())")
            .addLine("    .item2(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Bar\")")
            .addLine("        .price(2)")
            .addLine("        .build());")
            .addLine("assertEquals(\"Foo\", builder.build().item1().name());")
            .addLine("assertEquals(1, builder.build().item1().price());")
            .addLine("assertEquals(\"Bar\", builder.build().item2().name());")
            .addLine("assertEquals(2, builder.build().item2().price());")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item1(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Cheese\")")
            .addLine("        .price(3)")
            .addLine("        .build())")
            .addLine("    .item2(new com.example.DataType.Item.Builder()")
            .addLine("        .name(\"Ham\")")
            .addLine("        .price(4)")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertEquals(\"Cheese\", builder.build().item1().name());")
            .addLine("assertEquals(3, builder.build().item1().price());")
            .addLine("assertEquals(\"Ham\", builder.build().item2().name());")
            .addLine("assertEquals(4, builder.build().item2().price());")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(new com.example.DataType.Item.Builder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(com.example.DataType.Item.newBuilder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(com.example.DataType.Item.newBuilder()")
            .addLine("        .addNames(\"Cheese\", \"Ham\")")
            .addLine("        .build())")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
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
            .addLine("builder.item(new com.example.DataType.Item.Builder()")
            .addLine("    .addNames(\"Foo\", \"Bar\")")
            .addLine("    .build());")
            .addLine("assertThat(builder.build().item().names())")
            .addLine("    .containsExactly(\"Foo\", \"Bar\").inOrder();")
            .addLine("builder.clear().mergeFrom(new com.example.DataType.Builder()")
            .addLine("    .item(new com.example.DataType.Item.Builder()")
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
            .addLine("    PIdentityDefinition<T, U> identity();")
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
            .addLine("    String name();")
            .addLine("")
            .addLine("    class Builder {")
            .addLine("      private static class ValueImpl implements Value {")
            .addLine("        private final String name;")
            .addLine("        private ValueImpl(String name) { this.name = name; }")
            .addLine("        @Override public String name() { return name; }")
            .addLine("      }")
            .addLine("      private String name;")
            .addLine("      public Builder name(String name) {")
            .addLine("        this.name = name;")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder mergeFrom(Value value) {")
            .addLine("        this.name = value.name();")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder clear() { return this; }")
            .addLine("      public Value build() { return new ValueImpl(name); }")
            .addLine("      public Value buildPartial() { return new ValueImpl(name); }")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  Value value();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .value(new com.example.DataType.Value.Builder()")
            .addLine("        .name(\"thingy\"))")
            .addLine("    .build();")
            .addLine("assertEquals(\"thingy\", value.value().name());")
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
            .addLine("    String name();")
            .addLine("")
            .addLine("    class Builder {")
            .addLine("      private static class TemplateImpl implements Template {")
            .addLine("        private final String name;")
            .addLine("        private TemplateImpl(String name) { this.name = name; }")
            .addLine("        @Override public String name() { return name; }")
            .addLine("      }")
            .addLine("      private String name;")
            .addLine("      public Builder name(String name) {")
            .addLine("        this.name = name;")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder mergeFrom(Template value) {")
            .addLine("        this.name = value.name();")
            .addLine("        return this;")
            .addLine("      }")
            .addLine("      public Builder clear() { return this; }")
            .addLine("      public Template build() { return new TemplateImpl(name); }")
            .addLine("      public Template buildPartial() { return new TemplateImpl(name); }")
            .addLine("    }")
            .addLine("  }")
            .addLine("")
            .addLine("  Template template();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .template(new com.example.DataType.Template.Builder()")
            .addLine("        .name(\"thingy\"))")
            .addLine("    .build();")
            .addLine("assertEquals(\"thingy\", value.template().name());")
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
            .addLine("    String name();")
            .addLine("    int price();")
            .addLine("")
            .addLine("    class Builder extends DataType_Item_Builder {}")
            .addLine("  }")
            .addLine("")
            .addLine("  @JsonProperty(\"one\") Item item1();")
            .addLine("  @JsonProperty(\"two\") Item item2();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addImport("com.example.DataType")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .item1(new DataType.Item.Builder().name(\"Foo\").price(1).build())")
            .addLine("    .item2(new DataType.Item.Builder().name(\"Bar\").price(2).build())")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertEquals(\"Foo\", clone.item1().name());")
            .addLine("assertEquals(1, clone.item1().price());")
            .addLine("assertEquals(\"Bar\", clone.item2().name());")
            .addLine("assertEquals(2, clone.item2().price());")
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
            .addLine("  public abstract %s<String> names();", List.class)
            .addLine("  static class Builder extends Item_Builder {}")
            .addLine("}")
            .build())
        .with(new SourceBuilder()
            .addLine("package com.example.bar;")
            .addLine("import com.example.foo.Item;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  Item item();")
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
            .addLine("    names.addAll(item.names());")
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
            .addLine("    @%s public %s<String> names() { return names; }",
                Override.class, ImmutableList.class)
            .addLine("  }")
            .addLine("}")
            .build())
        .compiles();
  }
}
