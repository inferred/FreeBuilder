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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** FreeBuilder test using {@link NestedListGwtType}. */
@RunWith(JUnit4.class)
public class NestedListGwtTypeTest {

  private static final String NAME_1 = "name1";
  private static final String NAME_2 = "name2";
  private static final String NAME_3 = "name3";

  @Test
  public void testAllMethodInteractions() {
    NestedListGwtType.Builder builder = new NestedListGwtType.Builder();
    // getItemBuilder()
    builder.getItemBuilder().addNames(NAME_1);
    assertEquals(asList(NAME_1), builder.build().getItem().getNames());
    // setItem(Value)
    builder.setItem(new StringListGwtType.Builder().addNames(NAME_2, NAME_3).build());
    assertEquals(asList(NAME_2, NAME_3), builder.build().getItem().getNames());
    // setItem(Builder)
    builder.setItem(new StringListGwtType.Builder().addNames(NAME_1, NAME_3));
    assertEquals(asList(NAME_1, NAME_3), builder.build().getItem().getNames());
    // Top-level clear()
    builder.clear();
    assertEquals(asList(), builder.build().getItem().getNames());
    // mergeFrom(Value)
    builder.mergeFrom(
        new NestedListGwtType.Builder()
            .setItem(new StringListGwtType.Builder().addNames(NAME_2))
            .build());
    assertEquals(asList(NAME_2), builder.build().getItem().getNames());
    // mergeFrom(Builder)
    builder.mergeFrom(
        new NestedListGwtType.Builder().setItem(new StringListGwtType.Builder().addNames(NAME_3)));
    assertEquals(asList(NAME_2, NAME_3), builder.build().getItem().getNames());
  }
}
