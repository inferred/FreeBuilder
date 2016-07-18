package org.inferred.freebuilder.processor.util.testing;

import org.inferred.freebuilder.processor.util.testing.TestBuilder.TestSource;
import org.junit.Test;

import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;

/**
 * Convenience class for performing behavioral tests of API-generating
 * annotation processors.
 *
 * <h3>Behavior testing of generated APIs</h3>
 *
 * <p>Annotation processors that generate complex APIs from small template classes are
 * difficult to write good tests for. For an example, take a processor that generates
 * a builder for a class. Comparing generated source with a golden output, whether via
 * exact line-by-line comparison, AST comparison or bytecode comparison, leads to fragile
 * tests. Adding a new method&mdash;say, buildPartial()&mdash;or changing the way an
 * unset field is represented internally&mdash;say, from a null value to an explicit
 * boolean field&mdash;will break every test, even though the user-visible behavior is
 * unaltered.
 *
 * <p>Additionally, as test code will not compile without the processor, compilation
 * becomes part of the test. Moving part of the test out of JUnit loses convenient
 * integration with a number of tools and ADEs.
 *
 * <p>Behavioral testing verifies the generated code by compiling and running a small
 * Java test against it. Well-written behavioral tests will check specific contracts
 * the code generator must honor&mdash;e.g. "the data object returned will contain the
 * values set on the builder", or "an exception will be thrown if a field has not been
 * set"&mdash;contracts that will continue to hold even as internal representations
 * change, and new features are added.
 *
 * <p>BehaviorTester takes a set of annotation processors and Java classes, and runs
 * the JVM's own compiler against them. It also takes test code, wraps it in a static
 * method, compiles and invokes it. As we assume the classes and test code will not
 * compile without the annotation processors, we take them in as strings, which may
 * be hard-coded in the test or read in from a resource file. Here is an example for
 * a hypothetical Builder generator:
 *
 * <blockquote><code><pre>
 * {@link BehaviorTester#create()}
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
 * </pre></code></blockquote>
 */
public interface BehaviorTester {

  static SingleBehaviorTester create() {
    return new SingleBehaviorTester();
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

  /**
   * Adds a {@link TestSource} to compile and execute when {@link #runTest} is invoked.
   */
  BehaviorTester with(TestSource testSource);

  /**
   * Ensures {@link Thread#getContextClassLoader()} will return a class loader containing the
   * compiled sources. This is needed by some frameworks, e.g. GWT, but requires us to run tests
   * on a separate thread, which complicates exceptions and stack traces.
   */
  BehaviorTester withContextClassLoader();

  /**
   * Compiles everything given to {@link #with}.
   *
   * @return a {@link CompilationSubject} with which to make further assertions
   */
  CompilationSubject compiles();

  /**
   * Compiles, loads and tests everything given to {@link #with}.
   *
   * <p>Runs the compiler with the provided sources and processors. Loads the generated code into a
   * classloader. Finds all {@link Test @Test}-annotated methods (e.g. those built by {@link
   * TestBuilder}) and invokes them. Aggregates all exceptions, and propagates them to the caller.
   */
  void runTest();

  /**
   * Assertions that can be made about a compilation run.
   */
  public interface CompilationSubject {

    /**
     * Fails if the compiler issued warnings.
     */
    CompilationSubject withNoWarnings();

  }

}