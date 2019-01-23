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
package org.inferred.freebuilder.processor;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.tools.Diagnostic;

/** Compiler-specific methods for introspecting methods during compilation. */
public abstract class MethodIntrospector {

  interface OwnMethodInvocationVisitor {
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
      return JavacMethodIntrospector.instance(env);
    } catch (IllegalArgumentException e) {
      return new NoMethodIntrospector();
    } catch (LinkageError e) {
      return new NoMethodIntrospector();
    }
  }

  static class NoMethodIntrospector extends MethodIntrospector {
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
