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

import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newNestedClass;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.junit.Assert.assertEquals;

import org.inferred.freebuilder.processor.util.ClassTypeImpl.ClassElementImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@RunWith(JUnit4.class)
public class ImportManagerTest {

  @Test
  public void testNoConflicts() {
    ImportManager manager = new ImportManager.Builder().build();
    assertEquals("String", shorten(manager, QualifiedName.of("java.lang", "String")));
    assertEquals("List", shorten(manager, QualifiedName.of("java.util", "List")));
    assertEquals("java.awt.List", shorten(manager, QualifiedName.of("java.awt", "List")));
    assertEquals("Map", shorten(manager, QualifiedName.of("java.util", "Map")));
    assertEquals("Map.Entry", shorten(manager, QualifiedName.of("java.util", "Map", "Entry")));
    assertThat(manager.getClassImports())
        .containsExactly("java.util.List", "java.util.Map").inOrder();
  }

  @Test
  public void testConflicts() {
    ClassElementImpl listType = newTopLevelClass("org.example.List").asElement();
    ClassElementImpl stringType = newNestedClass(listType, "String").asElement();
    ImportManager manager = new ImportManager.Builder()
        .addImplicitImport(QualifiedName.of(listType))
        .addImplicitImport(QualifiedName.of(stringType))
        .build();
    assertEquals("java.lang.String", shorten(manager, QualifiedName.of("java.lang", "String")));
    assertEquals("java.util.List", shorten(manager, QualifiedName.of("java.util", "List")));
    assertEquals("java.awt.List", shorten(manager, QualifiedName.of("java.awt", "List")));
    assertEquals("Map", shorten(manager, QualifiedName.of("java.util", "Map")));
    assertThat(manager.getClassImports()).containsExactly("java.util.Map");
  }

  private static String shorten(TypeShortener shortener, QualifiedName type) {
    try {
      StringBuilder result = new StringBuilder();
      shortener.appendShortened(result, type);
      return result.toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
