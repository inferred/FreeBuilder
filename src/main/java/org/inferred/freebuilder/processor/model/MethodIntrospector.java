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
package org.inferred.freebuilder.processor.model;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

import org.inferred.freebuilder.processor.model.javac.JavacMethodIntrospector;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.tools.Diagnostic;

/** Compiler-specific methods for introspecting methods during compilation. */
public abstract class MethodIntrospector {

  private static final String JAVAC_METHOD_INTROSPECTOR =
      "org.inferred.freebuilder.processor.model.javac.JavacMethodIntrospector";

  public interface OwnMethodInvocationVisitor {
    interface Logger {
      void logMessage(Diagnostic.Kind kind, CharSequence msg);
    }

    void visitInvocation(Name methodName, Logger logger);
  }

  /**
   * Returns a set of methods which are definitely invoked on {@code this} in the given method,
   * or the empty set if method introspection is not supported on this compiler.
   */
  public abstract Set<Name> getOwnMethodInvocations(ExecutableElement method);

  /**
   * Calls {@code visitor} with every method invoked on {@code this} in the given method, if
   * method introspection is supported on this compiler.
   *
   * <p>The visitor is given the method name, and a logger to log warnings or errors to the
   * user at the method invocation site.
   */
  public abstract void visitAllOwnMethodInvocations(
      ExecutableElement method,
      OwnMethodInvocationVisitor visitor);

  /** Returns a {@link MethodIntrospector} implementation for the given environment. */
  public static MethodIntrospector instance(ProcessingEnvironment env) {
    try {
      try {
        return JavacMethodIntrospector.instance(env);
      } catch (LinkageError e) {
        return (MethodIntrospector) IntrospectorClassLoader
            .create(MethodIntrospector.class, env)
            .loadClass(JAVAC_METHOD_INTROSPECTOR)
            .getMethod("instance", ProcessingEnvironment.class)
            .invoke(null, env);
      }
    } catch (Exception | LinkageError e) {
      return new NoMethodIntrospector();
    }
  }

  /**
   * Loads {@link JavacMethodIntrospector} using types taken from both the environment class loader
   * (for com.sun types, if available) and the processor class loader (for org.inferred types).
   *
   * <p>Lets us succeed even when the two class loaders are isolated, for instance during Gradle
   * tests.
   */
  private static class IntrospectorClassLoader extends ClassLoader {

    private final ClassLoader processorLoader;
    private final ClassLoader processingEnvironmentLoader;

    static ClassLoader create(Class<?> processorClass, ProcessingEnvironment env) {
      return new IntrospectorClassLoader(
          processorClass.getClassLoader(), env.getClass().getClassLoader());
    }

    private IntrospectorClassLoader(
        ClassLoader processorLoader,
        ClassLoader processingEnvironmentLoader) {
      this.processorLoader = requireNonNull(processorLoader);
      this.processingEnvironmentLoader = requireNonNull(processingEnvironmentLoader);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name.startsWith(JAVAC_METHOD_INTROSPECTOR)) {
        String path = name.replace('.', '/').concat(".class");
        URL resource = processorLoader.getResource(path);
        try (InputStream stream = resource.openStream()) {
          byte[] bytes = ByteStreams.toByteArray(stream);
          return super.defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        try {
          return processingEnvironmentLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
          return processorLoader.loadClass(name);
        }
      }
    }
  }

  private static class NoMethodIntrospector extends MethodIntrospector {
    @Override
    public Set<Name> getOwnMethodInvocations(ExecutableElement method) {
      return ImmutableSet.of();
    }

    @Override
    public void visitAllOwnMethodInvocations(
        ExecutableElement method,
        OwnMethodInvocationVisitor visitor) { }
  }
}
