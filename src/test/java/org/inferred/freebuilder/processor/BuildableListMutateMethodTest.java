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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;

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
public class BuildableListMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}, {1}")
  public static Iterable<Object[]> featureSets() {
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA_AND_LAMBDAS;
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

  public BuildableListMutateMethodTest(NamingConvention convention, FeatureSet features) {
    this.convention = convention;
    this.features = features;
    buildableListType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface Receipt {")
        .addLine("  @%s", FreeBuilder.class)
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
  public void mutateGivesAccessToUnderlyingBuilders() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candy = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .addItems(new Item.Builder())")
            .addLine("    .mutateItems(list -> list.get(0)")
            .addLine("         .mergeFrom(candy))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy.build());",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Item.Builder candy = new Item.Builder().name(\"candy\").price(15);")
            .addLine("Receipt value = new Receipt.Builder()")
            .addLine("    .mutateItems(list -> list.add(candy))")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(candy.build());",
                convention.get("items"))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAliasesBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(buildableListType)
        .with(testBuilder()
            .addLine("Receipt.Builder builder = new Receipt.Builder();")
            .addLine("Item.Builder itemBuilder = new Item.Builder().name(\"candy\").price(15);")
            .addLine("builder.mutateItems(list -> list.add(itemBuilder));")
            .addLine("itemBuilder.name(\"apple\").price(50);")
            .addLine("builder.mutateItems(list -> list.add(itemBuilder));")
            .addLine("itemBuilder.name(\"poison\").price(500);")
            .addLine("Item poison = itemBuilder.build();")
            .addLine("Receipt value = builder.build();")
            .addLine("assertThat(value.%s).containsExactly(poison, poison);",
                convention.get("items"))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.Receipt")
        .addImport("com.example.Receipt.Item")
        .addImport(ImmutableList.class)
        .addImport(Stream.class)
        .addStaticImport(Truth.class, "assertThat");
  }
}
