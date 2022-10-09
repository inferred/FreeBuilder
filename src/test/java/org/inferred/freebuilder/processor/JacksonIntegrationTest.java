package org.inferred.freebuilder.processor;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.feature.StaticFeatureSet;
import org.inferred.freebuilder.processor.source.testing.BehaviorTester;
import org.inferred.freebuilder.processor.source.testing.ParameterizedBehaviorTestFactory.Shared;
import org.inferred.freebuilder.processor.source.testing.TestBuilder;
import org.junit.Test;

public class JacksonIntegrationTest {

  @Shared public BehaviorTester behaviorTester;

  @Test
  public void testJsonAliasSupport() {
    FeatureSet featureSet = new StaticFeatureSet();
    BehaviorTester.create(featureSet)
        .with(new Processor(featureSet))
        .with(SourceBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s", FreeBuilder.class)
            .addLine("@%s(builder = DataType.Builder.class)", JsonDeserialize.class)
            .addLine("public abstract class DataType {")
            .addLine("  @%s({\"a\", \"theagame\"})", JsonAlias.class)
            .addLine("  public abstract int propertyA();")
            .addLine("")
            .addLine("  public static class Builder extends DataType_Builder {}")
            .addLine("}"))
        .with(testBuilder()
            .addLine("DataType expected = new DataType.Builder().propertyA(13).build();")
            .addLine("%1$s mapper = new %1$s();", ObjectMapper.class)
            .addLine("String canonicalJson = \"{ \\\"propertyA\\\": 13 }\";")
            .addLine("DataType canonical = mapper.readValue(canonicalJson, DataType.class);")
            .addLine("assertEquals(expected, canonical);")
            .addLine("String alternative1Json = \"{ \\\"a\\\": 13 }\";")
            .addLine("DataType alternative1 = mapper.readValue(canonicalJson, DataType.class);")
            .addLine("assertEquals(expected, alternative1);")
            .addLine("String alternative2Json = \"{ \\\"theagame\\\": 13 }\";")
            .addLine("DataType alternative2 = mapper.readValue(canonicalJson, DataType.class);")
            .addLine("assertEquals(expected, alternative2);")
            .build())
        .runTest();
  }

  private static TestBuilder testBuilder() {
    return new TestBuilder().addImport("com.example.DataType");
  }
}
