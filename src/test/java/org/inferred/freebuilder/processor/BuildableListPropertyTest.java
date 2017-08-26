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
  public void varargsAddValueInstances() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy, apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void varargsAddValueInstances_preservesPartials() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").buildPartial();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").buildPartial();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy, apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void varargsAddBuilders() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candy = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Item.Builder apple = new Item.Builder().name(\"apple\").price(50);")
            .addLine("Receipt value = new Receipt.Builder().addItems(candy, apple).build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void varargsAddBuilders_copiesBuilderValues() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candyBuilder = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Item.Builder appleBuilder = new Item.Builder().name(\"apple\").price(15);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candyBuilder, appleBuilder)")
            .addLine("    .build();")
            .addLine("Item candy = candyBuilder.build();")
            .addLine("Item apple = appleBuilder.build();")
            .addLine("candyBuilder.name(\"poison\").price(500);")
            .addLine("appleBuilder.name(\"brick\").price(200);")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void addAllSpliteratorOfValueInstances() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllItems(Stream.of(candy, apple).spliterator())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void addAllSpliteratorOfValueInstances_preservesPartials() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").buildPartial();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").buildPartial();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllItems(Stream.of(candy, apple).spliterator())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void addAllSpliteratorOfBuilders() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candy = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Item.Builder apple = new Item.Builder().name(\"apple\").price(50);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllBuildersOfItems(Stream.of(candy, apple).spliterator())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void addAllSpliteratorOfBuilders_copiesBuilderValues() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candyBuilder = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Item.Builder appleBuilder = new Item.Builder().name(\"apple\").price(15);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllBuildersOfItems(")
            .addLine("        Stream.of(candyBuilder, appleBuilder).spliterator())")
            .addLine("    .build();")
            .addLine("Item candy = candyBuilder.build();")
            .addLine("Item apple = appleBuilder.build();")
            .addLine("candyBuilder.name(\"poison\").price(500);")
            .addLine("appleBuilder.name(\"brick\").price(200);")
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
  public void addAllIterableOfBuilders() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candy = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Item.Builder apple = new Item.Builder().name(\"apple\").price(50);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllBuildersOfItems(ImmutableList.of(candy, apple))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void addAllIterableOfBuilders_copiesBuilderValues() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candyBuilder = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Item.Builder appleBuilder = new Item.Builder().name(\"apple\").price(15);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addAllBuildersOfItems(ImmutableList.of(candyBuilder, appleBuilder))")
            .addLine("    .build();")
            .addLine("Item candy = candyBuilder.build();")
            .addLine("Item apple = appleBuilder.build();")
            .addLine("candyBuilder.name(\"poison\").price(500);")
            .addLine("appleBuilder.name(\"brick\").price(200);")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void clearProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy)")
            .addLine("    .clearItems()")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(apple);", convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void getter_returnsEmptyListIfBuilderIsEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("assertThat(new Receipt.Builder().%s).isEmpty();",
                convention.get("buildersOfItems"))
            .build())
        .runTest();
  }

  @Test
  public void getter_returnsUnmodifiableEmptyListIfBuilderIsEmpty() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("new Receipt.Builder().%s.add(new Item.Builder());",
                convention.get("buildersOfItems"))
            .build())
        .runTest();
  }

  @Test
  public void getter_returnsListOfItemBuilders() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt.Builder builder = new Receipt.Builder().addItems(candy, apple);")
            .addLine("assertThat(builder.%s).hasSize(2);", convention.get("buildersOfItems"))
            .addLine("assertThat(builder.%s.get(0).build()).isEqualTo(candy);",
                convention.get("buildersOfItems"))
            .addLine("assertThat(builder.%s.get(1).build()).isEqualTo(apple);",
                convention.get("buildersOfItems"))
            .build())
        .runTest();
  }

  @Test
  public void getter_returnsUnmodifiableListOfItemBuilders() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt.Builder builder = new Receipt.Builder().addItems(candy, apple);")
            .addLine("builder.%s.add(new Item.Builder());", convention.get("buildersOfItems"))
            .build())
        .runTest();
  }

  @Test
  public void getter_returnsListOfMutableItemBuildersUsedInContainingBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt.Builder builder = new Receipt.Builder()")
            .addLine("    .addItems(new Item.Builder(), new Item.Builder());")
            .addLine("builder.%s.get(0).mergeFrom(candy);", convention.get("buildersOfItems"))
            .addLine("builder.%s.get(1).mergeFrom(apple);", convention.get("buildersOfItems"))
            .addLine("assertThat(builder.build().%s).containsExactly(candy, apple).inOrder();",
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
    public void mergeFromBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Receipt.Builder initialBuilder = new Receipt.Builder().addItems(candy);")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .mergeFrom(initialBuilder)")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void buildPartial_cascades() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candy = new Item.Builder().name(\"candy\");")
            .addLine("Item.Builder apple = new Item.Builder().name(\"apple\");")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy)")
            .addLine("    .addItems(apple)")
            .addLine("    .buildPartial();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.buildPartial(), apple.buildPartial()).inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void clearBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").price(15).build();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy)")
            .addLine("    .clear()")
            .addLine("    .addItems(apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(apple);", convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void toBuilder_preservesContainedPartials() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item candy = new Item.Builder().name(\"candy\").buildPartial();")
            .addLine("Receipt initialReceipt = new Receipt.Builder()")
            .addLine("    .addItems(candy)")
            .addLine("    .buildPartial();")
            .addLine("Item apple = new Item.Builder().name(\"apple\").buildPartial();")
            .addLine("Receipt receipt = initialReceipt.toBuilder().addItems(apple).build();")
            .addLine("assertThat(receipt.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .runTest();

  }

  @Test
  public void varargsAddValueInstances_genericFieldCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<Integer>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends Receipt_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15).build();")
            .addLine("Item<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy, apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
  }

  @Test
  public void varargAddValueInstances_genericTypeCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt<T> {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<T>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder<T> toBuilder();")
            .addLine("  class Builder<T> extends Receipt_Builder<T> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15).build();")
            .addLine("Item<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50).build();")
            .addLine("Receipt<Integer> value = new Receipt.Builder<Integer>()")
            .addLine("    .addItems(candy, apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
  }

  @Test
  public void varargsAddBuilders_genericFieldCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<Integer>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends Receipt_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item.Builder<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15);")
            .addLine("Item.Builder<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy.build(), apple.build())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
  }

  @Test
  public void varargAddBuilders_genericTypeCompilesWithoutHeapPollutionWarnings() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt<T> {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<T>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder<T> toBuilder();")
            .addLine("  class Builder<T> extends Receipt_Builder<T> {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item.Builder<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15);")
            .addLine("Item.Builder<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50);")
            .addLine("Receipt<Integer> value = new Receipt.Builder<Integer>()")
            .addLine("    .addItems(candy.build(), apple.build())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
  }

  @Test
  public void canOverrideVarargsAddValueInstancesForGenericField() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<Integer>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends Receipt_Builder {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder addItems(Item<Integer>... items) {")
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15).build();")
            .addLine("Item<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50).build();")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy, apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
  }

  @Test
  public void canOverrideVarargAddValueInstancesForGenericType() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt<T> {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<T>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder<T> toBuilder();")
            .addLine("  class Builder<T> extends Receipt_Builder<T> {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder<T> addItems(Item<T>... items) {")
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15).build();")
            .addLine("Item<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50).build();")
            .addLine("Receipt<Integer> value = new Receipt.Builder<Integer>()")
            .addLine("    .addItems(candy, apple)")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy, apple).inOrder();",
                convention.get("items"))
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
  }

  @Test
  public void canOverrideVarargsAddBuildersForGenericField() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<Integer>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends Receipt_Builder {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder addItems(Item.Builder<Integer>... items) {")
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item.Builder<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15);")
            .addLine("Item.Builder<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(candy.build(), apple.build())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
  }

  @Test
  public void canOverrideVarargAddBuildersForGenericType() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface Receipt<T> {")
            .addLine("  @%s", FreeBuilder.class)
            .addLine("  interface Item<T> {")
            .addLine("    String name();")
            .addLine("    T price();")
            .addLine("")
            .addLine("    Builder<T> toBuilder();")
            .addLine("    class Builder<T> extends Receipt_Item_Builder<T> {}")
            .addLine("  }")
            .addLine("")
            .addLine("  %s<Item<T>> %s;", List.class, convention.get("items"))
            .addLine("")
            .addLine("  Builder<T> toBuilder();")
            .addLine("  class Builder<T> extends Receipt_Builder<T> {")
            .addLine("    @%s", Override.class)
            .addLine("    @%s", SafeVarargs.class)
            .addLine("    @%s(\"varargs\")", SuppressWarnings.class)
            .addLine("    public final Builder<T> addItems(Item.Builder<T>... items) {")
            .addLine("      return super.addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("Item.Builder<Integer> candy =")
            .addLine("    new Item.Builder<Integer>().name(\"candy\").price(15);")
            .addLine("Item.Builder<Integer> apple =")
            .addLine("    new Item.Builder<Integer>().name(\"apple\").price(50);")
            .addLine("Receipt<Integer> value = new Receipt.Builder<Integer>()")
            .addLine("    .addItems(candy.build(), apple.build())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get("items"))
            .addLine("    .containsExactly(candy.build(), apple.build()).inOrder();")
            .build())
        .compiles()
        .withNoWarnings()
        .allTestsPass();
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
