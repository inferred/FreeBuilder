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

import static com.google.common.truth.Truth.assertThat;
import static org.inferred.freebuilder.processor.util.testing.SourceBuilder.getTypeNameFromSource;
import static org.junit.Assert.assertEquals;

import org.inferred.freebuilder.processor.util.testing.TestBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

import javax.tools.JavaFileObject;

/** Unit tests for {@link TestBuilder}. */
@RunWith(JUnit4.class)
public class TestBuilderTest {
  @Test
  public void testUniqueNames() throws IOException {
    JavaFileObject source1 = new TestBuilder().build();
    assertEquals(
        "org.inferred.freebuilder.processor.util.testing.generatedcode.TestBuilderTest",
        getTypeNameFromSource(source1.getCharContent(false)));
    JavaFileObject source2 = new TestBuilder().build();
    assertEquals(
        "org.inferred.freebuilder.processor.util.testing.generatedcode.TestBuilderTest__2",
        getTypeNameFromSource(source2.getCharContent(false)));

    source1 = null;

    JavaFileObject source3 = new TestBuilder().build();
    assertEquals(
        "org.inferred.freebuilder.processor.util.testing.generatedcode.TestBuilderTest",
        getTypeNameFromSource(source3.getCharContent(false)));
    JavaFileObject source4 = new TestBuilder().build();
    assertEquals(
        "org.inferred.freebuilder.processor.util.testing.generatedcode.TestBuilderTest__3",
        getTypeNameFromSource(source4.getCharContent(false)));
  }

  public static class InnerClass { }

  @Test
  public void testInnerClassNames() throws IOException {
    String result = new TestBuilder()
        .addLine("%s", InnerClass.class)
        .build()
        .getCharContent(false)
        .toString();
    assertThat(result).contains(
        "org.inferred.freebuilder.processor.util.testing.TestBuilderTest.InnerClass");
  }
}
