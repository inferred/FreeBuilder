package org.inferred.freebuilder.processor.util.testing;

import java.io.File;
import java.util.EnumSet;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;

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
    return new Predicate<Diagnostic<?>>() {
      @Override public boolean apply(Diagnostic<?> diagnostic) {
        return allKinds.contains(diagnostic.getKind());
      }
    };
  }

  private Diagnostics() {}
}
