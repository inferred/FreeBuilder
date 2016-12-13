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

import static org.inferred.freebuilder.processor.util.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;
import static org.junit.Assume.assumeTrue;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.testing.EqualsTester;

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
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SetPropertyTest {

  public enum TestConvention {
    PREFIXLESS("prefixless", "items()"), BEAN("bean", "getItems()");

    private final String name;
    private final String getter;

    TestConvention(String name, String getter) {
      this.name = name;
      this.getter = getter;
    }

    public String getter() {
      return getter;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  @SuppressWarnings("unchecked")
  @Parameters(name = "Set<{0}>, {1}, {2}")
  public static Iterable<Object[]> parameters() {
    List<ElementFactory> elements = Arrays.asList(ElementFactory.values());
    List<TestConvention> conventions = Arrays.asList(TestConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(elements, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  private final ElementFactory elements;
  private final TestConvention convention;
  private final FeatureSet features;

  private final JavaFileObject setPropertyType;
  private final String validationErrorMessage;
  private final JavaFileObject validatedType;

  public SetPropertyTest(
      ElementFactory elements, TestConvention convention, FeatureSet features) {
    this.elements = elements;
    this.convention = convention;
    this.features = features;
    setPropertyType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract %s<%s> %s;", Set.class, elements.type(), convention.getter())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("}")
        .build();

    validationErrorMessage = elements.errorMessage();

    validatedType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract %s<%s> %s;", Set.class, elements.type(), convention.getter())
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {")
        .addLine("    @Override public Builder addItems(%s element) {", elements.unwrappedType())
        .addLine("      %s.checkArgument(%s, \"%s\");",
            Preconditions.class, elements.validation(), validationErrorMessage)
        .addLine("      return super.addItems(element);")
        .addLine("    }")
        .addLine("  }")
        .addLine("}")
        .build();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testDefaultEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder().build();")
            .addLine("assertThat(value.%s).isEmpty();", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems((%s) null);", elements.type())
            .build())
        .runTest();
  }

  @Test
  public void testAddSingleElement_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_null() {
    assumeTrue(elements.canRepresentSingleNullElement());
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(%s, null);", elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddVarargs_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s))", ImmutableList.class, elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(%s.asList(%s, null));", Arrays.class, elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_duplicate() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(%s))", ImmutableList.class, elements.examples(0, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIterable_iteratesOnce() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(new %s<>(%s))", DodgyIterable.class, elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(%s))", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_null() {
    assumeStreamsAvailable();
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(%s, null));", elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllStream_duplicate() {
    assumeStreamsAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(Stream.of(%s))", elements.examples(0, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIntStream() {
    assumeStreamsAvailable();
    assumeTrue(elements == ElementFactory.INTEGERS);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 2))", IntStream.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(1, 2).inOrder();", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testAddAllIntStream_duplicate() {
    assumeStreamsAvailable();
    assumeTrue(elements == ElementFactory.INTEGERS);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addAllItems(%s.of(1, 1))", IntStream.class)
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(1).inOrder();", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .removeItems(%s)", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.getter(), elements.example(1))
            .build())
        .runTest();
  }

  @Test
  public void testRemove_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .removeItems((%s) null);", elements.type())
            .build())
        .runTest();
  }

  @Test
  public void testRemove_missingElement() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .removeItems(%s)", elements.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testClear_noElements() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .clearItems()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testClear_twoElements() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .clearItems()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsLiveView() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<%s> itemsView = builder.%s;",
                Set.class, elements.type(), convention.getter())
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(%s);", elements.examples(0, 1))
            .addLine("assertThat(itemsView).containsExactly(%s).inOrder();",
                elements.examples(0, 1))
            .addLine("builder.clearItems();")
            .addLine("assertThat(itemsView).isEmpty();")
            .addLine("builder.addItems(%s);", elements.examples(2, 3))
            .addLine("assertThat(itemsView).containsExactly(%s).inOrder();",
                elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testGet_returnsUnmodifiableSet() {
    thrown.expect(UnsupportedOperationException.class);
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder();")
            .addLine("%s<%s> itemsView = builder.%s;",
                Set.class, elements.type(), convention.getter())
            .addLine("itemsView.add(%s);", elements.example(0))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .build();")
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(value);")
            .addLine("assertThat(builder.build().%s)", convention.getter())
            .addLine("    .containsExactly(%s).inOrder();", elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType.Builder template = new DataType.Builder()")
            .addLine("    .addItems(%s);", elements.examples(0, 1))
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .mergeFrom(template);")
            .addLine("assertThat(builder.build().%s)", convention.getter())
            .addLine("    .containsExactly(%s).inOrder();", elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.examples(0, 1))
            .addLine("    .clear()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(2, 3))
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
                Set.class, elements.type(), convention.getter())
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(%s... items) {", elements.unwrappedType())
            .addLine("      addItems(items);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder(%s)", elements.example(0))
            .addLine("    .clear()")
            .addLine("    .addItems(%s)", elements.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(2, 3))
            .build())
        .runTest();
  }

  @Test
  public void testImmutableSetProperty() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s> %s;", ImmutableSet.class, elements.type(), convention.getter())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testValidation_varargsAdd() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(validationErrorMessage);
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addItems(%s, %s);",
                elements.example(0), elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllStream() {
    assumeStreamsAvailable();
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(validationErrorMessage);
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(Stream.of(%s, %s));",
                elements.example(2), elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testValidation_addAllIterable() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(validationErrorMessage);
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(%s.of(%s, %s));",
                ImmutableList.class, elements.example(2), elements.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void testPrimitiveValidation_addAllIntStream() {
    assumeStreamsAvailable();
    assumeTrue(elements == ElementFactory.INTEGERS);
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(validationErrorMessage);
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addLine("new DataType.Builder().addAllItems(%s.of(3, -4));",
                IntStream.class)
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder().build(),")
            .addLine("        new DataType.Builder().build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.examples(0, 1))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.examples(0, 1))
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.example(0))
            .addLine("            .build(),")
            .addLine("        new DataType.Builder()")
            .addLine("            .addItems(%s)", elements.example(0))
            .addLine("            .build())")
            .addLine("    .testEquals();")
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
            .addLine("  @JsonProperty(\"stuff\") %s<%s> %s;",
                Set.class, elements.type(), convention.getter())
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertThat(value.%s).containsExactly(%s).inOrder();",
                convention.getter(), elements.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void testFromReusesImmutableSetInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(value).build();")
            .addLine("assertThat(copy.%1$s).isSameAs(value.%1$s);", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromReusesImmutableSetInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder().mergeFrom(value).build();")
            .addLine("assertThat(copy.%1$s).isSameAs(value.%1$s);", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testMergeFromEmptySetDoesNotPreventReuseOfImmutableSetInstance() {
    assumeGuavaAvailable();
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = new DataType.Builder()")
            .addLine("    .from(value)")
            .addLine("    .mergeFrom(new DataType.Builder())")
            .addLine("    .build();")
            .addLine("assertThat(copy.%1$s).isSameAs(value.%1$s);", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndAdd() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .addItems(%s)", elements.example(2))
            .addLine("    .build();")
            .addLine("assertThat(copy.%s)", convention.getter())
            .addLine("    .containsExactly(%s)", elements.examples(0, 1, 2))
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndRemove() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .removeItems(%s)", elements.example(0))
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).containsExactly(%s);",
                convention.getter(), elements.example(1))
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndClear() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clearItems()")
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).isEmpty();", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testModifyAndClearAll() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder")
            .addLine("    .from(value)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertThat(copy.%s).isEmpty();", convention.getter())
            .build())
        .runTest();
  }

  @Test
  public void testMergeInvalidData() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage(validationErrorMessage);
    behaviorTester
        .with(new Processor(features))
        .with(validatedType)
        .with(testBuilder()
            .addImport(Set.class)
            .addImport(ImmutableSet.class)
            .addLine("DataType value = new DataType() {")
            .addLine("  @Override public Set<%s> %s {", elements.type(), convention.getter())
            .addLine("    return ImmutableSet.of(%s, %s);",
                elements.example(0), elements.invalidExample())
            .addLine("  }")
            .addLine("};")
            .addLine("DataType.Builder.from(value);")
            .build())
        .runTest();
  }

  @Test
  public void testMergeCombinesSets() {
    behaviorTester
        .with(new Processor(features))
        .with(setPropertyType)
        .with(testBuilder()
            .addLine("DataType data1 = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(0))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType data2 = new DataType.Builder()")
            .addLine("    .addItems(%s)", elements.example(2))
            .addLine("    .addItems(%s)", elements.example(1))
            .addLine("    .build();")
            .addLine("DataType copy = DataType.Builder.from(data2).mergeFrom(data1).build();")
            .addLine("assertThat(copy.%s)", convention.getter())
            .addLine("    .containsExactly(%s)", elements.examples(2, 1, 0))
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  private void assumeStreamsAvailable() {
    assumeTrue("Streams available", features.get(SOURCE_LEVEL).stream().isPresent());
  }

  private void assumeGuavaAvailable() {
    assumeTrue("Guava available", features.get(GUAVA).isAvailable());
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addImport(Stream.class);
  }
}
