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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;

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

import java.util.Iterator;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ListMultimapMutateMethodTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.WITH_GUAVA_AND_LAMBDAS;
  }

  private static final JavaFileObject UNCHECKED_PROPERTY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String, String> getItems();", ListMultimap.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject CHECKED_PROPERTY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String, String> getItems();", ListMultimap.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder putItems(String key, String value) {")
      .addLine("      %s.checkArgument(!key.isEmpty(), \"key may not be empty\");",
          Preconditions.class)
      .addLine("      %s.checkArgument(!value.isEmpty(), \"value may not be empty\");",
          Preconditions.class)
      .addLine("      return super.putItems(key, value);")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject INTERNED_PROPERTY = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s<String, String> getItems();", ListMultimap.class)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {")
      .addLine("    @Override public Builder putItems(String key, String value) {")
      .addLine("      return super.putItems(key.intern(), value.intern());")
      .addLine("    }")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void mutateAndPutModifiesUnderlyingProperty_whenUnchecked() {
    behaviorTester
        .with(new Processor(features))
        .with(UNCHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("      items.put(\"one\", \"A\");")
            .addLine("      items.put(\"two\", \"B\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutModifiesUnderlyingProperty_whenChecked() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("      items.put(\"one\", \"A\");")
            .addLine("      items.put(\"two\", \"B\");")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value may not be empty");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder().mutateItems(items -> items.put(\"one\", \"\"));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items.put(\"one\", s))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().values().iterator().next()).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllValuesModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> {")
            .addLine("      items.putAll(\"one\", ImmutableList.of(\"A\", \"a\"));")
            .addLine("      items.putAll(\"two\", ImmutableList.of(\"B\", \"b\"));")
            .addLine("    })")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\", \"a\")")
            .addLine("    .and(\"two\", \"B\", \"b\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllValuesChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value may not be empty");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder().mutateItems(items -> items")
            .addLine("    .putAll(\"one\", ImmutableList.of(\"A\", \"\")));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllValuesKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .putAll(\"one\", ImmutableList.of(s, \"bazbam\")))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().values().iterator().next()).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllMultimapModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .putAll(ImmutableMultimap.of(\"one\", \"A\", \"two\", \"B\")))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\")")
            .addLine("    .and(\"two\", \"B\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllMultimapChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value may not be empty");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder().mutateItems(items -> items")
            .addLine("    .putAll(ImmutableMultimap.of(\"one\", \"A\", \"two\", \"\")));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndPutAllMultimapKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .putAll(ImmutableMultimap.of(\"one\", s, \"two\", \"bazbam\")))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().values().iterator().next()).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndReplaceValuesModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"i\")")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .replaceValues(\"one\", ImmutableList.of(\"A\", \"a\")))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\", \"a\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndReplaceValuesChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value may not be empty");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"i\")")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .replaceValues(\"one\", ImmutableList.of(\"A\", \"\")));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndReplaceValuesKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"i\")")
            .addLine("    .mutateItems(items -> items")
            .addLine("        .replaceValues(\"one\", ImmutableList.of(s, \"a\")))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().values().iterator().next()).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaGetModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .mutateItems(items -> items.get(\"one\").add(\"a\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\", \"a\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaGetChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value may not be empty");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .mutateItems(items -> items.get(\"one\").add(\"\"));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaGetKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .mutateItems(items -> items.get(\"one\").add(s))")
            .addLine("    .build();")
            .addLine("Iterator<? extends String> it = value.getItems().values().iterator();")
            .addLine("it.next();")
            .addLine("assertThat(it.next()).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaAsMapModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .mutateItems(items -> items.asMap().get(\"one\").add(\"a\"))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\", \"a\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaAsMapChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value may not be empty");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .mutateItems(items -> items.asMap().get(\"one\").add(\"\"));")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddViaAsMapKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putItems(\"one\", \"A\")")
            .addLine("    .mutateItems(items -> items.asMap().get(\"one\").add(s))")
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().get(\"one\").get(1)).isSameAs(i);")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexViaAsMapModifiesUnderlyingProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", ImmutableList.of(\"A\", \"B\", \"C\"))")
            .addLine("    .mutateItems(items -> %s.asMap(items).get(\"one\").add(2, \"foo\"))",
                Multimaps.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems())")
            .addLine("    .contains(\"one\", \"A\", \"B\", \"foo\", \"C\")")
            .addLine("    .andNothingElse()")
            .addLine("    .inOrder();")
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexViaAsMapChecksArguments() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("value may not be empty");
    behaviorTester
        .with(new Processor(features))
        .with(CHECKED_PROPERTY)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", ImmutableList.of(\"A\", \"B\", \"C\"))")
            .addLine("    .mutateItems(items -> %s.asMap(items).get(\"one\").add(2, \"\"));",
                Multimaps.class)
            .build())
        .runTest();
  }

  @Test
  public void mutateAndAddAtIndexViaAsMapKeepsSubstitute() {
    behaviorTester
        .with(new Processor(features))
        .with(INTERNED_PROPERTY)
        .with(testBuilder()
            .addLine("String s = new String(\"foobar\");")
            .addLine("String i = s.intern();")
            .addLine("assertThat(s).isNotSameAs(i);")
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .putAllItems(\"one\", ImmutableList.of(\"A\", \"B\", \"C\"))")
            .addLine("    .mutateItems(items -> %s.asMap(items).get(\"one\").add(2, s))",
                Multimaps.class)
            .addLine("    .build();")
            .addLine("assertThat(value.getItems().get(\"one\").get(2)).isSameAs(i);")
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
