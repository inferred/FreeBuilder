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
package org.inferred.freebuilder.processor.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

@RunWith(JUnit4.class)
public class ImportManagerTest {

  @Test
  public void testAdd() {
    ImportManager manager = new ImportManager();
    assertTrue(manager.add(QualifiedName.of("java.util", "List")));
    assertFalse(manager.add(QualifiedName.of("java.awt", "List")));
    assertTrue(manager.add(QualifiedName.of("java.util", "Map")));
    assertTrue(manager.add(QualifiedName.of("java.util", "Map", "Entry")));
    assertTrue(manager.add(QualifiedName.of("java.util", "List")));
    assertThat(manager.getClassImports()).containsExactly(
        "java.util.List", "java.util.Map", "java.util.Map.Entry");
  }

  @Test
  public void testLookup() {
    QualifiedName list1 = QualifiedName.of("java.util", "List");
    QualifiedName list2 = QualifiedName.of("java.awt", "List");

    ImportManager manager = new ImportManager();
    assertThat(manager.lookup("List")).isEqualTo(Optional.empty());
    manager.add(list1);
    assertThat(manager.lookup("List")).isEqualTo(Optional.of(list1));
    manager.add(list2);
    assertThat(manager.lookup("List")).isEqualTo(Optional.of(list1));
  }
}
