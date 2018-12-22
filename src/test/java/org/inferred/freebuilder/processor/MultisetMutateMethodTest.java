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

import static org.junit.Assume.assumeTrue;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Before;
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

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class MultisetMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "Multiset<{0}>, checked={1}, {2}, {3}")
  public static Iterable<Object[]> featureSets() {
    List<ElementFactory> elements = Arrays.asList(ElementFactory.values());
    List<Boolean> checkedAndInterned = ImmutableList.of(false, true);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA_AND_LAMBDAS;
    return () -> Lists
        .cartesianProduct(elements, checkedAndInterned, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory element;
  private final NamingConvention convention;
  private final boolean checked;
  private final boolean interned;
  private final FeatureSet features;

  private final JavaFileObject dataType;

  public MultisetMutateMethodTest(
      ElementFactory element,
      boolean checkedAndInterned,
      NamingConvention convention,
      FeatureSet features) {
    this.element = element;
    this.checked = checkedAndInterned;
    this.interned = checkedAndInterned;
    this.convention = convention;
    this.features = features;

    SourceBuilder dataType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s> %s;", Multiset.class, element.type(), convention.get("properties"))
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {");
    if (checkedAndInterned) {
      dataType
          .addLine("    @Override public Builder setCountOfProperties(%s element, int count) {",
              element.unwrappedType())
          .addLine("      %s.checkArgument(%s, \"%s\");",
              Preconditions.class, element.validation(), element.errorMessage())
          .addLine("      return super.setCountOfProperties(%s, count);",
              element.intern("element"))
          .addLine("    }");
    }
    dataType
        .addLine("  }")
        .addLine("}");
    this.dataType = dataType.build();
  }

  @Before
  public void before() {
    behaviorTester
        .with(new Processor(features))
        .withPermittedPackage(NonComparable.class.getPackage());
  }

  @Test
  public void mutateAndAddModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s))", element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.examples(0, 1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(element.errorMessage());
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s));", element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAcceptsMaxIntOccurrences() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToProperties(%s, Integer.MAX_VALUE - 1)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s))", element.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.count(%s)).isEqualTo(Integer.MAX_VALUE);",
                convention.get("properties"), element.examples(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddRejectsMaxIntPlusOneOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("too many occurrences: 2147483648");
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToProperties(%s, Integer.MAX_VALUE)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s));", element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", element.type(), element.example(0))
            .addLine("%s i = %s;", element.type(), element.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.add(s))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.%s, 0)).isSameAs(i);", convention.get("properties"))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s, 3))", element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.examples(0, 1, 1, 1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleRejectsNegativeOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("occurrences cannot be negative: -2");
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s, -2));", element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleAcceptsMaxIntOccurrences() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s, Integer.MAX_VALUE))",
                element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.count(%s)).isEqualTo(Integer.MAX_VALUE);",
                convention.get("properties"), element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleRejectsMaxIntPlusOneOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("too many occurrences: 2147483648");
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s, Integer.MAX_VALUE));",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleReturnsOldCount() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> assertThat(set.add(%s, 3)).isEqualTo(1));",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(element.errorMessage());
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.add(%s, 3));", element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddMultipleKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", element.type(), element.example(0))
            .addLine("%s i = %s;", element.type(), element.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.add(s, 3))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.%s, 1)).isSameAs(i);", convention.get("properties"))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.setCount(%s, 3))", element.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.examples(0, 0, 0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountReturnsOldCount() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> assertThat(set.setCount(%s, 3)).isEqualTo(1));",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(element.errorMessage());
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.setCount(%s, 3));", element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountChecksCount() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("count cannot be negative but was: -3");
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.setCount(%s, -3));", element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSetCountKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", element.type(), element.example(0))
            .addLine("%s i = %s;", element.type(), element.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.setCount(s, 3))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.%s, 1)).isSameAs(i);", convention.get("properties"))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountModifiesUnderlyingPropertyIfOldCountMatches() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.setCount(%s, 1, 3))", element.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.examples(0, 0, 0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountDoesNothingIfOldCountDoesNotMatch() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.setCount(%s, 2, 3))", element.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountReturnsTrueIfOldCountMatches() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> assertThat(set.setCount(%s, 1, 3)).isTrue());",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountReturnsFalseIfOldCountDoesNotMatch() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> assertThat(set.setCount(%s, 2, 3)).isFalse());",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(element.errorMessage());
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.setCount(%s, 0, 3));",
                element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountChecksCount() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("newCount cannot be negative but was: -3");
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.setCount(%s, 1, -3));", element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndConditionallySetCountKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", element.type(), element.example(0))
            .addLine("%s i = %s;", element.type(), element.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.setCount(s, 3))")
            .addLine("    .build();")
            .addLine("assertThat(get(value.%s, 1)).isSameAs(i);", convention.get("properties"))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(%s)))",
                element.examples(1, 1, 2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.examples(0, 1, 1, 2))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(element.errorMessage());
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(%s, %s)));",
                element.example(1), element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllAcceptsMaxIntOccurrences() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addCopiesToProperties(%s, Integer.MAX_VALUE - 2)", element.example(0))
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(%s)))",
                element.examples(0, 1, 0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.count(%s)).isEqualTo(Integer.MAX_VALUE);",
                convention.get("properties"), element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllRejectsMaxIntPlusOneOccurrences() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("too many occurrences: 2147483648");
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addCopiesToProperties(%s, Integer.MAX_VALUE - 1)", element.example(0))
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(%s)));",
                element.examples(0, 1, 0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAllKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", element.type(), element.example(0))
            .addLine("%s i = %s;", element.type(), element.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateProperties(set -> set.addAll(ImmutableList.of(s, %s, s)))",
                element.example(1))
            .addLine("    .build();")
            .addLine("assertThat(get(value.%s, 0)).isSameAs(i);", convention.get("properties"))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndSizeReturnsSize() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> assertThat(set.size()).equals(1));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndContainsReturnsTrueForContainedElement() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> assertThat(set.contains(%s)).isTrue());",
                element.example(0))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndContainsReturnsFalseForMissingElement() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> assertThat(set.contains(%s)).isFalse());",
                element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndIterateFindsContainedElement() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.example(0))
            .addLine("    .mutateProperties(set -> {")
            .addLine("      assertThat(%s.copyOf(set.iterator())).containsExactly(%s);",
                ImmutableMultiset.class, element.example(0))
            .addLine("    });")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndRemoveModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.examples(0, 1))
            .addLine("    .mutateProperties(set -> set.remove(%s))", element.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndCallRemoveOnIteratorModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.examples(0, 1))
            .addLine("    .mutateProperties(set -> {")
            .addLine("      Iterator<%s> it = set.iterator();", element.type())
            .addLine("      while (it.hasNext()) {")
            .addLine("        if (it.next().equals(%s)) {", element.example(0))
            .addLine("          it.remove();")
            .addLine("        }")
            .addLine("      }")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).containsExactly(%s);",
                convention.get("properties"), element.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndClearModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addProperties(%s)", element.examples(0, 1))
            .addLine("    .mutateProperties(set -> set.clear())")
            .addLine("    .build();")
            .addLine("assertThat(value.%s).isEmpty();",  convention.get("properties"))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType")
        .addPackageImport("java.util")
        .addPackageImport("com.google.common.collect")
        .addStaticImport(Iterables.class, "get");
  }
}
