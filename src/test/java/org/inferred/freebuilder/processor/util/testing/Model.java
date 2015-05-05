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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.common.util.concurrent.UncheckedTimeoutException;

/**
 * Utility class for creating javax.lang.model instances for testing.
 *
 * <p>The standard javax.lang.model.util objects, and {@link TypeMirror} instances for existing
 * classes, are readily available. More complex {@link Element elements} can be constructed from
 * Java source code snippets, allowing top-level types and even code with errors in to be contained
 * within a single test method.
 *
 * <blockquote><code><pre>
 * {@link Model} model = {@link Model#create()};
 * TypeMirror intType = model.{@link #typeMirror}(int.class);
 * TypeElement myType = model.{@link #newType}(
 *     "package my.test.package;",
 *     "public class MyType {",
 *     "  public void aMethod(int anArg);",
 *     "}");
 * ...
 * model.{@link #destroy()};
 * </pre></code></blockquote>
 *
 * <p>To save walking the hierarchy of elements to find an inner class, or other element, you can
 * grab any annotatable element with {@link #newElementWithMarker} (which uses {@code --->} as an
 * easily-spotted element identifier) or {@link #newElementAnnotatedWith} (which uses a
 * user-supplied annotation as the element identifier):
 *
 * <blockquote><code><pre>
 * VariableElement anArg = (VariableElement) model.{@link #newElementWithMarker}(
 *     "package my.test.package;",
 *     "public class MyType2 {",
 *     "  public void aMethod(---> int anArg);",
 *     "}");
 * ExecutableElement aMethod = (ExecutableElement) model.{@link #newElementAnnotatedWith}(
 *     Deprecated.class,
 *     "package my.test.package;",
 *     "public class MyType3 {",
 *     "  {@literal @}Deprecated public void aMethod(int anArg);",
 *     "}");
 * </pre></code></blockquote>
 */
public class Model {

  public static Model create() {
    Model model = new Model();
    model.start();
    return model;
  }

  private static final String IDENTIFYING_STRING = "--->";
  private static final String PACKAGE = "codegen.internal";
  private static final String PLACEHOLDER_TYPE = "CodegenInternalPlaceholder";
  private static final int TIMEOUT_SECONDS = jvmDebugging() ? Integer.MAX_VALUE : 30;

  private static final Pattern TYPE_NAME_PATTERN = Pattern.compile(
      "(class|[@]?interface|enum) +(\\w+)");

  private static class GenerationRequest {
    final SettableFuture<Element> resultFuture = SettableFuture.create();
    final String code;
    final Class<? extends Annotation> annotationType;

    GenerationRequest(String code, Class<? extends Annotation> annotationType) {
      this.code = code;
      this.annotationType = annotationType;
    }
  }

  private ExecutorService executorService;
  private ProcessingEnvironment processingEnv;
  private SynchronousQueue<GenerationRequest> requestQueue;

  /** Starts up the compiler thread and waits for it to return the processing environment. */
  protected void start() {
    checkState(executorService == null, "Cannot restart a Model");
    executorService = Executors.newSingleThreadExecutor();
    requestQueue = new SynchronousQueue<GenerationRequest>();
    CompilerRunner compilerRunner = new CompilerRunner();
    executorService.execute(compilerRunner);
    processingEnv = compilerRunner.getProcessingEnvironment();
  }

  private void checkRunning() {
    checkState((executorService != null) && (processingEnv != null), "Model not started");
    checkState(!executorService.isShutdown(), "Model destroyed");
  }

  /** Returns a {@link Types} implementation. */
  public Types typeUtils() {
    checkRunning();
    return processingEnv.getTypeUtils();
  }

  /** Returns a {@link Elements} implementation. */
  public Elements elementUtils() {
    checkRunning();
    return processingEnv.getElementUtils();
  }

  /** Returns a {@link ProcessingEnvironment} implementation. */
  public ProcessingEnvironment environment() {
    checkRunning();
    return processingEnv;
  }

  /** Returns a {@link TypeMirror} for the given class (raw T, not T&lt;?&gt;, if T is generic). */
  public TypeMirror typeMirror(Class<?> cls) {
    return TypeMirrors.typeMirror(typeUtils(), elementUtils(), cls);
  }

  /** Returns a {@link TypeMirror} for the given type. */
  public TypeMirror typeMirror(TypeToken<?> type) {
    return TypeMirrors.typeMirror(typeUtils(), elementUtils(), type);
  }

  /**
   * Returns a {@link TypeMirror} for the given type, substituting any provided arguments for
   * %1, %2, etc.
   *
   * e.g. <code>typeMirror("java.util.List&lt;%1&gt;", typeMirror(String.class))</code> will
   * return the same thing as <code>typeMirror("java.util.List&lt;java.lang.String&gt;")</code>
   *
   * @param typeSnippet the type, represented as a snippet of Java code, e.g. "java.lang.String",
   *     "java.util.Map&lt;%1, %2&gt;"
   * @param args existing {@link TypeMirror} instances to be substituted into the type
   */
  public TypeMirror typeMirror(String typeSnippet, TypeMirror... args) {
    return TypeMirrors.typeMirror(typeUtils(), elementUtils(), typeSnippet, args);
  }

  /**
   * Returns the {@link TypeElement} of {@code cls}.
   */
  public TypeElement typeElement(Class<?> cls) {
    return asTypeElement(typeMirror(cls));
  }

  /**
   * Returns the {@link TypeElement} of {@code qualifiedType}.
   */
  public TypeElement typeElement(String qualifiedType) {
    return asTypeElement(typeMirror(qualifiedType));
  }

  private static TypeElement asTypeElement(TypeMirror mirror) {
    checkArgument(mirror.getKind() == TypeKind.DECLARED,
        "%s is a %s, not a TypeElement", mirror, mirror.getKind());
    DeclaredType declaredType = (DeclaredType) mirror;
    ElementKind elementKind = declaredType.asElement().getKind();
    checkArgument(elementKind.isClass() || elementKind.isInterface(),
        "%s is a %s, not a TypeElement", mirror, elementKind);
    return (TypeElement) declaredType.asElement();
  }

  /** Parses the supplied type definition, returning its {@link TypeElement}. */
  public TypeElement newType(final String... code) {
    String codeString = Joiner.on("\n").join(code);
    codeString = TYPE_NAME_PATTERN.matcher(codeString)
        .replaceFirst("@" + Target.class.getCanonicalName() + " $0");
    return (TypeElement) newElementAnnotatedWith(Target.class, codeString);
  }

  /**
   * Parses the supplied code, returning the {@link Element} marked with "--->". (Only
   * elements that can be annotated in Java can be found this way; the marker is substituted with
   * an annotation internally.)
   *
   * <blockquote><code><pre>
   * Element element = model.newElementWithMarker(
   *     "interface MyType {",
   *     "  void myMethod(---> int arg);",
   *     "}")
   * </pre></code></blockquote>
   */
  public Element newElementWithMarker(final String... code) {
    String codeString = Joiner.on("\n").join(code);
    checkMarkerPresentExactlyOnce(codeString);
    codeString = codeString.replaceFirst(
        IDENTIFYING_STRING, " @" + Target.class.getCanonicalName() + " ");
    return newElementAnnotatedWith(Target.class, codeString);
  }

  /**
   * Parses the supplied code, returning the {@link Element} annotated with the given annotation.
   *
   * <blockquote><code><pre>
   * Element element = model.newElementAnnotatedWith(MyAnnotation.class
   *     "interface MyType {",
   *     "  void myMethod(@MyAnnotation int arg);",
   *     "}")
   * </pre></code></blockquote>
   */
  public Element newElementAnnotatedWith(
      Class<? extends Annotation> annotationType,
      String... code) {
    try {
      String codeString = Joiner.on("\n").join(code);
      GenerationRequest request = new GenerationRequest(codeString, annotationType);
      if (!requestQueue.offer(request, TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new UncheckedTimeoutException("Code generation request timed out");
      }
      return request.resultFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof CompilationException) {
        throw new CompilationException((CompilationException) e.getCause());
      }
      throw new IllegalArgumentException("Code generation failed: " + e.getMessage(), e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Code generation interruped", e);
    } catch (TimeoutException e) {
      throw new UncheckedTimeoutException("Code generation timed out", e);
    }
  }

  /** Gracefully shuts down the compiler thread. */
  public void destroy() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  /**
   * Waits if necessary for at most the given time for the computation to complete, and then
   * retrieves its result, if available. Similar to {@link Future#get(long, TimeUnit)}, but does not
   * throw any checked exceptions.
   */
  private static <T> T getUnchecked(Future<T> future, long timeout, TimeUnit unit) {
    try {
      return future.get(timeout, unit);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw Throwables.propagate(e);
    } catch (TimeoutException e) {
      throw Throwables.propagate(e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Error) {
        throw new ExecutionError((Error) e.getCause());
      }
      throw new UncheckedExecutionException(e.getCause());
    }
  }

  private static String getTypeName(String code) {
    Matcher matcher = TYPE_NAME_PATTERN.matcher(code);
    Preconditions.checkArgument(matcher.find());
    return matcher.group(2);
  }

  private static void checkMarkerPresentExactlyOnce(String codeString) {
    Matcher matcher = Pattern.compile(IDENTIFYING_STRING).matcher(codeString);
    Preconditions.checkArgument(
        matcher.find(),
        "Code must identify the element to be returned using '" + IDENTIFYING_STRING + "'");
    Preconditions.checkArgument(
        !matcher.find(),
        "Code must only contain one element marked with '" + IDENTIFYING_STRING + "'");
  }

  /** A Runnable that bootstraps an {@link ElementCapturingProcessor} in a new compiler. */
  private class CompilerRunner implements Runnable {

    private final SettableFuture<ProcessingEnvironment> processingEnvFuture =
        SettableFuture.create();
    private Class<? extends Annotation> annotationType = Target.class;
    private SettableFuture<Element> elementFuture = SettableFuture.create();

    ProcessingEnvironment getProcessingEnvironment() {
      return getUnchecked(processingEnvFuture, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
      TempJavaFileManager fileManager = new TempJavaFileManager();
      DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
      try {
        final JavaFileObject bootstrapType = new SourceBuilder()
            .addLine("package %s;", PACKAGE)
            .addLine("@%s", Target.class)
            .addLine("class %s { }", PLACEHOLDER_TYPE)
            .build();

        CompilationTask task = getSystemJavaCompiler().getTask(
            null,  // Writer
            fileManager,
            diagnostics,
            ImmutableList.of("-proc:only", "-encoding", "UTF-8"),
            null,  // Class names
            ImmutableList.of(bootstrapType));
        task.setProcessors(ImmutableList.of(new ElementCapturingProcessor()));
        task.call();
      } catch (RuntimeException e) {
        processingEnvFuture.setException(e);
        elementFuture.setException(e);
      } finally {
        if (!processingEnvFuture.isDone()) {
          processingEnvFuture.setException(new CompilationException(diagnostics.getDiagnostics()));
        }
        if (!elementFuture.isDone()) {
          if (diagnostics.getDiagnostics().isEmpty()) {
            elementFuture.setException(new IllegalStateException(
                "Code generation terminated abnormally. Was there no annotated element?"));
          } else {
            elementFuture.setException(new CompilationException(diagnostics.getDiagnostics()));
          }
        }
        fileManager.close();
      }
    }

    /**
     * A Processor that satisfies requests put on the {@link #requestQueue}.
     *
     * <p>Every processing round, finds the last annotated element that was generated (completing
     * the previous request), and generates a new source file (from the next request). Processing
     * ends when source code with no annotated element is given.
     */
    private class ElementCapturingProcessor extends AbstractProcessor {

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingEnvFuture.set(processingEnv);
        // Some Java compilers may return spurious extra elements; hence the apparently redundant
        // Sets.filter call, just to be sure.
        Set<? extends Element> elements = Sets.filter(
            roundEnv.getElementsAnnotatedWith(annotationType),
            new HasAnnotationOfType(annotationType));
        Element element;
        try {
          element = getOnlyElement(elements, null);
        } catch (IllegalArgumentException e) {
          elementFuture.setException(new IllegalArgumentException(
              "Multiple elements annotated with @" + annotationType.getName() + " found"));
          return false;
        }
        if (element != null) {
          elementFuture.set(element);
          String code = fetchCodeForNextRequest();
          if (code != null) {
            passSourceCodeToCompiler(code);
          }
        }
        return false;
      }

      private String fetchCodeForNextRequest() {
        try {
          GenerationRequest request = requestQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
          Preconditions.checkState(request != null, "Timed out waiting for next request");
          elementFuture = request.resultFuture;
          annotationType = request.annotationType;
          return request.code;
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return null;
        }
      }

      private void passSourceCodeToCompiler(String code) {
        try {
          processingEnv.getFiler()
              .createSourceFile(getTypeName(code))
              .openWriter()
              .append(code)
              .close();
        } catch (IOException e) {
          elementFuture.setException(e);
        }
      }
    }
  }

  private static class HasAnnotationOfType implements Predicate<Element> {
    private final Class<? extends Annotation> annotationType;

    public HasAnnotationOfType(Class<? extends Annotation> annotationType) {
      this.annotationType = annotationType;
    }

    @Override public boolean apply(Element input) {
      return input.getAnnotation(annotationType) != null;
    }
  }

  /** Returns true if the JVM is likely to be being debugged, so we can adjust timeouts. */
  private static boolean jvmDebugging() {
    return ManagementFactory.getRuntimeMXBean()
        .getInputArguments().toString()
        .contains("-agentlib:jdwp");
  }
}
