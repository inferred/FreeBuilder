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

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.junit.Test;

public class AbstractBuilderTest {
  private final BehaviorTester behaviorTester = BehaviorTester.create(new StaticFeatureSet());

  private static final CompilationUnitBuilder TYPE_WITH_ABSTRACT_BUILDER = CompilationUnitBuilder
      .forTesting()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class TypeWithAbstractBuilder {")
      .addLine("  public abstract int getItem();")
      .addLine("")
      .addLine("  public abstract static class Builder")
      .addLine("      extends TypeWithAbstractBuilder_Builder {}")
      .addLine("}");

  @Test
  public void testGenericWithConstraint() {
    behaviorTester
        .with(new Processor())
        .with(TYPE_WITH_ABSTRACT_BUILDER)
        .compiles()
        .withNoWarnings();
  }
}
