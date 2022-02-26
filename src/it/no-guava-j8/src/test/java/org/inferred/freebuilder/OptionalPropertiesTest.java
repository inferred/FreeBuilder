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
package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OptionalPropertiesTest {

  private static final String NAME_1 = "name1";
  private static final String NAME_2 = "name2";
  private static final String NAME_3 = "name3";

  @Test
  public void testSetters() {
    OptionalProperties.Builder builder = new OptionalProperties.Builder();
    assertEquals(Optional.empty(), builder.build().getFirstName());
    assertEquals(Optional.empty(), builder.build().getMiddleName());
    assertEquals(Optional.empty(), builder.build().getSurname());
    assertEquals("OptionalProperties{}", builder.build().toString());
    builder.setFirstName(NAME_1);
    assertEquals("OptionalProperties{firstName=name1}", builder.build().toString());
    builder.setMiddleName(NAME_2);
    assertEquals(
        "OptionalProperties{firstName=name1, middleName=name2}", builder.build().toString());
    builder.setSurname(NAME_3);
    assertEquals(Optional.of(NAME_1), builder.build().getFirstName());
    assertEquals(Optional.of(NAME_2), builder.build().getMiddleName());
    assertEquals(Optional.of(NAME_3), builder.build().getSurname());
    assertEquals(
        "OptionalProperties{firstName=name1, middleName=name2, surname=name3}",
        builder.build().toString());
  }
}
