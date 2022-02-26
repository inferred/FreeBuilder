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
package org.inferred.freebuilder.processor.source.testing;

import java.util.function.Consumer;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.testing.TestBuilder.TestSource;
import org.junit.Test;

/**
 * Convenience class for performing behavioral tests of API-generating annotation processors.
 *
 * <h3>Behavior testing of generated APIs</h3>
 *
 * <p>Annotation processors that generate complex APIs from small template classes are difficult to
 * write good tests for. For an example, take a processor that generates a builder for a class.
 * Comparing generated source with a golden output, whether via exact line-by-line comparison, AST
 * comparison or bytecode comparison, leads to fragile tests. Adding a new method&mdash;say,
 * buildPartial()&mdash;or changing the way an unset field is represented internally&mdash;say, from
 * a null value to an explicit boolean field&mdash;will break every test, even though the
 * user-visible behavior is unaltered.
 *
 * <p>Additionally, as test code will not compile without the processor, compilation becomes part of
 * the test. Moving part of the test out of JUnit loses convenient integration with a number of
 * tools and ADEs.
 *
 * <p>Behavioral testing verifies the generated code by compiling and running a small Java test
 * against it. Well-written behavioral tests will check specific contracts the code generator must
 * honor&mdash;e.g. "the data object returned will contain the values set on the builder", or "an
 * exception will be thrown if a field has not been set"&mdash;contracts that will continue to hold
 * even as internal representations change, and new features are added.
 *
 * <p>BehaviorTester takes a set of annotation processors and Java classes, and runs the JVM's own
 * compiler against them. It also takes test code, wraps it in a static method, compiles and invokes
 * it. As we assume the classes and test code will not compile without the annotation processors, we
 * take them in as strings, which may be hard-coded in the test or read in from a resource file.
 * Here is an example for a hypothetical Builder generator:
 *
 * <blockquote>
 *
 * <code><pre>
 * {@link BehaviorTester#create(FeatureSet) create(features)}
 *     {@link #with(Processor) .with}(builderGeneratingProcessor)
 *     {@link #with(JavaFileObject) .with}(new {@link SourceBuilder}()
 *         .addLine("package com.example;")
 *         .addLine("{@literal @}%s public TestClass { ", MakeMeABuilder.class)
 *         .addLine("  private final %s&lt;%s&gt; strings;", List.class, String.class)
 *         .addLine("  TestClass(TestClassBuilder builder) {")
 *         .addLine("    strings = builder.getStrings();")
 *         .addLine("  }")
 *         .addLine("  public %s&lt;%s&gt; getStrings() {", List.class, String.class)
 *         .addLine("    return strings;")
 *         .addLine("  }")
 *         .addLine("}")
 *         .build())
 *     {@link #with(JavaFileObject) .with}(new {@link TestBuilder}()
 *         .addLine("com.example.TestClass instance = new com.example.TestClassBuilder()")
 *         .addLine("    .addString(\"Foo\")")
 *         .addLine("    .addString(\"Bar\")")
 *         .addLine("    .build();")
 *         .addLine("assertEquals(\"Foo\", instance.getStrings().get(0));")
 *         .addLine("assertEquals(\"Bar\", instance.getStrings().get(1));")
 *         .build())
 *     {@link #runTest()};
 * </pre></code>
 *
 * </blockquote>
 */
public interface BehaviorTester {

  static BehaviorTester create(FeatureSet features) {
    return new SingleBehaviorTester(features);
  }

  /** Adds a {@link Processor} to pass to the compiler when {@link #runTest} is invoked. */
  BehaviorTester with(Processor processor);

  /**
   * Adds a {@link JavaFileObject} to pass to the compiler when {@link #runTest} is invoked.
   *
   * @see SourceBuilder
   * @see TestBuilder
   */
  BehaviorTester with(JavaFileObject compilationUnit);

  default BehaviorTester with(SourceBuilder code) {
    return with(new CompilationUnit(code));
  }

  /** Adds a {@link TestSource} to compile and execute when {@link #runTest} is invoked. */
  BehaviorTester with(TestSource testSource);

  /**
   * Ensures {@link Thread#getContextClassLoader()} will return a class loader containing the
   * compiled sources. This is needed by some frameworks, e.g. GWT, but requires us to run tests on
   * a separate thread, which complicates exceptions and stack traces.
   */
  BehaviorTester withContextClassLoader();

  /** Whitelists a specific package for access by generated code. */
  BehaviorTester withPermittedPackage(Package pkg);

  /**
   * Compiles everything given to {@link #with}.
   *
   * @return a {@link CompilationSubject} with which to make further assertions
   */
  CompilationSubject compiles();

  /**
   * Errors when attempting to compile everything given to {@link #with}.
   *
   * @return a {@link CompilationFailureSubject} with which to make further assertions
   */
  CompilationFailureSubject failsToCompile();

  /**
   * Compiles, loads and tests everything given to {@link #with}.
   *
   * <p>Runs the compiler with the provided sources and processors. Loads the generated code into a
   * classloader. Finds all {@link Test @Test}-annotated methods (e.g. those built by {@link
   * TestBuilder}) and invokes them. Aggregates all exceptions, and propagates them to the caller.
   */
  default void runTest() {
    compiles().allTestsPass();
  }

  /** Assertions that can be made about a compilation run. */
  public interface CompilationSubject {

    /** Fails if the compiler issued warnings. */
    CompilationSubject withNoWarnings();

    /** Fails if the compiler did not issue a warning matching {@code diagnosticAssertions}. */
    CompilationSubject withWarningThat(Consumer<DiagnosticSubject> diagnosticAssertions);

    /**
     * Loads and tests all test sources.
     *
     * <p>Aggregates all exceptions, and propagates them to the caller.
     */
    CompilationSubject allTestsPass();

    /**
     * Loads and tests all {@code testSources}.
     *
     * <p>Aggregates all exceptions, and propagates them to the caller. All test sources must have
     * been passed to {@link BehaviorTester#with(TestSource)} prior to calling {@link
     * BehaviorTester#compiles()}.
     */
    CompilationSubject testsPass(
        Iterable<? extends TestSource> testSources, boolean shouldSetContextClassLoader);
  }

  /** Assertions that can be made about a failed compilation. */
  public interface CompilationFailureSubject {
    CompilationFailureSubject withErrorThat(Consumer<DiagnosticSubject> diagnosticAssertions);
  }

  public interface DiagnosticSubject {
    DiagnosticSubject hasMessage(CharSequence expected);

    DiagnosticSubject inFile(String expected);

    DiagnosticSubject onLine(long line);
  }
}
