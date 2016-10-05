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

import com.google.common.annotations.GwtCompatible;
import com.google.common.testing.EqualsTester;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.RPC;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;

import javax.tools.JavaFileObject;

@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public class ProcessorTest {

  @Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject NO_BUILDER_CLASS = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract int getPropertyA();")
      .addLine("  public abstract boolean isPropertyB();")
      .addLine("}")
      .build();
  private static final String PROPERTY_A_DESCRIPTION = "the value of property A.";
  private static final String PROPERTY_B_DESCRIPTION = "whether the object is property B.";
  private static final JavaFileObject TWO_PROPERTY_FREE_BUILDER_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  /** Returns %s */", PROPERTY_A_DESCRIPTION)
      .addLine("  public abstract int getPropertyA();")
      .addLine("  /** Returns %s */", PROPERTY_B_DESCRIPTION)
      .addLine("  public abstract boolean isPropertyB();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  private static final JavaFileObject TWO_PROPERTY_FREE_BUILDER_INTERFACE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public interface DataType {")
      .addLine("  int getPropertyA();")
      .addLine("  boolean isPropertyB();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("}")
      .build();

  private static final JavaFileObject STRING_PROPERTY_TYPE = new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract String getName();")
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();

  @Parameter public FeatureSet features;

  @Rule public final ExpectedException thrown = ExpectedException.none();
  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testAbstractClass() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_PROPERTY_FREE_BUILDER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
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
        .with(TWO_PROPERTY_FREE_BUILDER_INTERFACE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void test_nullPointerException() {
    behaviorTester
        .with(new Processor(features))
        .with(STRING_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("try {")
            .addLine("  com.example.DataType.builder().setName(null);")
            .addLine("  fail(\"Expected NPE\");")
            .addLine("} catch (NullPointerException expected) { }")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderSerializability_nonSerializableSubclass() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_PROPERTY_FREE_BUILDER_TYPE)
        .with(new TestBuilder()
            .addLine("assertFalse(com.example.DataType.builder() instanceof %s);",
                Serializable.class)
            .build())
        .runTest();
  }

  @Test
  public void testBuilderSerializability_serializableSubclass() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder implements %s {}",
                Serializable.class)
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true);")
            .addLine("com.example.DataType.Builder copy =")
            .addLine("    %s.reserialize(builder);", ProcessorTest.class)
            .addLine("com.example.DataType value = copy.build();")
            .addLine("assertEquals(11, value.getPropertyA());")
            .addLine("assertTrue(value.isPropertyB());")
            .build())
        .runTest();
  }

  @Test
  public void testFrom() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_PROPERTY_FREE_BUILDER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("com.example.DataType.Builder builder =")
            .addLine("    com.example.DataType.Builder.from(value);")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  /** Returns %s */", PROPERTY_A_DESCRIPTION)
            .addLine("  public abstract int getPropertyA();")
            .addLine("  /** Returns %s */", PROPERTY_B_DESCRIPTION)
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  /** Returns %s */", PROPERTY_A_DESCRIPTION)
            .addLine("  public abstract int getPropertyA();")
            .addLine("  /** Returns %s */", PROPERTY_B_DESCRIPTION)
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    public Builder() {}")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("new com.example.DataType.Builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  /** Returns %s */", PROPERTY_A_DESCRIPTION)
            .addLine("  public abstract int getPropertyA();")
            .addLine("  /** Returns %s */", PROPERTY_B_DESCRIPTION)
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    private Builder() {}")
            .addLine("  }")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  /** Returns %s */", PROPERTY_A_DESCRIPTION)
            .addLine("  public abstract int getPropertyA();")
            .addLine("  /** Returns %s */", PROPERTY_B_DESCRIPTION)
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {")
            .addLine("    private Builder() {}")
            .addLine("  }")
            .addLine("  public static Builder newBuilder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType.newBuilder()")
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
        .with(new SourceBuilder()
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
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder(11, true)")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract String getTemplate();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .runTest();
  }

  @Test
  public void testBuilderGetters() {
    behaviorTester
        .with(new Processor(features))
        .with(TWO_PROPERTY_FREE_BUILDER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
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
        .with(TWO_PROPERTY_FREE_BUILDER_TYPE)
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(false)")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(false)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(12)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(12)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .buildPartial(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .setPropertyB(true)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .buildPartial(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyA(11)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setPropertyB(true)")
            .addLine("            .buildPartial(),")
            .addLine("        com.example.DataType.builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract double getValue();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("new %s()", EqualsTester.class)
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .build(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setValue(0.0)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setValue(-0.0)")
            .addLine("            .build())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .buildPartial(),")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setValue(Double.NaN)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
            .addLine("            .setValue(0.0)")
            .addLine("            .buildPartial())")
            .addLine("    .addEqualityGroup(")
            .addLine("        com.example.DataType.builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static Builder builder() {")
            .addLine("    return new Builder();")
            .addLine("  }")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder().build();")
            .addLine("assertEquals(\"DataType{}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testToString_oneProperty() {
    behaviorTester
        .with(new Processor(features))
        .with(STRING_PROPERTY_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
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
        .with(TWO_PROPERTY_FREE_BUILDER_TYPE)
        .with(new TestBuilder()
            .addLine("com.example.DataType value = com.example.DataType.builder()")
            .addLine("    .setPropertyA(11)")
            .addLine("    .setPropertyB(true)")
            .addLine("    .build();")
            .addLine("assertEquals(\"DataType{propertyA=11, propertyB=true}\", value.toString());")
            .build())
        .runTest();
  }

  @Test
  public void testGwtSerialize_twoStringProperties() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(serializable = true)", GwtCompatible.class)
            .addLine("public interface DataType {")
            .addLine("  String getPropertyA();")
            .addLine("  String getPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(serializable = true)", GwtCompatible.class)
            .addLine("public interface DataType {")
            .addLine("  int getPropertyA();")
            .addLine("  float getPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(serializable = true)", GwtCompatible.class)
            .addLine("public interface DataType {")
            .addLine("  %s<%s> getNames();", List.class, String.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
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

  public static <T> T reserialize(final T object) {
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
        .with(new SourceBuilder()
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
            .addLine("}")
            .build())
        // If hashCode and equals are not final, they are overridden to respect Partial behavior.
        .with(new TestBuilder()
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
        .with(new SourceBuilder()
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
            .addLine("}")
            .build())
        // If toString is not final, it is overridden in Partial.
        .with(new TestBuilder()
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
        .with(new SourceBuilder()
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
            .addLine("}")
            .build())
        .runTest();
  }

  @Test
  public void testUnderriding_finalHashCodeAndEquals() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
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
            .addLine("}")
            .build())
        .with(new TestBuilder()
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
        .with(new SourceBuilder()
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
            .addLine("}")
            .build())
        .with(new TestBuilder()
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
  public void testSiblingNameClashes() {
    behaviorTester
        .with(new Processor(features))
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("/** Block import of java.lang.String. #evil */")
            .addLine("public interface String {}")
            .build())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  java.lang.String getProperty();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("/** Clashes with the inner type generated by FreeBuilder. */")
            .addLine("public class Value {}")
            .build())
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public interface DataType {")
            .addLine("  Value getProperty();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.Value property = new com.example.Value();")
            .addLine("com.example.DataType dataType = new com.example.DataType.Builder()")
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
        .with(NO_BUILDER_CLASS)
        .with(new TestBuilder()
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();")
            .addLine("  public abstract boolean isPropertyB();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("  public static class Preconditions {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addLine("com.example.DataType value = new com.example.DataType.Builder()")
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
        .with(new SourceBuilder()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public abstract class DataType {")
            .addLine("  public abstract int getPropertyA();", JsonProperty.class)
            .addLine("  @%s(\"b\") public abstract boolean isPropertyB();", JsonProperty.class)
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}")
            .build())
        .with(new TestBuilder()
            .addImport("com.example.DataType")
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

}
