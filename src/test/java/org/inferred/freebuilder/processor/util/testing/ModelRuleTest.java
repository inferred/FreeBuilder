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

import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;

/** Tests for {@link ModelRule}. */
@RunWith(JUnit4.class)
public class ModelRuleTest {

  @Rule public ModelRule model = new ModelRule();
  public ModelRule brokenModel = new ModelRule();

  @Test
  public void newType() {
    TypeElement type = model.newType(
        "package foo.bar;",
        "public class MyType {",
        "  public void doNothing() { }",
        "}");
    assertEquals(ElementKind.CLASS, type.getKind());
    assertEquals(NestingKind.TOP_LEVEL, type.getNestingKind());
    assertEquals("MyType", type.getSimpleName().toString());
    assertEquals("foo.bar.MyType", type.toString());
    assertEquals("doNothing",
        getOnlyElement(methodsIn(type.getEnclosedElements())).getSimpleName().toString());
  }

  @Test(expected = IllegalStateException.class)
  public void testRuleAnnotationMissing() {
    brokenModel.typeUtils();
  }
}
