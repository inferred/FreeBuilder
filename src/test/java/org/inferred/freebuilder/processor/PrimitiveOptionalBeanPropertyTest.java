package org.inferred.freebuilder.processor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.common.testing.EqualsTester;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester;
import org.inferred.freebuilder.processor.util.testing.ParameterizedBehaviorTestFactory;
import org.inferred.freebuilder.processor.util.testing.SourceBuilder;
import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.tools.JavaFileObject;
import java.util.List;
import java.util.OptionalInt;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(ParameterizedBehaviorTestFactory.class)
public abstract class PrimitiveOptionalBeanPropertyTest {

  @Parameterized.Parameters(name = "{0}")
  public static List<FeatureSet> featureSets() {
    return FeatureSets.ALL;
  }

  private static final JavaFileObject twoOptionalPropertiesType(Class<?> type) {
    return new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s getItem1();", type)
      .addLine("  public abstract %s getItem2();", type)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();
  }

  private static final JavaFileObject optionalPropertyType(Class<?> type) {
    return new SourceBuilder()
      .addLine("package com.example;")
      .addLine("@%s", FreeBuilder.class)
      .addLine("public abstract class DataType {")
      .addLine("  public abstract %s getItem();", type)
      .addLine("")
      .addLine("  public static class Builder extends DataType_Builder {}")
      .addLine("  public static Builder builder() {")
      .addLine("    return new Builder();")
      .addLine("  }")
      .addLine("}")
      .build();
  }

  @Parameterized.Parameter
  public FeatureSet features;
  private final Class<?> type = type();
  private final Class<? extends Number> wrapper = wrapper();
  private final String primitive = primitive();

  protected abstract Class<?> type();

  protected abstract String primitive();

  protected abstract Class<? extends Number> wrapper();

  protected abstract Number num(Integer value);

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  @ParameterizedBehaviorTestFactory.Shared
  public BehaviorTester behaviorTester;

  @Test
  public void testConstructor_defaultEmpty() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testConstructor_primitive_defaultEmpty() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder().build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testBuilderGetter_defaultValue() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder();")
              .addLine("assertEquals(%s.empty(), builder.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testBuilderGetter_nonDefaultValue() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder builder = new com.example.DataType.Builder()")
              .addLine("    .setItem(%s);", num(-21))
              .addLine("assertEquals(%s.of(%s), builder.getItem());", type, num(-21))
              .build())
      .runTest();
  }

  @Test
  public void testSet_notNull() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(%s)", num(15))
              .addLine("    .build();")
              .addLine("assertEquals(%s.of(%s), value.getItem());", type, num(15))
              .build())
      .runTest();
  }

  @Test
  public void testSet_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("new com.example.DataType.Builder().setItem((%s) null);",
                       type)
              .build())
      .runTest();
  }

  @Test
  public void testSet_optionalOf() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(%s.of(%s))", type, num(42))
              .addLine("    .build();")
              .addLine("assertEquals(%s.of(%s), value.getItem());", type, num(42))
              .build())
      .runTest();
  }

  @Test
  public void testSet_empty() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(%s.empty())", type)
              .addLine("    .build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testSet_nullOptional() {
    thrown.expect(NullPointerException.class);
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("new com.example.DataType.Builder().setItem((%s) null);",
                       type)
              .build())
      .runTest();
  }

  @Test
  public void testClear() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(0)")
              .addLine("    .clearItem()")
              .addLine("    .build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testSet_primitive_notNull() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(%s)", num(5))
              .addLine("    .build();")
              .addLine("assertEquals(%s.of(%s), value.getItem());", type, num(5))
              .build())
      .runTest();
  }

  @Test
  public void testSet_primitive_null() {
    thrown.expect(NullPointerException.class);
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("new com.example.DataType.Builder().setItem((%s) null);", wrapper)
              .build())
      .runTest();
  }

  @Test
  public void testSet_primitive_optionalOf() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(%s.of(5))", type)
              .addLine("    .build();")
              .addLine("assertEquals(%s.of(5), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testSet_primitive_empty() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(%s.empty())", type)
              .addLine("    .build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testSet_primitive_nullOptional() {
    thrown.expect(NullPointerException.class);
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("new com.example.DataType.Builder().setItem((%s) null);",
                       type)
              .build())
      .runTest();
  }

  @Test
  public void testClear_primitive() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(5)")
              .addLine("    .clearItem()")
              .addLine("    .build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = com.example.DataType.builder()")
              .addLine("    .setItem(0)")
              .addLine("    .build();")
              .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
              .addLine("    .mergeFrom(value);")
              .addLine("assertEquals(%s.of(0), builder.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testMergeFrom_builder() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
              .addLine("    .setItem(10);")
              .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
              .addLine("    .mergeFrom(template);")
              .addLine("assertEquals(%s.of(10), builder.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testMergeFrom_valueInstance_emptyOptional() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = com.example.DataType.builder()")
              .addLine("    .build();")
              .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
              .addLine("    .setItem(999)")
              .addLine("    .mergeFrom(value);")
              .addLine("assertEquals(%s.of(999), builder.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testMergeFrom_builder_emptyOptional() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder template = com.example.DataType.builder();")
              .addLine("com.example.DataType.Builder builder = com.example.DataType.builder()")
              .addLine("    .setItem(42)")
              .addLine("    .mergeFrom(template);")
              .addLine("assertEquals(%s.of(42), builder.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testBuilderClear() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder()")
              .addLine("    .setItem(42)")
              .addLine("    .clear()")
              .addLine("    .build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testBuilderClear_customDefault() {
    behaviorTester
      .with(new Processor(features))
      .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s getItem();", type)
              .addLine("")
              .addLine("  public static class Builder extends DataType_Builder {}")
              .addLine("  public static Builder builder() {")
              .addLine("    return new Builder().setItem(-90);")
              .addLine("  }")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addLine("com.example.DataType value = com.example.DataType.builder()")
              .addLine("    .setItem(42)")
              .addLine("    .clear()")
              .addLine("    .build();")
              .addLine("assertEquals(%s.of(-90), value.getItem());", type)
              .build())
      .runTest();
  }

  @Test
  public void testBuilderClear_noBuilderFactory() {
    behaviorTester
      .with(new Processor(features))
      .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s getItem();", type)
              .addLine("")
              .addLine("  public static class Builder extends DataType_Builder {")
              .addLine("    public Builder(%s i) {", primitive)
              .addLine("      setItem(i);")
              .addLine("    }")
              .addLine("  }")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addLine("com.example.DataType value = new com.example.DataType.Builder(42)")
              .addLine("    .clear()")
              .addLine("    .build();")
              .addLine("assertEquals(%s.empty(), value.getItem());", type)
              .build())
      .runTest();

  }

  @Test
  public void testCustomization_optionalOf() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too long");
    behaviorTester
      .with(new Processor(features))
      .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s getItem();", type)
              .addLine("")
              .addLine("  public static class Builder extends DataType_Builder {")
              .addLine("    @Override public Builder setItem(%s item) {", primitive)
              .addLine("      if (item > 10) {")
              .addLine("        throw new IllegalArgumentException(\"Item too long\");")
              .addLine("      }")
              .addLine("      return super.setItem(item);")
              .addLine("    }")
              .addLine("  }")
              .addLine("")
              .addLine("  public static Builder builder() {")
              .addLine("    return new Builder();")
              .addLine("  }")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
              .addLine("    .setItem(%s.of(11));", type)
              .build())
      .runTest();
  }

  @Test
  public void testCustomization_empty() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
      .with(new Processor(features))
      .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s getItem();", type)
              .addLine("")
              .addLine("  public static class Builder extends DataType_Builder {")
              .addLine("    @Override public Builder clearItem() {")
              .addLine("      throw new UnsupportedOperationException(\"Fooled you!\");")
              .addLine("    }")
              .addLine("  }")
              .addLine("")
              .addLine("  public static Builder builder() {")
              .addLine("    return new Builder();")
              .addLine("  }")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
              .addLine("    .setItem(%s.empty());", type)
              .build())
      .runTest();
  }

  @Test
  public void testCustomization_null() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
      .with(new Processor(features))
      .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s getItem();", type)
              .addLine("")
              .addLine("  public static class Builder extends DataType_Builder {")
              .addLine("    @Override public Builder clearItem() {")
              .addLine("      throw new UnsupportedOperationException(\"Fooled you!\");")
              .addLine("    }")
              .addLine("  }")
              .addLine("")
              .addLine("  public static Builder builder() {")
              .addLine("    return new Builder();")
              .addLine("  }")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
              .addLine("    .clearItem();", type)
              .build())
      .runTest();
  }

  @Test
  public void testCustomization_primitive_optionalOf() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("Item too big");
    behaviorTester
      .with(new Processor(features))
      .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s getItem();", type)
              .addLine("")
              .addLine("  public static class Builder extends DataType_Builder {")
              .addLine("    @Override public Builder setItem(%s item) {", primitive)
              .addLine("      if (item > 10) {")
              .addLine("        throw new IllegalArgumentException(\"Item too big\");")
              .addLine("      }")
              .addLine("      return super.setItem(item);")
              .addLine("    }")
              .addLine("  }")
              .addLine("")
              .addLine("  public static Builder builder() {")
              .addLine("    return new Builder();")
              .addLine("  }")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
              .addLine("    .setItem(%s.of(13));", type)
              .build())
      .runTest();
  }

  @Test
  public void testCustomization_primitive_empty() {
    thrown.expectMessage("Fooled you!");
    behaviorTester
      .with(new Processor(features))
      .with(new SourceBuilder()
              .addLine("package com.example;")
              .addLine("@%s", FreeBuilder.class)
              .addLine("public abstract class DataType {")
              .addLine("  public abstract %s getItem();", type)
              .addLine("")
              .addLine("  public static class Builder extends DataType_Builder {")
              .addLine("    @Override public Builder clearItem() {")
              .addLine("      throw new UnsupportedOperationException(\"Fooled you!\");")
              .addLine("    }")
              .addLine("  }")
              .addLine("")
              .addLine("  public static Builder builder() {")
              .addLine("    return new Builder();")
              .addLine("  }")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addLine("com.example.DataType.Builder template = com.example.DataType.builder()")
              .addLine("    .setItem(%s.empty());", type)
              .build())
      .runTest();
  }

  @Test
  public void testEquality() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("new %s()", EqualsTester.class)
              .addLine("    .addEqualityGroup(")
              .addLine("        com.example.DataType.builder().build(),")
              .addLine("        com.example.DataType.builder()")
              .addLine("            .setItem(%s.empty())", type)
              .addLine("            .build())")
              .addLine("    .addEqualityGroup(")
              .addLine("        com.example.DataType.builder()")
              .addLine("            .setItem(42)")
              .addLine("            .build(),")
              .addLine("        com.example.DataType.builder()")
              .addLine("            .setItem(%s.of(42))", type)
              .addLine("            .build())")
              .addLine("    .testEquals();")
              .build())
      .runTest();
  }

  @Test
  public void testValueToString_singleField() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType empty = com.example.DataType.builder()")
              .addLine("    .build();")
              .addLine("com.example.DataType present = com.example.DataType.builder()")
              .addLine("    .setItem(%s)", num(42))
              .addLine("    .build();")
              .addLine("assertEquals(\"DataType{}\", empty.toString());")
              .addLine("assertEquals(\"DataType{item=%s}\", present.toString());", num(42))
              .build())
      .runTest();
  }

  @Test
  public void testValueToString_twoFields() {
    behaviorTester
      .with(new Processor(features))
      .with(twoOptionalPropertiesType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType aa = com.example.DataType.builder()")
              .addLine("    .build();")
              .addLine("com.example.DataType pa = com.example.DataType.builder()")
              .addLine("    .setItem1(%s)", num(3))
              .addLine("    .build();")
              .addLine("com.example.DataType ap = com.example.DataType.builder()")
              .addLine("    .setItem2(5)")
              .addLine("    .build();")
              .addLine("com.example.DataType pp = com.example.DataType.builder()")
              .addLine("    .setItem1(3)", num(3))
              .addLine("    .setItem2(5)")
              .addLine("    .build();")
              .addLine("assertEquals(\"DataType{}\", aa.toString());")
              .addLine("assertEquals(\"DataType{item1=%s}\", pa.toString());", num(3))
              .addLine("assertEquals(\"DataType{item2=%s}\", ap.toString());", num(5))
              .addLine("assertEquals(\"DataType{item1=%s, item2=%s}\", pp.toString());", num(3),
                       num(5))
              .build())
      .runTest();
  }

  @Test
  public void testPartialToString_singleField() {
    behaviorTester
      .with(new Processor(features))
      .with(optionalPropertyType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType empty = com.example.DataType.builder()")
              .addLine("    .buildPartial();")
              .addLine("com.example.DataType present = com.example.DataType.builder()")
              .addLine("    .setItem(%s)", num(234))
              .addLine("    .buildPartial();")
              .addLine("assertEquals(\"partial DataType{}\", empty.toString());")
              .addLine("assertEquals(\"partial DataType{item=%s}\", present.toString());", num(234))
              .build())
      .runTest();
  }

  @Test
  public void testPartialToString_twoFields() {
    behaviorTester
      .with(new Processor(features))
      .with(twoOptionalPropertiesType(type))
      .with(new TestBuilder()
              .addLine("com.example.DataType aa = com.example.DataType.builder()")
              .addLine("    .buildPartial();")
              .addLine("com.example.DataType pa = com.example.DataType.builder()")
              .addLine("    .setItem1(%s)", num(5))
              .addLine("    .buildPartial();")
              .addLine("com.example.DataType ap = com.example.DataType.builder()")
              .addLine("    .setItem2(%s)", num(27))
              .addLine("    .buildPartial();")
              .addLine("com.example.DataType pp = com.example.DataType.builder()")
              .addLine("    .setItem1(%s)", num(5))
              .addLine("    .setItem2(%s)", num(27))
              .addLine("    .buildPartial();")
              .addLine("assertEquals(\"partial DataType{}\", aa.toString());")
              .addLine("assertEquals(\"partial DataType{item1=%s}\", pa.toString());", num(5))
              .addLine("assertEquals(\"partial DataType{item2=%s}\", ap.toString());", num(27))
              .addLine("assertEquals(\"partial DataType{item1=%s, item2=%s}\", pp.toString());",
                       num(5), num(27))
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
              .addLine("import " + JsonProperty.class.getName() + ";")
              .addLine("@%s", FreeBuilder.class)
              .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
              .addLine("public interface DataType {")
              .addLine("  @JsonProperty(\"stuff\") %s getItem();", type)
              .addLine("")
              .addLine("  class Builder extends DataType_Builder {}")
              .addLine("}")
              .build())
      .with(new TestBuilder()
              .addImport("com.example.DataType")
              .addLine("DataType value = new DataType.Builder()")
              .addLine("    .setItem(42)")
              .addLine("    .build();")
              .addLine("%1$s mapper = new %1$s()", ObjectMapper.class)
              .addLine("    .registerModule(new %s());", Jdk8Module.class)
              .addLine("String json = mapper.writeValueAsString(value);")
              .addLine("DataType clone = mapper.readValue(json, DataType.class);")
              .addLine("assertEquals(%s.of(42), clone.getItem());", type)
              .build())
      .runTest();
  }
}
