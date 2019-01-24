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
package org.inferred.freebuilder.processor.source.testing;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;

import java.io.File;
import java.util.EnumSet;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

/**
 * Static utilities for {@link Diagnostic} instances.
 */
public class Diagnostics {

  /**
   * Appends a human-readable form of {@code diagnostic} to {@code appendable}.
   */
  public static void appendTo(
      StringBuilder appendable,
      Diagnostic<? extends JavaFileObject> diagnostic,
      int indentLevel) {
    String indent = "\n" + Strings.repeat(" ", indentLevel);
    appendable.append(diagnostic.getMessage(Locale.getDefault()).replace("\n", indent));
    JavaFileObject source = diagnostic.getSource();
    long line = diagnostic.getLineNumber();
    if (source != null && line != Diagnostic.NOPOS) {
      File sourceFile = new File(source.getName());
      appendable.append(" (").append(sourceFile.getName()).append(":").append(line).append(")");
    }
  }

  /**
   * Predicate that returns true if its argument is of kind {@code kind} or any of
   * {@code otherKinds}.
   */
  public static final Predicate<Diagnostic<?>> isKind(Kind kind, Kind... otherKinds) {
    final EnumSet<Kind> allKinds = EnumSet.of(kind, otherKinds);
    return diagnostic -> allKinds.contains(diagnostic.getKind());
  }

  private Diagnostics() {}
}
