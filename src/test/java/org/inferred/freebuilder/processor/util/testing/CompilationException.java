package org.inferred.freebuilder.processor.util.testing;

import java.io.File;
import java.util.List;
import java.util.Locale;

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
      fullMessage.append("\n    ").append(i++).append(") ")
          .append(diagnostic.getMessage(Locale.getDefault()));
      JavaFileObject source = diagnostic.getSource();
      long line = diagnostic.getLineNumber();
      if (source != null && line != Diagnostic.NOPOS) {
        File sourceFile = new File(source.getName());
        fullMessage.append(" (").append(sourceFile.getName()).append(":").append(line).append(")");
      }
    }
    return fullMessage.toString();
  }
}
