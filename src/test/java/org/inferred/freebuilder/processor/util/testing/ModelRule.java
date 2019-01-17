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
package org.inferred.freebuilder.processor.util.testing;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * {@link org.junit.rules.TestRule TestRule} for creating javax.lang.model instances in a unit
 * test.
 *
 * <p>The standard javax.lang.model.util objects, and {@link javax.lang.model.type.TypeMirror
 * TypeMirror} instances for existing classes, are readily available. More complex {@link
 * javax.lang.model.element.Element elements} can be constructed from Java source code snippets,
 * allowing top-level types and even code with errors in to be contained within a single test
 * method.
 *
 * <blockquote><code><pre>
 * {@link org.junit.runner.RunWith @RunWith}({@link org.junit.runners.JUnit4 JUnit4}.class)
 * public class TypeUtilsTest {
 *
 *   {@link org.junit.Rule @Rule} public final ModelRule model = new ModelRule();
 *
 *   {@link org.junit.Test @Test}
 *   public void aTest() {
 *     TypeMirror intType = {@link #typeMirror model.typeMirror}(int.class);
 *     TypeElement myType = {@link #newType model.newType}(
 *         "package my.test.package;",
 *         "public class MyType {",
 *         "  public void aMethod(int anArg);",
 *         "}");
 *     ...
 * </pre></code></blockquote>
 */
public class ModelRule extends Model implements TestRule {

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        start();
        try {
          base.evaluate();
        } finally {
          destroy();
        }
      }
    };
  }
}
