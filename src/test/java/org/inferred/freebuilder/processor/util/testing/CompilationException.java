/*
 * Copyright 2015 Google Inc. All rights reserved.
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

import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.google.common.collect.ImmutableList;

/**
 * {@code CompilationException} is thrown when a
 * {@link javax.tools.JavaCompiler.CompilationTask CompilationTask} fails, to capture any emitted
 * {@link Diagnostic} instances.
 */
public class CompilationException extends RuntimeException {

  private final ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics;

  public CompilationException(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    this.diagnostics = ImmutableList.copyOf(diagnostics);
  }

  public CompilationException(CompilationException e) {
    this(e.diagnostics);
  }

  @Override
  public String getMessage() {
    StringBuilder fullMessage = new StringBuilder("Compilation failed");
    int i = 1;
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
      fullMessage.append("\n    ").append(i++).append(") ");
      Diagnostics.appendTo(fullMessage, diagnostic, 8);
    }
    return fullMessage.toString();
  }
}
