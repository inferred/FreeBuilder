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
package org.inferred.freebuilder.processor.util.feature;

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;

@RunWith(JUnit4.class)
public class SourceLevelTest {

  @Test
  public void java6() {
    assertEquals(SourceLevel.JAVA_6, sourceLevelFrom(SourceVersion.RELEASE_6));
  }

  @Test
  public void java7() {
    assertEquals(SourceLevel.JAVA_7, sourceLevelFrom(SourceVersion.RELEASE_7));
  }

  private static SourceLevel sourceLevelFrom(SourceVersion version) {
    ProcessingEnvironment env = mock(ProcessingEnvironment.class);
    when(env.getSourceVersion()).thenReturn(version);
    return SOURCE_LEVEL.forEnvironment(env);
  }
}
