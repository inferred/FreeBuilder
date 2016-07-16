package org.inferred.freebuilder.processor.util.testing;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.junit.runner.Description.createTestDescription;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import org.inferred.freebuilder.processor.util.testing.BehaviorTester.CompilationSubject;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;

public class BehaviorTesterRunner implements ParametersRunnerFactory {

  private static class RunnerWithParameters extends BlockJUnit4ClassRunnerWithParameters {

    private static class Compiler implements Supplier<CompilationSubject> {
      private CompiledUnits units;
      private CompilationSubject subject;

      Compiler(CompiledUnits units) {
        this.units = units;
      }

      @Override
      public CompilationSubject get() {
        if (subject == null) {
          BehaviorTesterImpl tester = new BehaviorTesterImpl();
          for (Processor processor : units.processors) {
            tester.with(processor);
          }
          for (JavaFileObject compilationUnit : units.compilationUnits.values()) {
            tester.with(compilationUnit);
          }
          subject = tester.compiles();
          units = null;  // Allow compilers to be reclaimed by GC (processors retain a reference)
        }
        return subject;
      }
    }

    private static final String COMPILATION_FAILURE_EXPECTED =
        "Expected test to throw an instance of " + CompilationException.class.getName();

    private final Field testerField;
    private Map<FrameworkMethod, CompiledUnits> methods;
    private BehaviorTester tester;
    private final Description introspection;

    private RunnerWithParameters(TestWithParameters test) throws InitializationError {
      super(test);
      List<Field> testerFields = stream(test.getTestClass().getJavaClass().getFields())
          .filter(field -> field.getType().equals(BehaviorTester.class))
          .collect(toList());
      if (testerFields.isEmpty()) {
        throw new InitializationError("No public BehaviorTester field found");
      } else if (testerFields.size() > 1) {
        throw new InitializationError("Multiple public BehaviorTester fields found");
      }
      testerField = getOnlyElement(testerFields);
      introspection = createTestDescription(
          getTestClass().getJavaClass(), "Introspect" + test.getParameters());
    }

    @Override
    protected Statement childrenInvoker(RunNotifier notifier) {
      // Invoke children once to collect test objects to compile.
      // Invoke children again with relevant classloaders.
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          runChildren(notifier);
        }
      };
    }

    @Override
    public Description getDescription() {
      Description originalDescription = super.getDescription();
      Description suiteDescription = originalDescription.childlessCopy();
      suiteDescription.addChild(introspection);
      originalDescription.getChildren().forEach(suiteDescription::addChild);
      return suiteDescription;
    }

    private void runChildren(RunNotifier notifier) throws Throwable {
      notifier.fireTestStarted(introspection);
      try {
        Statement childrenInvoker = super.childrenInvoker(notifier);
        methods = new LinkedHashMap<>();
        childrenInvoker.evaluate();
        notifier.fireTestFinished(introspection);
      } catch (Throwable t) {
        notifier.fireTestFailure(new Failure(introspection, t));
        throw t;
      }

      Multimap<CompiledUnits, FrameworkMethod> methodsByUnits = mergeCompiledUnits(methods);
      Map<FrameworkMethod, List<JavaFileObject>> compilationUnitsByMethod =
          new LinkedHashMap<>(Maps.transformValues(methods, units ->
              ImmutableList.copyOf(units.compilationUnits.values())));
      methods = null;  // Allow compilers to be reclaimed by GC (processors retain a reference)

      for (CompiledUnits units : methodsByUnits.keySet()) {
        Compiler compiler = new Compiler(units);
        for (FrameworkMethod method : methodsByUnits.get(units)) {
          tester = new DelegatingBehaviorTester(compiler, compilationUnitsByMethod.remove(method));
          try {
            super.runChild(method, notifier);
          } finally {
            tester = null;
          }
        }
        // Allow compilers to be reclaimed by GC (processors retain a reference)
        units.processors.clear();
        // Allow unique test names to be reclaimed by GC
        units.compilationUnits.clear();
      }
    }

    private static Multimap<CompiledUnits, FrameworkMethod> mergeCompiledUnits(
        Map<FrameworkMethod, CompiledUnits> unitsByMethod) {
      // Determine what sets of units need to be compiled
      Multimap<CompiledUnits, FrameworkMethod> methodsByUnits = LinkedHashMultimap.create();
      for (Entry<FrameworkMethod, CompiledUnits> entry : unitsByMethod.entrySet()) {
        CompiledUnits mergedUnit = methodsByUnits.keySet()
            .stream()
            .filter(entry.getValue()::isCompatible)
            .findAny()
            .map(units -> units.add(entry.getValue()))
            .orElse(entry.getValue().clone());
        methodsByUnits.put(mergedUnit, entry.getKey());
      }
      System.out.println(String.format(
          "Merged %d tests into %d compiler passes",
          unitsByMethod.size(),
          methodsByUnits.keySet().size()));
      return methodsByUnits;
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
      CompiledUnits units = new CompiledUnits();
      methods.put(method, units);
      tester = units;
      try {
        super.runChild(method, new RunNotifier() {
          @Override
          public void fireTestFailure(Failure failure) {
            if (COMPILATION_FAILURE_EXPECTED.equals(failure.getMessage())) {
              // Test is expecting compilation to fail, so merging with other tests will only
              // propagate the failure. Mark the test as unmergeable.
              units.unmergeable = true;
            }
          }
        });
      } finally {
        tester = null;
      }
    }

    @Override
    public Object createTest() throws Exception {
      checkState(tester != null);
      Object test = super.createTest();
      // Inject the correct BehaviorTester implementation.
      testerField.set(test, tester);
      return test;
    }
  }

  private static class CompiledUnits implements BehaviorTester {

    boolean unmergeable = false;
    final Set<Processor> processors = new LinkedHashSet<>();
    final Map<String, JavaFileObject> compilationUnits = new LinkedHashMap<>();

    @Override
    public CompiledUnits clone() {
      CompiledUnits copy = new CompiledUnits();
      copy.processors.addAll(processors);
      copy.compilationUnits.putAll(compilationUnits);
      return copy;
    }

    boolean isCompatible(CompiledUnits other) {
      if (unmergeable || other.unmergeable) {
        return false;
      }
      if (!processors.equals(other.processors)) {
        return false;
      }
      for (Entry<String, JavaFileObject> entry : other.compilationUnits.entrySet()) {
        JavaFileObject unit = compilationUnits.get(entry.getKey());
        if (unit != null && !unit.equals(entry.getValue())) {
          return false;
        }
      }
      return true;
    }

    CompiledUnits add(CompiledUnits other) {
      compilationUnits.putAll(other.compilationUnits);
      return this;
    }

    @Override
    public BehaviorTester with(Processor processor) {
      processors.add(processor);
      return this;
    }

    @Override
    public BehaviorTester with(JavaFileObject compilationUnit) {
      compilationUnits.put(compilationUnit.getName(), compilationUnit);
      return this;
    }

    @Override
    public BehaviorTester withContextClassLoader() {
      return this;
    }

    @Override
    public CompilationSubject compiles() {
      return new CompilationSubject() {
        @Override
        public CompilationSubject withNoWarnings() {
          return this;
        }

        @Override
        public CompilationSubject testsPass(
            Iterable<? extends JavaFileObject> compilationUnits,
            boolean shouldSetContextClassLoader) {
          return this;
        }
      };
    }

    @Override
    public void runTest() {}
  }

  private static class DelegatingBehaviorTester implements BehaviorTester {

    private final Supplier<BehaviorTesterImpl.CompilationSubject> compiler;
    private final Collection<JavaFileObject> compilationUnits;
    private boolean shouldSetContextClassLoader = false;

    DelegatingBehaviorTester(
        Supplier<BehaviorTesterImpl.CompilationSubject> compiler,
        Collection<JavaFileObject> compilationUnits) {
      this.compiler = compiler;
      this.compilationUnits = compilationUnits;
    }

    @Override
    public BehaviorTester with(Processor processor) {
      return this;
    }

    @Override
    public BehaviorTester with(JavaFileObject compilationUnit) {
      return this;
    }

    @Override
    public BehaviorTester withContextClassLoader() {
      shouldSetContextClassLoader = true;
      return this;
    }

    @Override
    public CompilationSubject compiles() {
      compiler.get();
      return new CompilationSubject() {
        @Override
        public CompilationSubject withNoWarnings() {
          compiler.get().withNoWarnings();
          return this;
        }

        @Override
        public CompilationSubject testsPass(
            Iterable<? extends JavaFileObject> compilationUnits,
            boolean shouldSetContextClassLoader) {
          compiler.get().testsPass(compilationUnits, shouldSetContextClassLoader);
          return this;
        }
      };
    }

    @Override
    public void runTest() {
      compiler.get().testsPass(compilationUnits, shouldSetContextClassLoader);
    }

  }

  @Override
  public Runner createRunnerForTestWithParameters(TestWithParameters test)
      throws InitializationError {
    return new RunnerWithParameters(test);
  }

}
