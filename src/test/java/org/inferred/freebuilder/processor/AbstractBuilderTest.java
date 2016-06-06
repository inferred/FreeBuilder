package org.inferred.freebuilder.processor;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.junit.Test;

import javax.tools.JavaFileObject;

public class AbstractBuilderTest {
  private final BehaviorTester behaviorTester = new BehaviorTester();

  private static final JavaFileObject TYPE_WITH_ABSTRACT_BUILDER = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class TypeWithAbstractBuilder {")
      .addLine("  public abstract int getItem();")
      .addLine("")
      .addLine("  public abstract static class Builder")
      .addLine("      extends TypeWithAbstractBuilder_Builder {}")
      .addLine("}")
      .build();

  @Test
  public void testGenericWithConstraint() {
    behaviorTester
        .with(new Processor())
        .with(TYPE_WITH_ABSTRACT_BUILDER)
        .compiles()
        .withNoWarnings();
  }
}
