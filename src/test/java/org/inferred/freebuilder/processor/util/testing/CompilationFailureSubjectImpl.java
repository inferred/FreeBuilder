package org.inferred.freebuilder.processor.util.testing;

import static java.util.stream.Collectors.joining;

import org.inferred.freebuilder.processor.util.testing.BehaviorTester.CompilationFailureSubject;
import org.inferred.freebuilder.processor.util.testing.BehaviorTester.DiagnosticSubject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public class CompilationFailureSubjectImpl implements CompilationFailureSubject {

  private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

  CompilationFailureSubjectImpl(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    this.diagnostics = diagnostics;
  }

  @Override
  public CompilationFailureSubject withErrorThat(Consumer<DiagnosticSubject> diagnosticAssertions) {
    if (diagnostics.isEmpty()) {
      throw new AssertionError("Expected compilation to fail with an error, but none were emitted");
    }
    diagnosticAssertions.accept(new MultipleDiagnosticSubjects(diagnostics));
    return this;
  }


  private class MultipleDiagnosticSubjects implements DiagnosticSubject {

    private final List<Diagnostic<? extends JavaFileObject>> diagnostics;
    private final Iterator<Diagnostic<? extends JavaFileObject>> nextCandidate;
    private Diagnostic<? extends JavaFileObject> candidate;
    private final List<String> candidateProfile = new ArrayList<>();

    MultipleDiagnosticSubjects(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
      this.diagnostics = diagnostics;
      this.nextCandidate = diagnostics.iterator();
      this.candidate = nextCandidate.next();
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
    public DiagnosticSubject inFile(CharSequence expected) {
      candidateProfile.add("in file '" + expected + "'");
      while (!candidate.getSource().getName().contentEquals(expected)) {
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
      errorMessage.append("Expected compilation to fail with an error ");
      errorMessage.append(candidateProfile.stream().collect(joining(" ")));
      if (diagnostics.size() == 1) {
        errorMessage.append(" but the only error emitted was " + diagnostics.get(0));
      } else {
        errorMessage.append("\nIn fact, the following errors were emitted:\n");
        int i = 1;
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
          errorMessage.append("  ").append(i++).append(") ").append(diagnostic).append("\n");
        }
      }
      return errorMessage.toString();
    }
  }
}
