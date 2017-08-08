package org.inferred.freebuilder.processor.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.processor.FeatureSets;
import org.inferred.freebuilder.processor.util.ObjectsExcerpts.Nullability;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.util.Arrays;
import java.util.List;

import javax.lang.model.type.TypeKind;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ObjectsExcerptsTest {

  public enum ValueSet {
    BOOLEANS("boolean", "Boolean", TypeKind.BOOLEAN, "true", "false"),
    INTS("int", "Integer", TypeKind.INT, "0", "10"),
    FLOATS("float", "Float", TypeKind.FLOAT, "0.0F", "5.5F", "Float.NaN"),
    DOUBLES("double", "Double", TypeKind.DOUBLE, "0.0", "5.5", "Double.NaN"),
    STRINGS("String", "String", TypeKind.DECLARED, "\"\"", "\"hello\"");

    private final String notNullableType;
    private final String nullableType;
    private final TypeKind kind;
    private final List<String> values;

    ValueSet(String notNullableType, String nullableType, TypeKind kind, String... values) {
      this.notNullableType = notNullableType;
      this.nullableType = nullableType;
      this.kind = kind;
      this.values = ImmutableList.copyOf(values);
    }
  }

  @Parameters(name = "{0}, {1}")
  @SuppressWarnings("unchecked")
  public static Iterable<Object[]> parameters() {
    return () -> Lists
        .cartesianProduct(FeatureSets.ALL, Arrays.asList(ValueSet.values()))
        .stream()
        .map(List::toArray)
        .iterator();
  }

  @Shared public BehaviorTester behaviorTester;
  @Parameter(value = 0) public FeatureSet features;
  @Parameter(value = 1) public ValueSet valueSet;

  @Test
  public void testEquals_notNullable() {
    SourceBuilder code = SourceStringBuilder.compilable(features);
    int numValues = valueSet.values.size();
    for (int i = 0; i < numValues; ++i) {
      code.addLine("%s value%s = %s;", valueSet.notNullableType, i, valueSet.values.get(i));
    }
    for (int i = 0; i < numValues; ++i) {
      for (int j = 0; j < numValues; ++j) {
        code.addLine("assert%s(value%s + \" equals \" + value%s, %s);",
                (i == j) ? "True" : "False",
                i,
                j,
                ObjectsExcerpts.equals(
                    "value" + i, "value" + j, valueSet.kind, Nullability.NOT_NULLABLE));
      }
    }
    behaviorTester.with(new TestBuilder().addLine("%s", code.toString()).build()).runTest();
  }

  @Test
  public void testEquals_nullable() {
    SourceBuilder code = SourceStringBuilder.compilable(features);
    int numValues = valueSet.values.size();
    for (int i = 0; i < numValues; ++i) {
      code.addLine("%s value%s = %s;", valueSet.nullableType, i, valueSet.values.get(i));
    }
    code.addLine("%s value%s = null;", valueSet.nullableType, numValues);
    for (int i = 0; i < numValues + 1; ++i) {
      for (int j = 0; j < numValues + 1; ++j) {
        code.addLine("assert%s(value%s + \" equals \" + value%s, %s);",
                (i == j) ? "True" : "False",
                i,
                j,
                ObjectsExcerpts.equals(
                    "value" + i, "value" + j, valueSet.kind, Nullability.NULLABLE));
      }
    }
    behaviorTester.with(new TestBuilder().addLine("%s", code.toString()).build()).runTest();
  }

  @Test
  public void testNotEquals_notNullable() {
    SourceBuilder code = SourceStringBuilder.compilable(features);
    int numValues = valueSet.values.size();
    for (int i = 0; i < numValues; ++i) {
      code.addLine("%s value%s = %s;", valueSet.notNullableType, i, valueSet.values.get(i));
    }
    for (int i = 0; i < numValues; ++i) {
      for (int j = 0; j < numValues; ++j) {
        code.addLine("assert%s(value%s + \" equals \" + value%s, %s);",
                (i != j) ? "True" : "False",
                i,
                j,
                ObjectsExcerpts.notEquals(
                    "value" + i, "value" + j, valueSet.kind, Nullability.NOT_NULLABLE));
      }
    }
    behaviorTester.with(new TestBuilder().addLine("%s", code.toString()).build()).runTest();
  }

  @Test
  public void testNotEquals_nullable() {
    SourceBuilder code = SourceStringBuilder.compilable(features);
    int numValues = valueSet.values.size();
    for (int i = 0; i < numValues; ++i) {
      code.addLine("%s value%s = %s;", valueSet.nullableType, i, valueSet.values.get(i));
    }
    code.addLine("%s value%s = null;", valueSet.nullableType, numValues);
    for (int i = 0; i < numValues + 1; ++i) {
      for (int j = 0; j < numValues + 1; ++j) {
        code.addLine("assert%s(value%s + \" equals \" + value%s, %s);",
                (i != j) ? "True" : "False",
                i,
                j,
                ObjectsExcerpts.notEquals(
                    "value" + i, "value" + j, valueSet.kind, Nullability.NULLABLE));
      }
    }
    behaviorTester.with(new TestBuilder().addLine("%s", code.toString()).build()).runTest();
  }
}
