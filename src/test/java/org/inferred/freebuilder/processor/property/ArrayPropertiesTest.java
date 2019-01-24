package org.inferred.freebuilder.processor.property;

import com.google.common.testing.EqualsTester;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.Processor;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.feature.StaticFeatureSet;
import org.inferred.freebuilder.processor.source.testing.BehaviorTester;
import org.inferred.freebuilder.processor.source.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ArrayPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private final FeatureSet features = new StaticFeatureSet();
  private final BehaviorTester behaviorTester = BehaviorTester.create(features);

  @Test
  public void testToString() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  int[] ints();")
            .addLine("  String[] strings();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .ints(new int[] { 1, 2, 3 })")
            .addLine("    .strings(new String[] { \"a\", \"b\", \"c\" })")
            .addLine("    .build();")
            .addLine("assertThat(value.toString()).isEqualTo(")
            .addLine("    \"DataType{ints=[1, 2, 3], strings=[a, b, c]}\");")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  int[] ints();")
            .addLine("")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("int[] a = { 1, 2, 3 };")
            .addLine("int[] b = { 1, 2, 3 };")
            .addLine("int[] c = new int[0];")
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("         new DataType.Builder().ints(a).build(),")
            .addLine("         new DataType.Builder().ints(a).build())")
            .addLine("    .addEqualityGroup(")
            .addLine("         new DataType.Builder().ints(b).build(),")
            .addLine("         new DataType.Builder().ints(b).build())")
            .addLine("    .addEqualityGroup(")
            .addLine("         new DataType.Builder().ints(c).build(),")
            .addLine("         new DataType.Builder().ints(c).build())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
