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

import static com.google.common.util.concurrent.Uninterruptibles.joinUninterruptibly;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.Test;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

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
 * new {@link #BehaviorTester()}
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
public class BehaviorTester {
  private final List<Processor> processors = new ArrayList<Processor>();
  private final List<JavaFileObject> compilationUnits = new ArrayList<JavaFileObject>();
  private boolean shouldSetContextClassLoader = false;

  /** Adds a {@link Processor} to pass to the compiler when {@link #runTest} is invoked. */
  public BehaviorTester with(Processor processor) {
    processors.add(processor);
    return this;
  }

  /**
   * Adds a {@link JavaFileObject} to pass to the compiler when {@link #runTest} is invoked.
   *
   * @see SourceBuilder
   * @see TestBuilder
   */
  public BehaviorTester with(JavaFileObject compilationUnit) {
    compilationUnits.add(compilationUnit);
    return this;
  }

  /**
   * Ensures {@link Thread#getContextClassLoader()} will return a class loader containing the
   * compiled sources. This is needed by some frameworks, e.g. GWT, but requires us to run tests
   * on a separate thread, which complicates exceptions and stack traces.
   */
  public BehaviorTester withContextClassLoader() {
    shouldSetContextClassLoader = true;
    return this;
  }

  /**
   * Compiles, loads and tests everything given to {@link #with}.
   *
   * <p>Runs the compiler with the provided sources and processors. Loads the generated code into a
   * classloader. Finds all {@link Test @Test}-annotated methods (e.g. those built by {@link
   * TestBuilder}) and invokes them. Aggregates all exceptions, and propagates them to the caller.
   */
  public void runTest() {
    try (TempJavaFileManager fileManager = new TempJavaFileManager()) {
      compile(fileManager, compilationUnits, processors);
      final ClassLoader classLoader = fileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);
      final List<Throwable> exceptions = new ArrayList<Throwable>();
      if (shouldSetContextClassLoader) {
        Thread t = new Thread() {
          @Override
          public void run() {
            runTests(classLoader, exceptions);
          }
        };
        t.setContextClassLoader(classLoader);
        t.start();
        joinUninterruptibly(t);
      } else {
        runTests(classLoader, exceptions);
        if (exceptions.size() == 1) {
          // If there was a single error on the same thread, propagate it directly.
          // This makes testing for expected errors easier.
          Throwables.propagateIfPossible(exceptions.get(0));
        }
      }
      if (!exceptions.isEmpty()) {
        Throwable cause = exceptions.remove(0);
        RuntimeException aggregate = new RuntimeException("Behavioral test failed", cause);
        for (Throwable suppressed : exceptions) {
          aggregate.addSuppressed(suppressed);
        }
        throw aggregate;
      }
    }
  }

  private void runTests(final ClassLoader classLoader, final List<Throwable> throwables) {
    for (Class<?> compiledClass : loadCompiledClasses(classLoader, compilationUnits)) {
      for (Method testMethod : getTestMethods(compiledClass)) {
        try {
          testMethod.invoke(testMethod.getDeclaringClass().newInstance());
        } catch (InvocationTargetException e) {
          throwables.add(e.getCause());
        } catch (IllegalAccessException | InstantiationException e) {
          throwables.add(new AssertionError("Unexpected failure", e));
        }
      }
    }
  }

  private static List<Class<?>> loadCompiledClasses(
      ClassLoader classLoader, Iterable<? extends JavaFileObject> compilationUnits) {
    try {
      ImmutableList.Builder<Class<?>> resultBuilder = ImmutableList.builder();
      for (JavaFileObject unit : compilationUnits) {
        if (unit.getKind() == Kind.SOURCE) {
          String typeName = SourceBuilder.getTypeNameFromSource(unit.getCharContent(true));
          resultBuilder.add(classLoader.loadClass(typeName));
        }
      }
      return resultBuilder.build();
    } catch (IOException | ClassNotFoundException e) {
      fail("Unexpected failure: " + e);
      return null; // Unreachable
    }
  }

  private static List<Method> getTestMethods(Class<?> cls) {
    ImmutableList.Builder<Method> resultBuilder = ImmutableList.builder();
    for (Method method : cls.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Test.class)) {
        Preconditions.checkState(Modifier.isPublic(method.getModifiers()),
            "Test %s#%s is not public", cls.getName(), method.getName());
        Preconditions.checkState(method.getParameterTypes().length == 0,
            "Test %s#%s has parameters", cls.getName(), method.getName());
        resultBuilder.add(method);
      }
    }
    return resultBuilder.build();
  }

  private static void compile(
      JavaFileManager fileManager,
      Iterable<? extends JavaFileObject> compilationUnits,
      Iterable<? extends Processor> processors) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    CompilationTask task = getCompiler().getTask(
        null,
        fileManager,
        diagnostics,
        null,
        null,
        compilationUnits);
    task.setProcessors(processors);
    boolean successful = task.call();
    if (!successful) {
      throw new CompilationException(diagnostics.getDiagnostics());
    }
  }

  private static JavaCompiler getCompiler() {
    return ToolProvider.getSystemJavaCompiler();
  }
}
