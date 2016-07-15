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

import static com.google.common.base.Predicates.not;
import static com.google.common.util.concurrent.Uninterruptibles.joinUninterruptibly;
import static org.junit.Assert.fail;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

class BehaviorTesterImpl implements BehaviorTester {
  private final List<Processor> processors = new ArrayList<Processor>();
  private final List<JavaFileObject> compilationUnits = new ArrayList<JavaFileObject>();
  private boolean shouldSetContextClassLoader = false;

  @Override
  public BehaviorTesterImpl with(Processor processor) {
    processors.add(processor);
    return this;
  }

  @Override
  public BehaviorTesterImpl with(JavaFileObject compilationUnit) {
    compilationUnits.add(compilationUnit);
    return this;
  }

  @Override
  public BehaviorTesterImpl withContextClassLoader() {
    shouldSetContextClassLoader = true;
    return this;
  }

  public static class CompilationSubjectImpl implements CompilationSubject {

    private final ClassLoader classLoader;
    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

    private CompilationSubjectImpl(
        ClassLoader classLoader,
        List<Diagnostic<? extends JavaFileObject>> diagnostics) {
      this.classLoader = classLoader;
      this.diagnostics = diagnostics;
    }

    @Override
    public CompilationSubject withNoWarnings() {
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
    public CompilationSubject testsPass(
        Iterable<? extends JavaFileObject> compilationUnits,
        boolean shouldSetContextClassLoader) {
      final List<Throwable> exceptions = new ArrayList<Throwable>();
      if (shouldSetContextClassLoader) {
        Thread t = new Thread() {
          @Override
          public void run() {
            runTests(classLoader, compilationUnits, exceptions);
          }
        };
        t.setContextClassLoader(classLoader);
        t.start();
        joinUninterruptibly(t);
      } else {
        runTests(classLoader, compilationUnits, exceptions);
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
      return this;
    }
  }

  @Override
  public CompilationSubject compiles() {
    TempJavaFileManager fileManager = new TempJavaFileManager();
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        compile(fileManager, compilationUnits, processors);
    ClassLoader classLoader = fileManager.getClassLoader(StandardLocation.CLASS_OUTPUT);
    return new CompilationSubjectImpl(classLoader, diagnostics);
  }

  @Override
  public void runTest() {
    compiles().testsPass(compilationUnits, shouldSetContextClassLoader);
  }

  private static void runTests(
      ClassLoader classLoader,
      Iterable<? extends JavaFileObject> compilationUnits,
      List<Throwable> throwables) {
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

  private static ImmutableList<Diagnostic<? extends JavaFileObject>> compile(
      JavaFileManager fileManager,
      Iterable<? extends JavaFileObject> compilationUnits,
      Iterable<? extends Processor> processors) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    CompilationTask task = getCompiler().getTask(
        null,
        fileManager,
        diagnostics,
        ImmutableList.of("-Xlint:unchecked", "-Xdiags:verbose"),
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
