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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class OptionalMapperMethodTest {

  @SuppressWarnings("unchecked")
  @Parameters(name = "{0}<{1}>, checked={2}, {3}, {4}")
  public static Iterable<Object[]> parameters() {
    List<Class<?>> optionals = Arrays.asList(
        java.util.Optional.class,
        com.google.common.base.Optional.class);
    List<Boolean> checked = ImmutableList.of(false, true);
    List<NamingConvention> conventions = Arrays.asList(NamingConvention.values());
    List<FeatureSet> features = FeatureSets.ALL;
    return () -> Lists
        .cartesianProduct(optionals, TYPES, checked, conventions, features)
        .stream()
        .filter(parameters -> {
          Class<?> optional = (Class<?>) parameters.get(0);
          FeatureSet featureSet = (FeatureSet) parameters.get(4);
          if (optional.equals(com.google.common.base.Optional.class)
              && !featureSet.get(GuavaLibrary.GUAVA).isAvailable()) {
            return false;
          }
          return true;
        })
        .map(List::toArray)
        .iterator();
  }

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  private final ElementFactory element;
  private final boolean checked;
  private final NamingConvention convention;
  private final FeatureSet features;

  private final JavaFileObject dataType;

  public OptionalMapperMethodTest(
      Class<?> optional,
      ElementFactory element,
      boolean checked,
      NamingConvention convention,
      FeatureSet features) {
    this.element = element;
    this.checked = checked;
    this.convention = convention;
    this.features = features;

    SourceBuilder dataType = new SourceBuilder()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  %s<%s> %s;", optional, element.type(), convention.get("property"))
        .addLine("")
        .addLine("  class Builder extends DataType_Builder {");
    if (checked) {
      dataType
          .addLine("    @Override public Builder %s(%s element) {",
              convention.set("property"), element.unwrappedType())
          .addLine("      if (!(%s)) {", element.validation())
          .addLine("        throw new IllegalArgumentException(\"%s\");", element.errorMessage())
          .addLine("      }")
          .addLine("      return super.%s(element);", convention.set("property"))
          .addLine("    }");
    }
    dataType
        .addLine("  }")
        .addLine("}");
    this.dataType = dataType.build();
  }

  @Test
  public void mapReplacesValueToBeReturnedFromGetter() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("property"), element.example(0))
            .addLine("    .mapProperty(a -> a + %s)", element.example(1))
            .addLine("    .build();")
            .addLine("assertEquals(%s + %s, (%s) value.%s.get());",
                element.example(0),
                element.example(1),
                element.unwrappedType(),
                convention.get("property"))
            .build())
        .runTest();
  }

  @Test
  public void mapDelegatesToSetterForValidation() {
    if (checked) {
      thrown.expect(IllegalArgumentException.class);
      thrown.expectMessage(element.errorMessage());
    }
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("property"), element.example(0))
            .addLine("    .mapProperty(a -> %s);", element.invalidExample())
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNull() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("property"), element.example(0))
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapThrowsNpeIfMapperIsNullAndPropertyIsEmpty() {
    thrown.expect(NullPointerException.class);
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .mapProperty(null);")
            .build())
        .runTest();
  }

  @Test
  public void mapAllowsNullReturn() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("property"), element.example(0))
            .addLine("    .mapProperty(a -> null)")
            .addLine("    .build();")
            .addLine("assertFalse(value.%s.isPresent());", convention.get("property"))
            .build())
        .runTest();
  }

  @Test
  public void mapSkipsMapperIfPropertyIsEmpty() {
    behaviorTester
        .with(new Processor(features))
        .with(dataType)
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .mapProperty(a -> { fail(\"mapper called\"); return null; })")
            .addLine("    .build();")
            .addLine("assertFalse(value.%s.isPresent());", convention.get("property"))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomBoxedFunctionalInterface() throws IOException {
    SourceBuilder customMutatorType = new SourceBuilder();
    for (String line : dataType.getCharContent(true).toString().split("\n")) {
      customMutatorType.addLine("%s", line);
      if (line.contains("extends DataType_Builder")) {
        customMutatorType
            .addLine("    public interface Mapper {")
            .addLine("      %1$s map(%1$s value);", element.type())
            .addLine("    }")
            .addLine("    @Override public Builder mapProperty(Mapper mapper) {")
            .addLine("      return super.mapProperty(mapper);")
            .addLine("    }");
      }
    }

    behaviorTester
        .with(new Processor(features))
        .with(customMutatorType.build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("property"), element.example(0))
            .addLine("    .mapProperty(a -> %s)", element.example(1))
            .addLine("    .build();")
            .addLine("assertEquals(%s, (%s) value.%s.get());",
                element.example(1), element.unwrappedType(), convention.get("property"))
            .build())
        .runTest();
  }

  @Test
  public void canUseCustomUnboxedFunctionalInterface() throws IOException {
    SourceBuilder customMutatorType = new SourceBuilder();
    for (String line : dataType.getCharContent(true).toString().split("\n")) {
      customMutatorType.addLine("%s", line);
      if (line.contains("extends DataType_Builder")) {
        customMutatorType
            .addLine("    public interface Mapper {")
            .addLine("      %1$s map(%1$s value);", element.unwrappedType())
            .addLine("    }")
            .addLine("    @Override public Builder mapProperty(Mapper mapper) {")
            .addLine("      return super.mapProperty(mapper);")
            .addLine("    }");
      }
    }

    behaviorTester
        .with(new Processor(features))
        .with(customMutatorType.build())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .%s(%s)", convention.set("property"), element.example(0))
            .addLine("    .mapProperty(a -> %s)", element.example(1))
            .addLine("    .build();")
            .addLine("assertEquals(%s, (%s) value.%s.get());",
                element.example(1), element.unwrappedType(), convention.get("property"))
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
