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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static com.google.common.util.concurrent.Uninterruptibles.joinUninterruptibly;

import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;

import org.inferred.freebuilder.processor.util.testing.TestBuilder.TestFile;
import org.inferred.freebuilder.processor.util.testing.TestBuilder.TestSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

class SingleBehaviorTester implements BehaviorTester {
  private final List<Processor> processors = new ArrayList<>();
  private final List<JavaFileObject> compilationUnits = new ArrayList<>();
  private boolean shouldSetContextClassLoader = false;
  private final Multiset<String> seenNames = LinkedHashMultiset.create();
  private final Map<TestSource, TestFile> testFilesBySource = new LinkedHashMap<>();

  @Override
  public BehaviorTester with(Processor processor) {
    processors.add(processor);
    return this;
  }

  @Override
  public BehaviorTester with(JavaFileObject compilationUnit) {
    compilationUnits.add(compilationUnit);
    return this;
  }

  @Override
  public BehaviorTester with(TestSource testSource) {
    TestFile testFile = testSource.selectName(seenNames);
    compilationUnits.add(testFile);
    testFilesBySource.put(testSource, testFile);
    return this;
  }

  @Override
  public BehaviorTester withContextClassLoader() {
    shouldSetContextClassLoader = true;
    return this;
  }

  @Override
  public CompilationSubject compiles() {
    TempJavaFileManager fileManager = TempJavaFileManager.newTempFileManager(null, null, null);
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        compile(fileManager, compilationUnits, processors);
    final ClassLoader classLoader = fileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);
    return new SingleCompilationSubject(
        classLoader, diagnostics, shouldSetContextClassLoader, testFilesBySource);
  }

  static class SingleCompilationSubject implements CompilationSubject {

    private final ClassLoader classLoader;
    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    private final boolean shouldSetContextClassLoader;
    private final Map<TestSource, TestFile> testFilesBySource;

    private SingleCompilationSubject(
        ClassLoader classLoader,
        List<Diagnostic<? extends JavaFileObject>> diagnostics,
        boolean shouldSetContextClassLoader,
        Map<TestSource, TestFile> testFilesBySource) {
      this.classLoader = classLoader;
      this.diagnostics = diagnostics;
      this.shouldSetContextClassLoader = shouldSetContextClassLoader;
      this.testFilesBySource = testFilesBySource;
    }

    @Override
    public CompilationSubject withNoWarnings() {
      checkState(classLoader != null, "CompilationSubject closed");
      ImmutableList<Diagnostic<? extends JavaFileObject>> warnings = FluentIterable
          .from(diagnostics)
          .filter(Diagnostics.isKind(Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING))
          .toList();
      if (!warnings.isEmpty()) {
        StringBuilder message =
            new StringBuilder("The following warnings were issued by the compiler:");
        for (int i = 0; i < warnings.size(); ++i) {
          message.append("\n    ").append(i + 1).append(") ");
          Diagnostics.appendTo(message, warnings.get(i), 8);
        }
        throw new AssertionError(message.toString());
      }
      return this;
    }

    @Override
    public CompilationSubject allTestsPass() {
      checkState(classLoader != null, "CompilationSubject closed");
      runTests(classLoader, testFilesBySource.values(), shouldSetContextClassLoader);
      return this;
    }

    @Override
    public CompilationSubject testsPass(
        Iterable<? extends TestSource> testSources,
        boolean shouldSetContextClassLoader) {
      checkState(classLoader != null, "CompilationSubject closed");
      Iterable<TestFile> testFiles = Iterables.transform(testSources, testSource ->
          testFilesBySource.computeIfAbsent(testSource, s -> {
              throw new IllegalStateException("Test source not compiled: " + s);
          }));
      runTests(classLoader, testFiles, shouldSetContextClassLoader);
      return this;
    }
  }

  private static void runTests(
      ClassLoader classLoader,
      Iterable<? extends TestFile> testFiles,
      boolean shouldSetContextClassLoader) {
    final List<Throwable> exceptions = new ArrayList<>();
    if (shouldSetContextClassLoader) {
      Thread t = new Thread() {
        @Override
        public void run() {
          runTests(classLoader, testFiles, exceptions);
        }
      };
      t.setContextClassLoader(classLoader);
      t.start();
      joinUninterruptibly(t);
    } else {
      runTests(classLoader, testFiles, exceptions);
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

  private static void runTests(
      ClassLoader classLoader,
      Iterable<? extends TestFile> testFiles,
      final List<Throwable> throwables) {
    for (TestFile testFile : testFiles) {
      try {
        Class<?> testClass = classLoader.loadClass(testFile.getClassName());
        Object testInstance = testClass.newInstance();
        Method testMethod = testClass.getMethod(testFile.getMethodName());
        testMethod.invoke(testInstance);
      } catch (InvocationTargetException e) {
        throwables.add(e.getCause());
      } catch (ClassNotFoundException
          | NoSuchMethodException
          | IllegalAccessException
          | InstantiationException e) {
        throwables.add(new AssertionError("Unexpected failure", e));
      }
    }
  }

  private static ImmutableList<Diagnostic<? extends JavaFileObject>> compile(
      JavaFileManager fileManager,
      Iterable<? extends JavaFileObject> compilationUnits,
      Iterable<? extends Processor> processors) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    CompilationTask task = getCompiler().getTask(
        null,
        fileManager,
        diagnostics,
        ImmutableList.of("-Xlint:unchecked", "-Xlint:varargs", "-Xdiags:verbose"),
        null,
        compilationUnits);
    task.setProcessors(processors);
    boolean successful = task.call();
    if (!successful) {
      throw new CompilationException(diagnostics.getDiagnostics());
    }
    // Filter out any errors: if compilation succeeded, they're probably "cannot find symbol"
    // errors erroneously emitted by the compiler prior to running annotation processing.
    return FluentIterable.from(diagnostics.getDiagnostics())
        .filter(not(Diagnostics.isKind(Diagnostic.Kind.ERROR)))
        .toList();
  }

  private static JavaCompiler getCompiler() {
    return ToolProvider.getSystemJavaCompiler();
  }
}
