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
package org.inferred.freebuilder.processor.util.testing;

import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Parameterized;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * Optimized test runner factory for {@link Parameterized} tests using {@link BehaviorTester}.
 *
 * @see BehaviorTestRunner
 */
public class ParameterizedBehaviorTestFactory implements ParametersRunnerFactory {

  private static class ParameterizedBehaviorTestRunner
        extends BlockJUnit4ClassRunnerWithParameters {

    private final SharedBehaviorTesting testing;

    ParameterizedBehaviorTestRunner(TestWithParameters test) throws InitializationError {
      super(test);

      // JDK-8 bug: Cannot use `super::` if the superclass method is protected; see
      //     https://bugs.openjdk.java.net/browse/JDK-8139836
      testing = new SharedBehaviorTesting(
          notifier -> super.childrenInvoker(notifier),
          (method, notifier) -> super.runChild(method, notifier),
          super::createTest,
          super::getDescription,
          super::getTestClass,
          (cls, name) -> Description.createTestDescription(cls, name + test.getParameters()));
    }

    @Override
    public Description getDescription() {
      return testing.getDescription();
    }

    @Override
    protected Statement childrenInvoker(RunNotifier notifier) {
      return testing.childrenInvoker(notifier);
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
      testing.runChild(method, notifier);
    }

    @Override
    public Object createTest() throws Exception {
      return testing.createTest();
    }
  }

  @Override
  public Runner createRunnerForTestWithParameters(TestWithParameters test)
      throws InitializationError {
    return new ParameterizedBehaviorTestRunner(test);
  }
}
