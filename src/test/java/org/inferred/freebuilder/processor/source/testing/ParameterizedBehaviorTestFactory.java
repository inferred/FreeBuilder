/*
 * Copyright 2016 Google Inc. All rights reserved.
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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.junit.rules.ExpectedException;
import org.junit.runner.Runner;
import org.junit.runners.Parameterized;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * Optimized test runner factory for {@link Parameterized} tests using {@link BehaviorTester}.
 *
 * <p>Pre-runs all tests once with no compilation, to determine which can use the same compiler. If
 * two tests add the same processors and do not give conflicting java files, they will be run
 * back-to-back with the same compiler instance. This means <b>all tests are run twice</b>. Any
 * tests which do not compile the same code every time (e.g. random or time-based code) will flake
 * with this runner.
 *
 * <p>Tests that provide <em>non-compilable</em> test code <b>must</b> use {@link ExpectedException}
 * to catch the {@link CompilationException}, as this runner uses the resulting pre-run error
 * message to mark the test as <b>unmergeable</b>. (If we did not do this, all tests using the same
 * compiler would fail with the same compilation exception!)
 *
 * <p>Adds an "Introspection" test to the suite to show how much time the initial pre-run is taking.
 * If no tests can share a compiler, the "Introspection" test will fail. This may indicate your
 * processor does not have suitable equals and hashCode implementations.
 */
public class ParameterizedBehaviorTestFactory implements ParametersRunnerFactory {

  /**
   * Annotates a public {@link BehaviorTester} field that will potentially be shared between
   * multiple unit tests.
   */
  @Retention(RUNTIME)
  @Target(FIELD)
  public @interface Shared {}

  @Override
  public Runner createRunnerForTestWithParameters(TestWithParameters test)
      throws InitializationError {
    return new ParameterizedBehaviorTestRunner(test);
  }
}
