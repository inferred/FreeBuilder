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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;

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

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ListMultimapMutateMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "ListMultimap<{0}, {1}>, checked={2}, {3}, {4}")
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
  private final JavaFileObject dataType;

  public ListMultimapMutateMethodTest(
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

    SourceBuilder dataType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s, %s> %s;", ListMultimap.class, key.type(), value.type(), convention.get())
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
    this.dataType = dataType.build();
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
            .addLine("    .mutateItems(items -> items.put(%s, s))", key.example(1))
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
            .addLine("    .putAll(ImmutableMultimap.of(%s, %s, %s, %s)));",
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
            .addLine("    .putItems(%s, %s)", key.example(1), value.example(2))
            .addLine("    .mutateItems(items -> items")
            .addLine("        .replaceValues(%s, ImmutableList.of(s, %s)))",
                key.example(1), value.example(3))
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
            .addLine("assertThat(value.%s.get(%s).get(1)).isSameAs(i);",
                convention.get(), key.example(1))
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexViaAsMapModifiesUnderlyingProperty() {
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s, ImmutableList.of(%s))",
                key.example(0), value.examples(1, 2, 3))
            .addLine("    .mutateItems(items -> %s.asMap(items).get(%s).add(2, %s))",
                Multimaps.class, key.example(0), value.examples(4))
            .addLine("    .build();")
            .addLine("assertThat(value.%s)", convention.get())
            .addLine("    .contains(%s, %s)", key.example(0), value.examples(1, 2, 4, 3))
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexViaAsMapChecksArguments() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(value.errorMessage("value"));
    }
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putAllItems(%s, ImmutableList.of(%s))",
                key.example(0), value.examples(1, 2, 3))
            .addLine("    .mutateItems(items -> %s.asMap(items).get(%s).add(2, %s));",
                Multimaps.class, key.example(0), value.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexViaAsMapKeepsSubstitute() {
    assumeTrue(interned);
    behaviorTester
        .with(dataType)
        .with(testBuilder()
            .addLine("%1$s s = new %1$s(%2$s);", value.type(), value.example(0))
            .addLine("%s i = %s;", value.type(), value.intern("s"))
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(%s, ImmutableList.of(%s))",
                key.example(0), value.examples(1, 2, 3))
            .addLine("    .mutateItems(items -> %s.asMap(items).get(%s).add(2, s))",
                Multimaps.class, key.example(0))
            .addLine("    .build();")
            .addLine("assertThat(value.%s.get(%s).get(2)).isSameAs(i);",
                convention.get(), key.example(0))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomFunctionalInterface() throws IOException {
    SourceBuilder customMutatorType = new SourceBuilder();
    for (String line : dataType.getCharContent(true).toString().split("\n")) {
      customMutatorType.addLine("%s", line);
      if (line.contains("extends DataType_Builder")) {
        customMutatorType
            .addLine("    public interface Mutator {")
            .addLine("      void mutate(%s<%s, %s> multimap);",
                ListMultimap.class, key.type(), value.type())
            .addLine("    }")
            .addLine("    @Override public Builder mutateItems(Mutator mutator) {")
            .addLine("      return super.mutateItems(mutator);")
            .addLine("    }");
      }
    }

    behaviorTester
        .with(customMutatorType.build())
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
        .addImport(Iterator.class);
  }
}
