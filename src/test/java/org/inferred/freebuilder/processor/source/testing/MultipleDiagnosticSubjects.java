package org.inferred.freebuilder.processor.source.testing;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import org.inferred.freebuilder.processor.source.testing.BehaviorTester.DiagnosticSubject;

class MultipleDiagnosticSubjects implements DiagnosticSubject {

  private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
  private final Iterator<Diagnostic<? extends JavaFileObject>> nextCandidate;
  private Diagnostic<? extends JavaFileObject> candidate;
  private final List<String> candidateProfile = new ArrayList<>();

  static DiagnosticSubject create(
      List<Diagnostic<? extends JavaFileObject>> diagnostics, Kind kind) {
    if (diagnostics.isEmpty()) {
      throw new AssertionError("Expected " + kindAsString(kind) + ", but none were emitted");
    }
    return new MultipleDiagnosticSubjects(diagnostics).ofKind(kind);
  }

  private static String kindAsString(Kind kind) {
    switch (kind) {
      case ERROR:
        return "an error";
      case OTHER:
        return "a message";
      default:
        return "a " + kind.toString().toLowerCase();
    }
  }

  private MultipleDiagnosticSubjects(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    this.diagnostics = diagnostics;
    this.nextCandidate = diagnostics.iterator();
    this.candidate = nextCandidate.next();
  }

  private DiagnosticSubject ofKind(Kind kind) {
    candidateProfile.add(kindAsString(kind));
    while (candidate.getKind() != kind) {
      if (!nextCandidate.hasNext()) {
        throw new AssertionError(noneMatch());
      }
      candidate = nextCandidate.next();
    }
    return this;
  }

  @Override
  public DiagnosticSubject hasMessage(CharSequence expected) {
    candidateProfile.add("with message '" + expected + "'");
    while (!candidate.getMessage(null).contentEquals(expected)) {
      if (!nextCandidate.hasNext()) {
        throw new AssertionError(noneMatch());
      }
      candidate = nextCandidate.next();
    }
    return this;
  }

  @Override
  public DiagnosticSubject inFile(String expected) {
    candidateProfile.add("in file '" + expected + "'");
    while (candidate.getSource() == null
        || !Objects.equals(candidate.getSource().getName(), expected)) {
      if (!nextCandidate.hasNext()) {
        throw new AssertionError(noneMatch());
      }
      candidate = nextCandidate.next();
    }
    return this;
  }

  @Override
  public DiagnosticSubject onLine(long line) {
    candidateProfile.add("on line " + line);
    while (candidate.getLineNumber() != line) {
      if (!nextCandidate.hasNext()) {
        throw new AssertionError(noneMatch());
      }
      candidate = nextCandidate.next();
    }
    return this;
  }

  private String noneMatch() {
    StringBuilder errorMessage = new StringBuilder();
    errorMessage.append("Expected compilation to fail with ");
    errorMessage.append(candidateProfile.stream().collect(joining(" ")));
    if (diagnostics.size() == 1) {
      errorMessage.append(" but the only message emitted was:\n" + diagnostics.get(0));
    } else {
      errorMessage.append("\nIn fact, the following messages were emitted:");
      int i = 1;
      for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
        errorMessage.append("\n  ").append(i++).append(") ").append(diagnostic);
      }
    }
    return errorMessage.toString();
  }
}
