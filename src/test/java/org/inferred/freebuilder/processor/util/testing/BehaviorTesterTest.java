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
package org.inferred.freebuilder.processor.util.testing;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.rules.ExpectedException.none;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
import org.inferred.freebuilder.processor.util.feature.SourceLevel;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;
import org.inferred.freebuilder.processor.util.testing.TestBuilder.TestSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/** Unit tests for {@link BehaviorTester}. */
@RunWith(JUnit4.class)
public class BehaviorTesterTest {

  @Rule public final ExpectedException thrown = none();

  @Test
  public void simpleExample() {
    behaviorTester()
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@%s(%s.RUNTIME)", Retention.class, RetentionPolicy.class)
            .addLine("public @interface TestAnnotation { }"))
        .with(CompilationUnitBuilder.forTesting()
            .addLine("package com.example;")
            .addLine("@TestAnnotation public class MyClass {")
            .addLine("  public MyOtherClass get() {")
            .addLine("    return new MyOtherClass();")
            .addLine("  }")
            .addLine("}"))
        .with(
            new TestProcessor() {
              @Override public boolean process(
                  Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                if (!annotations.isEmpty()) {
                  try (Writer writer = processingEnv.getFiler()
                      .createSourceFile("com.example.MyOtherClass")
                      .openWriter()) {
                    writer.append("package com.example;\n"
                        + "public class MyOtherClass {\n"
                        + "  public String get() {\n"
                        + "    return \"Hello world!\";\n"
                        + "  }\n"
                        + "}\n");
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                }
                return true;
              }
            })
        .with(new TestBuilder()
            .addLine("assertEquals(\"Hello world!\", new com.example.MyClass().get().get());")
            .build())
        .runTest();
  }

  @Test
  public void failingTest_throwsOriginalRuntimeException() {
    thrown.expect(AssertionError.class);
    thrown.expectMessage("expected:<2> but was:<3>");
    behaviorTester()
        .with(new TestBuilder()
              .addLine("assertEquals(2, 3);")
              .build())
        .runTest();
  }

  public void failingCompilation_throwsAssertionError() {
    thrown.expect(CompilationException.class);
    behaviorTester()
        .with(new TestBuilder()
            .addLine("jooblefish")
            .build())
        .runTest();
  }

  @Test
  public void failingTest_includesHelpfulStackTrace() {
    int firstLine = new Exception().getStackTrace()[0].getLineNumber();
    try {
      behaviorTester()
          .with(new TestBuilder()
                .addLine("throw new RuntimeException(\"d'oh\");") // 4 lines after firstLine
                .build())
          .runTest();
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertEquals("d'oh", e.getMessage());
      StackTraceElement stackTraceElement = e.getStackTrace()[0];
      assertThat(stackTraceElement.getClassName()).contains(
          "org.inferred.freebuilder.processor.util.testing.generatedcode.BehaviorTesterTest");
      assertEquals("failingTest_includesHelpfulStackTrace", stackTraceElement.getMethodName());
      assertTrue(
          (stackTraceElement.getLineNumber() == firstLine + 4)  // ECJ gives great stack traces
              || (stackTraceElement.getLineNumber() == firstLine + 2));  // javac, not so great
    }
  }

  @Test
  public void sourceLevelAffectsCompilationErrors() {
    // Cannot currently write this test as there is only one supported Java source level
    assertThat(SourceLevel.values()).hasLength(1);
  }

  @Test
  public void guavaPackageAffectsAvailabilityOfGuavaInSource() {
    CompilationUnitBuilder source = CompilationUnitBuilder.forTesting()
        .addLine("package com.example;")
        .addLine("public class Test {")
        .addLine("  private final %s<Integer> aField = %s.of();", List.class, ImmutableList.class)
        .addLine("}");
    TestSource test = new TestBuilder()
        .addLine("new com.example.Test();")
        .build();
    behaviorTesterWith(GuavaLibrary.AVAILABLE).with(source).with(test).runTest();
    thrown.expect(NoClassDefFoundError.class);
    thrown.expectMessage("com/google/common/collect/ImmutableList");
    behaviorTesterWith(GuavaLibrary.UNAVAILABLE).with(source).with(test).runTest();
  }

  private static BehaviorTester behaviorTester() {
    return behaviorTesterWith();
  }

  private static BehaviorTester behaviorTesterWith(Feature<?>... features) {
    return BehaviorTester.create(new StaticFeatureSet(features));
  }

  private abstract static class TestProcessor extends AbstractProcessor {
    @Override public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("com.example.TestAnnotation");
    }

    @Override public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }
  }
}
