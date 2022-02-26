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
package org.inferred.freebuilder.processor.source.testing;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.util.concurrent.Uninterruptibles.joinUninterruptibly;
import static java.util.stream.Collectors.toSet;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static org.inferred.freebuilder.processor.source.feature.GuavaLibrary.GUAVA;
import static org.inferred.freebuilder.processor.source.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.feature.SourceLevel;
import org.inferred.freebuilder.processor.source.testing.TestBuilder.TestFile;
import org.inferred.freebuilder.processor.source.testing.TestBuilder.TestSource;

class SingleBehaviorTester implements BehaviorTester {

  private final SourceLevel sourceLevel;
  private final ImmutableSet.Builder<String> permittedPackages =
      ImmutableSet.<String>builder()
          .add("java")
          .add("sun.reflect")
          .add("com.fasterxml.jackson.annotation")
          .add("com.fasterxml.jackson.databind.annotation");
  private final List<Processor> processors = new ArrayList<>();
  private final List<JavaFileObject> compilationUnits = new ArrayList<>();
  private boolean shouldSetContextClassLoader = false;
  private final Multiset<String> seenNames = LinkedHashMultiset.create();
  private final Map<TestSource, TestFile> testFilesBySource = new LinkedHashMap<>();

  SingleBehaviorTester(FeatureSet features) {
    sourceLevel = features.get(SOURCE_LEVEL);
    if (features.get(GUAVA).isAvailable()) {
      permittedPackages.add("com.google.common");
    }
  }

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
  public BehaviorTester withPermittedPackage(Package pkg) {
    permittedPackages.add(pkg.getName());
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
        compile(fileManager, compilationUnits, processors, sourceLevel);
    Set<String> testFiles =
        testFilesBySource.values().stream().map(TestFile::getClassName).collect(toSet());

    // The compiled source classes may only access packages in the feature set, and explicitly
    // whitelisted test packages.
    ClassLoader restrictedClassLoader = new RestrictedClassLoader(permittedPackages.build());
    ClassLoader sourceClassLoader =
        new SourceClassLoader(restrictedClassLoader, fileManager, testFiles);

    // The compiled test classes can access anything.
    ClassLoader testClassLoader = new TestClassLoader(sourceClassLoader, fileManager, testFiles);

    return new SingleCompilationSubject(
        testClassLoader, diagnostics, shouldSetContextClassLoader, testFilesBySource);
  }

  @Override
  public CompilationFailureSubject failsToCompile() {
    try {
      compiles();
      throw new AssertionError("Expected compilation to fail but it succeeded");
    } catch (CompilationException e) {
      return new CompilationFailureSubject() {
        @Override
        public CompilationFailureSubject withErrorThat(
            Consumer<DiagnosticSubject> diagnosticAssertions) {
          diagnosticAssertions.accept(
              MultipleDiagnosticSubjects.create(e.getDiagnostics(), Kind.ERROR));
          return this;
        }
      };
    }
  }

  /**
   * A wrapper around the boot classloader that blocks access to packages not in the current {@link
   * FeatureSet}.
   */
  private static class RestrictedClassLoader extends ClassLoader {

    private final List<String> permittedPackages;

    RestrictedClassLoader(Iterable<String> permittedPackages) {
      this.permittedPackages = ImmutableList.copyOf(permittedPackages);
    }

    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!permittedPackages.stream().anyMatch(pkg -> name.startsWith(pkg))) {
        throw new ClassNotFoundException();
      }
      return super.loadClass(name, resolve);
    }
  }

  /**
   * A classloader for compiled source classes. Permits access to classes in a single parent
   * classloader.
   */
  private static class SourceClassLoader extends ClassLoader {

    private final JavaFileManager fileManager;
    private final Set<String> testFiles;

    SourceClassLoader(ClassLoader parent, JavaFileManager fileManager, Set<String> testFiles) {
      super(parent);
      this.fileManager = fileManager;
      this.testFiles = testFiles;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      if (testFiles.contains(name.split("[$]", 0)[0])) {
        throw new ClassNotFoundException();
      }
      try {
        JavaFileObject classFile =
            fileManager.getJavaFileForInput(StandardLocation.CLASS_OUTPUT, name, CLASS);
        if (classFile == null) {
          throw new ClassNotFoundException();
        }
        byte[] bytes = ByteStreams.toByteArray(classFile.openInputStream());
        return super.defineClass(name, bytes, 0, bytes.length);
      } catch (IOException e) {
        throw new ClassNotFoundException();
      }
    }
  }

  /**
   * A classloader for compiled test code. Permits access to classes in a parent classloader, as
   * well as anything visible from the boot classloader. This means test code can access test
   * libraries without them leaking into the source classloader.
   */
  private static class TestClassLoader extends ClassLoader {

    private final TempJavaFileManager fileManager;
    private final Set<String> testFiles;

    TestClassLoader(ClassLoader parent, TempJavaFileManager fileManager, Set<String> testFiles) {
      super(parent);
      this.fileManager = fileManager;
      this.testFiles = testFiles;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      if (!testFiles.contains(name.split("[$]", 0)[0])) {
        return SingleBehaviorTester.class.getClassLoader().loadClass(name);
      }
      try {
        JavaFileObject classFile =
            fileManager.getJavaFileForInput(StandardLocation.CLASS_OUTPUT, name, CLASS);
        if (classFile == null) {
          throw new ClassNotFoundException();
        }
        byte[] bytes = ByteStreams.toByteArray(classFile.openInputStream());
        return super.defineClass(name, bytes, 0, bytes.length);
      } catch (IOException e) {
        throw new ClassNotFoundException();
      }
    }
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
      ImmutableList<Diagnostic<? extends JavaFileObject>> warnings =
          FluentIterable.from(diagnostics)
              .filter(
                  Diagnostics.isKind(Diagnostic.Kind.WARNING, Diagnostic.Kind.MANDATORY_WARNING))
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
    public CompilationSubject withWarningThat(Consumer<DiagnosticSubject> diagnosticAssertions) {
      checkState(classLoader != null, "CompilationSubject closed");
      diagnosticAssertions.accept(MultipleDiagnosticSubjects.create(diagnostics, Kind.WARNING));
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
        Iterable<? extends TestSource> testSources, boolean shouldSetContextClassLoader) {
      checkState(classLoader != null, "CompilationSubject closed");
      Iterable<TestFile> testFiles =
          Iterables.transform(
              testSources,
              testSource ->
                  testFilesBySource.computeIfAbsent(
                      testSource,
                      s -> {
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
      Thread t =
          new Thread() {
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
        throwIfUnchecked(exceptions.get(0));
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
      Iterable<? extends Processor> processors,
      SourceLevel sourceLevel) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    List<String> arguments =
        ImmutableList.<String>builder()
            .add("-Xlint:unchecked")
            .add("-Xlint:varargs")
            .add("-Xdiags:verbose")
            .addAll(sourceLevel.javacArguments())
            .build();
    CompilationTask task =
        getCompiler().getTask(null, fileManager, diagnostics, arguments, null, compilationUnits);
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
