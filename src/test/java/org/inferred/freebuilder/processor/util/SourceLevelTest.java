/*
 * Copyright 2015 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.util;

import static org.junit.Assert.assertEquals;

import javax.lang.model.SourceVersion;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceLevelTest {

  @Test
  public void testFrom() {
    // Tests are currently run with JDK 7, so we can test up to RELEASE_7.
    assertEquals(SourceLevel.JAVA_6, SourceLevel.from(SourceVersion.RELEASE_6));
    assertEquals(SourceLevel.JAVA_7, SourceLevel.from(SourceVersion.RELEASE_7));
  }
}
