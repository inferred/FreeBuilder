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
import static org.junit.Assume.assumeTrue;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.testtype.NonComparable;
import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
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
import java.util.Iterator;
import java.util.List;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class SetMultimapMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "SetMultimap<{0}, {1}>, checked={2}, {3}, {4}")
  public static Iterable<Object[]> featureSets() {
    List<Boolean> checkedAndInterned = ImmutableList.of(false, true);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.WITH_GUAVA;
    return () -> Lists
        .cartesianProduct(TYPES, TYPES, checkedAndInterned, conventions, features)
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory key;
  private final ElementFactory value;
  private final boolean checked;
  private final boolean interned;
  private final NamingConvention convention;
  private final FeatureSet features;
  private final CompilationUnitBuilder dataType;

  public SetMultimapMutateMethodTest(
      ElementFactory key,
      ElementFactory value,
      boolean checkedAndInterned,
      NamingConvention convention,
      FeatureSet features) {
    this.key = key;
    this.value = value;
    this.checked = checkedAndInterned;
    this.interned = checkedAndInterned;
    this.convention = convention;
    this.features = features;

    dataType = CompilationUnitBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s, %s> %s;", SetMultimap.class, key.type(), value.type(), convention.get())
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {");
    if (checkedAndInterned) {
      dataType
          .addLine("    @Override public Builder putItems(%s key, %s value) {",
              key.unwrappedType(), value.unwrappedType())
          .addLine("      %s.checkArgument(%s, \"%s\");",
              Preconditions.class, key.validation("key"), key.errorMessage("key"))
          .addLine("      %s.checkArgument(%s, \"%s\");",
              Preconditions.class, value.validation("value"), value.errorMessage("value"))
          .addLine("      return super.putItems(%s, %s);", key.intern("key"), value.intern("value"))
          .addLine("    }");
    }
    dataType
        .addLine("  }")
        .addLine("}");
  }

  @Before
  public void before() {
    behaviorTester
        .with(new Processor(features))
        .withPermittedPackage(NonComparable.class.getPackage());
  }

  @Test
  public void mutateAndPutModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("      items.put(%s, %s);", key.example(0), value.example(1))
            .addLine("      items.put(%s, %s);", key.example(2), value.example(3))
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(value.errorMessage("value"));
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder().mutateItems(items -> items.put(%s, %s));",
                key.example(0), value.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", value.type(), value.example(0))
            .addLine("%s i = %s;", value.type(), value.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.put(%s, s))", key.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.values().iterator().next()).isSameAs(i);",
                convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllValuesModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("      items.putAll(%s, ImmutableList.of(%s));",
                key.example(0), value.examples(1, 2))
            .addLine("      items.putAll(%s, ImmutableList.of(%s));",
                key.example(3), value.examples(4, 5))
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.examples(1, 2))
            .addLine("    .and(%s, %s)", key.example(3), value.examples(4, 5))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllValuesChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(value.errorMessage("value"));
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder().mutateItems(items -> items")
            .addLine("    .putAll(%s, ImmutableList.of(%s, %s)));",
                key.example(0), value.example(1), value.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllValuesKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", value.type(), value.example(0))
            .addLine("%s i = %s;", value.type(), value.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .putAll(%s, ImmutableList.of(s, %s)))",
                key.example(0), value.example(1))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.values().iterator().next()).isSameAs(i);",
                convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllMultimapModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .putAll(ImmutableMultimap.of(%s, %s, %s, %s)))",
                key.example(0), value.example(1), key.example(2), value.example(3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllMultimapChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(value.errorMessage("value"));
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder().mutateItems(items -> items")
            .addLine("        .putAll(ImmutableMultimap.of(%s, %s, %s, %s)));",
                key.example(0), value.example(1), key.example(2), value.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllMultimapKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", value.type(), value.example(0))
            .addLine("%s i = %s;", value.type(), value.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .putAll(ImmutableMultimap.of(%s, s, %s, %s)))",
                key.example(0), key.example(1), value.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.values().iterator().next()).isSameAs(i);",
                convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndReplaceValuesModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .mutateItems(items -> items")
            .addLine("        .replaceValues(%s, ImmutableList.of(%s)))",
                key.example(0), value.examples(2, 3))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.examples(2, 3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndReplaceValuesChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(value.errorMessage("value"));
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .mutateItems(items -> items")
            .addLine("        .replaceValues(%s, ImmutableList.of(%s, %s)));",
                key.example(0), value.example(2), value.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndReplaceValuesKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", value.type(), value.example(0))
            .addLine("%s i = %s;", value.type(), value.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .mutateItems(items -> items")
            .addLine("        .replaceValues(%s, ImmutableList.of(s, %s)))",
                key.example(0), value.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.values().iterator().next()).isSameAs(i);",
                convention.get())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaGetModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .mutateItems(items -> items.get(%s).add(%s))",
                key.example(0), value.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.examples(1, 2))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaGetChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(value.errorMessage("value"));
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .mutateItems(items -> items.get(%s).add(%s));",
                key.example(0), value.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaGetKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", value.type(), value.example(0))
            .addLine("%s i = %s;", value.type(), value.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(1), value.example(2))
            .addLine("    .mutateItems(items -> items.get(%s).add(s))", key.example(1))
            .addLine("    .build();")
            .addLine("Iterator<? extends %s> it = value.%s.values().iterator();",
                value.type(), convention.get())
            .addLine("it.next();")
            .addLine("assertThat(it.next()).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaAsMapModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .mutateItems(items -> items.asMap().get(%s).add(%s))",
                key.example(0), value.example(2))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.examples(1, 2))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaAsMapChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(value.errorMessage("value"));
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(0), value.example(1))
            .addLine("    .mutateItems(items -> items.asMap().get(%s).add(%s));",
                key.example(0), value.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaAsMapKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", value.type(), value.example(0))
            .addLine("%s i = %s;", value.type(), value.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(%s, %s)", key.example(1), value.example(2))
            .addLine("    .mutateItems(items -> items.asMap().get(%s).add(s))", key.example(1))
            .addLine("    .build();")
            .addLine("assertThat(Iterables.get(value.%s.get(%s), 1)).isSameAs(i);",
                convention.get(), key.example(1))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomFunctionalInterface() {
    CompilationUnitBuilder customMutatorType = CompilationUnitBuilder.forTesting();
    for (String line : dataType.toString().split("\n")) {
      if (line.contains("extends DataType_Builder")) {
        int insertOffset = line.indexOf('{') + 1;
        customMutatorType
            .addLine("%s", line.substring(0, insertOffset))
            .addLine("    public interface Mutator {")
            .addLine("      void mutate(%s<%s, %s> multimap);",
                SetMultimap.class, key.type(), value.type())
            .addLine("    }")
            .addLine("    @Override public Builder mutateItems(Mutator mutator) {")
            .addLine("      return super.mutateItems(mutator);")
            .addLine("    }")
            .addLine("%s", line.substring(insertOffset));
      } else {
        customMutatorType.addLine("%s", line);
      }
    }

    behaviorTester
        .with(customMutatorType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("      items.put(%s, %s);", key.example(0), value.example(1))
            .addLine("      items.put(%s, %s);", key.example(2), value.example(3))
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.example(1))
            .addLine("    .and(%s, %s)", key.example(2), value.example(3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addStaticImport(MultimapSubject.class, "assertThat")
        .addImport("com.example.DataType")
        .addImport(ImmutableList.class)
        .addImport(ImmutableMultimap.class)
        .addImport(Iterables.class)
        .addImport(Iterator.class);
  }
}
