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
package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.base.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** FreeBuilder test using {@link OptionalGwtType}. */
@RunWith(JUnit4.class)
public class OptionalGwtTypeTest {

  private static final String NAME_1 = "name1";
  private static final String NAME_2 = "name2";
  private static final String NAME_3 = "name3";

  @Test
  public void testAllMethodInteractions() {
    OptionalGwtType.Builder builder = new OptionalGwtType.Builder();
    builder.setName(NAME_1);
    assertEquals(Optional.of(NAME_1), builder.getName());
    assertEquals(Optional.of(NAME_1), builder.build().getName());
    builder.clearName();
    assertEquals(Optional.absent(), builder.getName());
    assertEquals(Optional.absent(), builder.build().getName());
    builder.setNullableName(NAME_3);
    assertEquals(Optional.of(NAME_3), builder.getName());
    assertEquals(Optional.of(NAME_3), builder.build().getName());
    builder.setName(Optional.absent());
    assertEquals(Optional.absent(), builder.getName());
    assertEquals(Optional.absent(), builder.build().getName());
    builder.setNullableName(null);
    assertEquals(Optional.absent(), builder.getName());
    assertEquals(Optional.absent(), builder.build().getName());
    builder.setName(Optional.of(NAME_2));
    assertEquals(Optional.of(NAME_2), builder.getName());
    assertEquals(Optional.of(NAME_2), builder.build().getName());
    builder.clear();
    assertEquals(Optional.absent(), builder.getName());
    assertEquals(Optional.absent(), builder.build().getName());
  }
}
