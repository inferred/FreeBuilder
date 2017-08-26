package org.inferred.freebuilder.processor;

import static org.apache.commons.lang3.StringEscapeUtils.escapeJava;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

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
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class BuildableListPropertyTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}, {1}")
  public static Iterable<Object[]> featureSets() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA;
    return () -> Lists
        .cartesianProduct(conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject buildableListType;

  public BuildableListPropertyTest(NamingConvention convention, FeatureSet features) {
      this.convention = convention;
      this.features = features;
      buildableListType = new SourceBuilder()
          .addLine("package com.example;")
          .addLine("@%s", FreeBuilder.class)
          .addLine("@%s(builder = Receipt.Builder.class)", JsonDeserialize.class)
          .addLine("public interface Receipt {")
          .addLine("  @%s", FreeBuilder.class)
          .addLine("  @%s(builder = Item.Builder.class)", JsonDeserialize.class)
          .addLine("  interface Item {")
          .addLine("    String name();")
          .addLine("    int price();")
          .addLine("")
          .addLine("    Builder toBuilder();")
          .addLine("    class Builder extends Receipt_Item_Builder {}")
          .addLine("  }")
          .addLine("")
          .addLine("  %s<Item> %s;", List.class, convention.get("items"))
          .addLine("")
          .addLine("  Builder toBuilder();")
          .addLine("  class Builder extends Receipt_Builder {}")
          .addLine("}")
          .build();
  }

  @Test
  public void addValueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy)")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void addValueInstance_preservesPartials() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").buildPartial();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").buildPartial();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy)")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void addBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candy = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Item.Builder apple = new Item.Builder().name(\"apple\").price(50);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy)")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void addBuilder_copiesBuilderValues() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder itemBuilder = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Receipt.Builder builder = new Receipt.Builder().addItems(itemBuilder);")
            .addLine("Item candy = itemBuilder.build();")
            .addLine("itemBuilder.name(\"apple\").price(50);")
            .addLine("builder.addItems(itemBuilder);")
            .addLine("Item apple = itemBuilder.build();")
            .addLine("itemBuilder.name(\"poison\").price(500);")
            .addLine("Receipt value = builder.build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void addAllIterableOfValueInstances() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllItems(ImmutableList.of(candy, apple))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void addAllIterableOfValueInstances_preservesPartials() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").buildPartial();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").buildPartial();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllItems(ImmutableList.of(candy, apple))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void mergeFromValue() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Receipt initialReceipt = new Receipt.Builder().addItems(candy).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .mergeFrom(initialReceipt)")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void mergeFromValue_preservesPartials() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").buildPartial();")
            .addLine("Receipt initialReceipt = new Receipt.Builder().addItems(candy).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").buildPartial();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .mergeFrom(initialReceipt)")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void testJacksonInteroperability() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllItems(ImmutableList.of(candy, apple))")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("assertThat(json).isEqualTo(\"%s\");", escapeJava("{\"items\":["
                + "{\"name\":\"candy\",\"price\":15},"
                + "{\"name\":\"apple\",\"price\":50}]}"))
            .addLine("Receipt clone = mapper.readValue(json, Receipt.class);")
            .addLine("assertThat(clone.%s).containsExactly(candy, apple).inOrder();",
                convention.get())
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.Receipt")
        .addImport("com.example.Receipt.Item")
        .addImport(ImmutableList.class)
        .addImport(Stream.class);
  }
}
