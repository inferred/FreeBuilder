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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.annotations.GwtCompatible;
import com.google.common.testing.EqualsTester;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.RPC;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Nullable;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ProcessorTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testAbstractClass() {
    behaviorTester
        .with(new Processor(features))
        .with(twoPropertyType())
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testInterface() {
    behaviorTester
        .with(new Processor(features))
        .with(twoPropertyInterface())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testPrefixlessInterface() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  int propertyA();")
            .addLine("  boolean propertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .propertyA(11)")
            .addLine("    .propertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.propertyA());")
            .addLine("assertTrue(value.propertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testGenericInterfaceWithBound() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType<N extends Number> {")
            .addLine("  N getProperty();")
            .addLine("  class Builder<N extends Number> extends DataType_Builder<N> { }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType<Integer> value =")
            .addLine("    new DataType.Builder<Integer>()")
            .addLine("        .setProperty(11)")
            .addLine("        .build();")
            .addLine("assertEquals(11, (int) value.getProperty());")
            .build())
        .runTest();
  }

  @Test
  public void test_nullPointerException() {
    behaviorTester
        .with(new Processor(features))
        .with(stringPropertyType())
        .with(testBuilder()
            .addLine("try {")
            .addLine("  DataType.builder().setName(null);")
            .addLine("  fail(\"Expected NPE\");")
            .addLine("} catch (NullPointerException expected) { }")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderSerializability_nonSerializableSubclass() {
    behaviorTester
        .with(new Processor(features))
        .with(twoPropertyType())
        .with(testBuilder()
            .addLine("assertFalse(DataType.builder() instanceof %s);",
                Serializable.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderSerializability_serializableSubclass() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder implements %s {}",
                Serializable.class)
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType.Builder builder = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true);")
            .addLine("DataType.Builder copy =")
            .addLine("    %s.reserialize(builder);", ProcessorTest.class)
            .addLine("DataType value = copy.build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testFrom() {
    behaviorTester
        .with(new Processor(features))
        .with(twoPropertyType())
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("DataType.Builder builder =")
            .addLine("    DataType.Builder.from(value);")
            .addLine("assertEquals(11, builder.getPropertyA());")
            .addLine("assertTrue(builder.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testClear_implicitConstructor() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyA, propertyB]");
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .build())
        .runTest();
  }

  @Test
  public void testClear_explicitNoArgsConstructor() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyA, propertyB]");
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder() {}")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .build())
        .runTest();
  }

  @Test
  public void testClear_builderMethod() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyA, propertyB]");
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    private Builder() {}")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType.builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .build())
        .runTest();
  }

  @Test
  public void testClear_newBuilderMethod() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Not set: [propertyA, propertyB]");
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    private Builder() {}")
            .addLine("  }")
            .addLine("  public static Builder newBuilder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType.newBuilder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .build())
        .runTest();
  }

  @Test
  public void testClear_noBuilderFactory() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder(int a, boolean b) {")
            .addLine("      setPropertyA(a);")
            .addLine("      setPropertyB(b);")
            .addLine("    }")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder(11, true)")
            .addLine("    .clear()")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testPropertyNamedTemplate() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract String getTemplate();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .runTest();
  }

  @Test
  public void testBuilderGetters() {
    behaviorTester
        .with(new Processor(features))
        .with(twoPropertyType())
        .with(testBuilder()
            .addLine("DataType.Builder builder = DataType.builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true);")
            .addLine("assertEquals(11, builder.getPropertyA());")
            .addLine("assertTrue(builder.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(twoPropertyType())
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(false)")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(false)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(12)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(12)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .buildPartial(),")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .buildPartial(),")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyB(true)")
            .addLine("            .buildPartial(),")
            .addLine("        DataType.builder()")
            .addLine("            .setPropertyB(true)")
            .addLine("            .buildPartial())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testDoubleEquality() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract double getValue();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .build(),")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(0.0)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(-0.0)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .buildPartial(),")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(0.0)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        DataType.builder()")
            .addLine("            .setValue(-0.0)")
            .addLine("            .buildPartial())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testToString_noProperties() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = DataType.builder().build();")
            .addLine("assertEquals(\"DataType{}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToString_oneProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(stringPropertyType())
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setName(\"fred\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{name=fred}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToString_twoPrimitiveProperties() {
    behaviorTester
        .with(new Processor(features))
        .with(twoPropertyType())
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{propertyA=11, propertyB=true}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToString_nullablePropertyNamedResult() {
    // See https://github.com/google/FreeBuilder/issues/261
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract @%s String getValue();", Nullable.class)
            .addLine("  public abstract @%s String getResult();", Nullable.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setResult(\"fred\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{result=fred}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testPartialToString_propertyNamedResult() {
    // See https://github.com/google/FreeBuilder/issues/261
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract String getValue();")
            .addLine("  public abstract String getResult();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setResult(\"fred\")")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"partial DataType{result=fred}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testGwtSerialize_twoStringProperties() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(serializable = true)", GwtCompatible.class)
            .addLine("public interface DataType {")
            .addLine("  String getPropertyA();")
            .addLine("  String getPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(\"foo\")")
            .addLine("    .setPropertyB(\"bar\")")
            .addLine("    .build();")
            .addLine("%s.gwtSerialize(value);", this.getClass())
            .build())
        .withContextClassLoader()  // Used by GWT to find the custom field serializer.
        .runTest();
  }

  @Test
  public void testGwtSerialize_twoPrimitiveProperties() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(serializable = true)", GwtCompatible.class)
            .addLine("public interface DataType {")
            .addLine("  int getPropertyA();")
            .addLine("  float getPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(5)")
            .addLine("    .setPropertyB(3.142F)")
            .addLine("    .build();")
            .addLine("%s.gwtSerialize(value);", this.getClass())
            .build())
        .withContextClassLoader()  // Used by GWT to find the custom field serializer.
        .runTest();
  }

  @Test
  public void testGwtSerialize_stringListProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(serializable = true)", GwtCompatible.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s> getNames();", List.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .addNames(\"foo\")")
            .addLine("    .addNames(\"bar\")")
            .addLine("    .build();")
            .addLine("%s.gwtSerialize(value);", this.getClass())
            .build())
        .withContextClassLoader()  // Used by GWT to find the custom field serializer.
        .runTest();
  }

  /**
   * Server-side deserialize does not match server-side serialize, so we can't test a round trip.
   */
  public static <T> void gwtSerialize(T object) throws SerializationException {
    RPC.encodeResponseForSuccess(arbitraryVoidReturningMethod(), object);
  }

  private static Method arbitraryVoidReturningMethod() {
    try {
      return ProcessorTest.class.getMethod("gwtSerialize", Object.class);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  public static <T> T reserialize(T object) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try {
      ObjectOutputStream out = new ObjectOutputStream(bytes);
      out.writeObject(object);
      ObjectInputStream in = new ObjectInputStream(
          new ByteArrayInputStream(bytes.toByteArray())) {
        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws ClassNotFoundException {
          return Class.forName(desc.getName(), false, object.getClass().getClassLoader());
        }
      };
      @SuppressWarnings("unchecked")
      T result = (T) in.readObject();
      return result;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testUnderriding_hashCodeAndEquals() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class Person {")
            .addLine("  public abstract String getName();")
            .addLine("  public abstract int getAge();")
            .addLine("")
            .addLine("  @Override public boolean equals(Object other) {")
            .addLine("    return (other instanceof Person)")
            .addLine("        && getName().equals(((Person) other).getName());")
            .addLine("  }")
            .addLine("  @Override public int hashCode() {")
            .addLine("    return getName().hashCode();")
            .addLine("  }")
            .addLine("")
            .addLine("  public static class Builder extends Person_Builder {}")
            .addLine("}"))
        // If hashCode and equals are not final, they are overridden to respect Partial behavior.
        .with(testBuilder()
            .addImport(EqualsTester.class)
            .addImport("com.example.Person")
            .addLine("new EqualsTester()")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Bill\").setAge(10).build(),")
            .addLine("        new Person.Builder().setName(\"Bill\").setAge(18).build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Ted\").setAge(10).build(),")
            .addLine("        new Person.Builder().setName(\"Ted\").setAge(18).build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Bill\").setAge(10).buildPartial(),")
            .addLine("        new Person.Builder().setName(\"Bill\").setAge(10).buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Bill\").setAge(18).buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Bill\").buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Ted\").setAge(10).buildPartial(),")
            .addLine("        new Person.Builder().setName(\"Ted\").setAge(10).buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Ted\").setAge(18).buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setName(\"Ted\").buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setAge(10).buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        new Person.Builder().setAge(18).buildPartial())")
            .addLine("    .testEquals();")
            .build())
        .runTest();
  }

  @Test
  public void testUnderriding_toString() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class Person {")
            .addLine("  public abstract String getName();")
            .addLine("  public abstract int getAge();")
            .addLine("")
            .addLine("  @Override public String toString() {")
            .addLine("    return getName() + \" (age \" + getAge() + \")\";")
            .addLine("  }")
            .addLine("")
            .addLine("  public static class Builder extends Person_Builder {}")
            .addLine("}"))
        // If toString is not final, it is overridden in Partial.
        .with(testBuilder()
            .addLine("com.example.Person p1 = new com.example.Person.Builder()")
            .addLine("    .setName(\"Bill\")")
            .addLine("    .setAge(18)")
            .addLine("    .build();")
            .addLine("com.example.Person p2 = new com.example.Person.Builder()")
            .addLine("    .setName(\"Bill\")")
            .addLine("    .buildPartial();")
            .addLine("assertEquals(\"Bill (age 18)\", p1.toString());")
            .addLine("assertEquals(\"partial Person{name=Bill}\", p2.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testUnderriding_finalHashCodeEqualsAndToString() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class Person {")
            .addLine("  public abstract String getName();")
            .addLine("  public abstract int getAge();")
            .addLine("")
            .addLine("  @Override public final boolean equals(Object other) {")
            .addLine("    return (other instanceof Person)")
            .addLine("        && getName().equals(((Person) other).getName());")
            .addLine("  }")
            .addLine("  @Override public final int hashCode() {")
            .addLine("    return getName().hashCode();")
            .addLine("  }")
            .addLine("  @Override public final String toString() {")
            .addLine("    return getName() + \" (age \" + getAge() + \")\";")
            .addLine("  }")
            .addLine("")
            .addLine("  public static class Builder extends Person_Builder {}")
            .addLine("}"))
        .runTest();
  }

  @Test
  public void testUnderriding_finalHashCodeAndEquals() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class Person {")
            .addLine("  public abstract String getName();")
            .addLine("  public abstract int getAge();")
            .addLine("")
            .addLine("  @Override public final boolean equals(Object other) {")
            .addLine("    return (other instanceof Person)")
            .addLine("        && getName().equals(((Person) other).getName());")
            .addLine("  }")
            .addLine("  @Override public final int hashCode() {")
            .addLine("    return getName().hashCode();")
            .addLine("  }")
            .addLine("")
            .addLine("  public static class Builder extends Person_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("com.example.Person billAt18 = new com.example.Person.Builder()")
            .addLine("    .setName(\"Bill\")")
            .addLine("    .setAge(18)")
            .addLine("    .build();")
            .addLine("com.example.Person billAt26 = new com.example.Person.Builder()")
            .addLine("    .setName(\"Bill\")")
            .addLine("    .setAge(26)")
            .addLine("    .build();")
            .addLine("assertEquals(billAt18, billAt26);")
            .addLine("assertEquals(billAt18.hashCode(), billAt26.hashCode());")
            .addLine("assertEquals(\"Person{name=Bill, age=26}\", billAt26.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testUnderriding_finalToString() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class Person {")
            .addLine("  public abstract String getName();")
            .addLine("  public abstract int getAge();")
            .addLine("")
            .addLine("  @Override public final String toString() {")
            .addLine("    return getName() + \" (age \" + getAge() + \")\";")
            .addLine("  }")
            .addLine("")
            .addLine("  public static class Builder extends Person_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("com.example.Person p1 = new com.example.Person.Builder()")
            .addLine("    .setName(\"Bill\")")
            .addLine("    .setAge(18)")
            .addLine("    .build();")
            .addLine("com.example.Person p2 = new com.example.Person.Builder()")
            .addLine("    .setName(\"Bill\")")
            .addLine("    .setAge(18)")
            .addLine("    .build();")
            .addLine("assertEquals(p1, p2);")
            .addLine("assertEquals(p1.hashCode(), p2.hashCode());")
            .addLine("assertEquals(\"Bill (age 18)\", p1.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  String getName();")
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setName(\"fred\")")
            .addLine("    .build();")
            .addLine("DataType.Builder copyBuilder = value.toBuilder();")
            .addLine("copyBuilder.setName(copyBuilder.getName() + \" 2\");")
            .addLine("DataType copy = copyBuilder.build();")
            .addLine("assertEquals(\"DataType{name=fred 2}\", copy.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  String getName();")
            .addLine("  int getAge();")
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setName(\"fred\")")
            .addLine("    .buildPartial();")
            .addLine("DataType.Builder copyBuilder = value.toBuilder();")
            .addLine("copyBuilder.setName(copyBuilder.getName() + \" 2\");")
            .addLine("DataType copy = copyBuilder.build();")
            .addLine("assertEquals(\"partial DataType{name=fred 2}\", copy.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial_withGenerics() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType<T> {")
            .addLine("  T getName();")
            .addLine("  int getAge();")
            .addLine("")
            .addLine("  Builder<T> toBuilder();")
            .addLine("  class Builder<T> extends DataType_Builder<T> {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType<String> value = new DataType.Builder<String>()")
            .addLine("    .setName(\"fred\")")
            .addLine("    .buildPartial();")
            .addLine("DataType.Builder<String> copyBuilder = value.toBuilder();")
            .addLine("copyBuilder.setName(copyBuilder.getName() + \" 2\");")
            .addLine("DataType<String> copy = copyBuilder.build();")
            .addLine("assertEquals(\"partial DataType{name=fred 2}\", copy.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial_withProtectedConstructorAndStaticBuilderMethod() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract String getName();")
            .addLine("  public abstract int getAge();")
            .addLine("")
            .addLine("  public static Builder builder() { return new Builder(); }")
            .addLine("  public abstract Builder toBuilder();")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    Builder() {}")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = DataType.builder()")
            .addLine("    .setName(\"fred\")")
            .addLine("    .buildPartial();")
            .addLine("DataType.Builder copyBuilder = value.toBuilder();")
            .addLine("copyBuilder.setName(copyBuilder.getName() + \" 2\");")
            .addLine("DataType copy = copyBuilder.build();")
            .addLine("assertEquals(\"partial DataType{name=fred 2}\", copy.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_fromPartial_withProtectedConstructorAndParameterizedBuilderMethod() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract String getName();")
            .addLine("  public abstract int getAge();")
            .addLine("")
            .addLine("  public static Builder builder(String name) {")
            .addLine("    return new Builder().setName(name);")
            .addLine("  }")
            .addLine("  public abstract Builder toBuilder();")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    Builder() {}")
            .addLine("  }")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = DataType.builder(\"fred\")")
            .addLine("    .buildPartial();")
            .addLine("DataType.Builder copyBuilder = value.toBuilder();")
            .addLine("copyBuilder.setName(copyBuilder.getName() + \" 2\");")
            .addLine("DataType copy = copyBuilder.build();")
            .addLine("assertEquals(\"partial DataType{name=fred 2}\", copy.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToBuilder_withPropertyCalledBuilder() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  String builder();")
            .addLine("")
            .addLine("  Builder toBuilder();")
            .addLine("  class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .builder(\"Bob\")")
            .addLine("    .build();")
            .addLine("DataType.Builder copyBuilder = value.toBuilder();")
            .addLine("copyBuilder.builder(copyBuilder.builder() + \" 2\");")
            .addLine("DataType copy = copyBuilder.build();")
            .addLine("assertEquals(\"DataType{builder=Bob 2}\", copy.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testSiblingNameClashes() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("/** Block import of java.lang.String. #evil */")
            .addLine("public interface String {}"))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  java.lang.String getProperty();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setProperty(\"hello\")")
            .addLine("    .build();")
            .addLine("assertEquals(\"hello\", value.getProperty());")
            .build())
        .runTest();
  }

  @Test
  public void testNestedNameClashes() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("/** Clashes with the inner type generated by FreeBuilder. */")
            .addLine("public class Value {}"))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  Value getProperty();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("com.example.Value property = new com.example.Value();")
            .addLine("DataType dataType = new DataType.Builder()")
            .addLine("    .setProperty(property)")
            .addLine("    .build();")
            .addLine("assertEquals(property, dataType.getProperty());")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderClassIsEmpty_whenNotSubclassed() {
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("}"))
        .with(testBuilder()
            .addLine("Class<?> builderClass = Class.forName(\"com.example.DataType_Builder\");")
            .addLine("assertThat(builderClass.getDeclaredMethods()).asList().isEmpty();")
            .build())
        .runTest();
  }

  @Test
  public void testNestedClassHidingType() {
    // See also https://github.com/google/FreeBuilder/issues/61
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static class Preconditions {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testJacksonInteroperability() {
    // See also https://github.com/google/FreeBuilder/issues/68
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();", JsonProperty.class)
            .addLine("  @%s(\"b\") public abstract boolean isPropertyB();", JsonProperty.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String json = mapper.writeValueAsString(value);")
            .addLine("DataType clone = mapper.readValue(json, DataType.class);")
            .addLine("assertEquals(11, clone.getPropertyA());")
            .addLine("assertTrue(clone.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testDoubleRegistration() {
    // See also https://github.com/google/FreeBuilder/issues/21
    behaviorTester
        .with(new Processor(features))
        .with(new Processor(features))
        .with(twoPropertyInterface())
        .with(testBuilder()
            .addLine("DataType value = new DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testNestedClassHidingPropertyClass() {
    // A contrived example of how horribly messed up scopes can make things.
    // In this, datatype B extends interface A, which defines a nested type X.
    // There is also a top-level type called X that B uses for one of its properties.
    // If we generate code without paying attention to inherited scopes, we may try to
    // import and refer to the top-level class X, not realising the nested A.X will hide it
    // in our value type implementations.
    behaviorTester
        .with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example.p;")
            .addLine("public interface A {")
            .addLine("  interface X { }")
            .addLine("}"))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example.q;")
            .addLine("public interface X { }"))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example.r;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface B extends com.example.p.A {")
            .addLine("  com.example.q.X bar();")
            .addLine("  class Builder extends B_Builder {}")
            .addLine("}"))
        .compiles()
        .withNoWarnings();
  }

  @Test
  public void testNameConflictWithJavaLangType() {
    // The implicit import of java.lang in every scope can be hidden by an explicit import of
    // a type with the same name. This sounds crazy but even com.sun is occasionally guilty of
    // reusing a tempting name like "Override" — and accidentally importing such a type is just
    // as chaotic as you might expect.
    behaviorTester.with(new Processor(features))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example.p;")
            .addLine("public interface Override { }"))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example.r;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface A {")
            .addLine("  com.example.p.Override override();")
            .addLine("  class Builder extends A_Builder {}")
            .addLine("}"))
        .compiles()
        .withNoWarnings();
  }

  private static CompilationUnitBuilder twoPropertyType() {
    return CompilationUnitBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract int getPropertyA();")
        .addLine("  public abstract boolean isPropertyB();")
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("  public static Builder builder() {")
        .addLine("    return new Builder();")
        .addLine("  }")
        .addLine("}");
  }

  private static CompilationUnitBuilder twoPropertyInterface() {
    return CompilationUnitBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public interface DataType {")
        .addLine("  int getPropertyA();")
        .addLine("  boolean isPropertyB();")
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("}");
  }

  private static CompilationUnitBuilder stringPropertyType() {
    return CompilationUnitBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("@%s", FreeBuilder.class)
        .addLine("public abstract class DataType {")
        .addLine("  public abstract String getName();")
        .addLine("")
        .addLine("  public static class Builder extends DataType_Builder {}")
        .addLine("  public static Builder builder() {")
        .addLine("    return new Builder();")
        .addLine("  }")
        .addLine("}");
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder()
        .addImport("com.example.DataType");
  }
}
