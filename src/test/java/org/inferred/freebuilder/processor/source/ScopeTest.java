/*
 * Copyright 2017 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.source;

import static com.google.common.truth.Truth.assertThat;

import static org.inferred.freebuilder.processor.source.Scope.Level.FILE;
import static org.inferred.freebuilder.processor.source.Scope.Level.METHOD;
import static org.junit.rules.ExpectedException.none;

import org.inferred.freebuilder.processor.source.Scope.FileScope;
import org.inferred.freebuilder.processor.source.Scope.Level;
import org.inferred.freebuilder.processor.source.Scope.MethodScope;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ScopeTest {

  private static class FileElement extends ValueType implements Scope.Key<String> {

    private final String name;

    FileElement(String name) {
      this.name = name;
    }

    @Override
    public Level level() {
      return FILE;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("name", name);
    }
  }

  private static class MethodElement extends ValueType implements Scope.Key<Integer> {

    private final String name;

    MethodElement(String name) {
      this.name = name;
    }

    @Override
    public Level level() {
      return METHOD;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("name", name);
    }
  }

  @Rule public final ExpectedException thrown = none();

  @Test
  public void testPutIfAbsent() {
    Scope scope = new FileScope();
    FileElement key1 = new FileElement("foo");
    FileElement key2 = new FileElement("bar");
    String value1 = "aString";
    String value2 = "anotherString";
    String value3 = "aThirdString";
    String value4 = "aFourthString";

    String existingValue1 = scope.putIfAbsent(key1, value1);
    assertThat(existingValue1).isNull();
    String existingValue2 = scope.putIfAbsent(key2, value2);
    assertThat(existingValue2).isNull();
    String existingValue3 = scope.putIfAbsent(key1, value3);
    assertThat(existingValue3).isEqualTo(value1);
    String existingValue4 = scope.putIfAbsent(key1, value4);
    assertThat(existingValue4).isEqualTo(value1);
  }

  @Test
  public void testPutIfAbsent_throwsGivenMethodElementAtFileScope() {
    Scope scope = new FileScope();
    MethodElement key = new MethodElement("foo");

    thrown.expect(IllegalStateException.class);
    scope.putIfAbsent(key, 1);
  }

  @Test
  public void testPutIfAbsent_methodElementStoredInMethodScope() {
    Scope fileScope = new FileScope();
    Scope methodScope1 = new MethodScope(fileScope);
    Scope methodScope2 = new MethodScope(fileScope);
    MethodElement key = new MethodElement("foo");
    Integer existingValue1 = methodScope1.putIfAbsent(key, 1);
    assertThat(existingValue1).isNull();
    Integer existingValue2 = methodScope1.putIfAbsent(key, 2);
    assertThat(existingValue2).isEqualTo(1);
    Integer existingValue3 = methodScope1.putIfAbsent(key, 3);
    assertThat(existingValue3).isEqualTo(1);
    Integer existingValue4 = methodScope2.putIfAbsent(key, 4);
    assertThat(existingValue4).isNull();
  }

  @Test
  public void testPutIfAbsent_fileElementFallsThroughToFileScope() {
    Scope fileScope = new FileScope();
    Scope methodScope1 = new MethodScope(fileScope);
    Scope methodScope2 = new MethodScope(fileScope);
    FileElement key1 = new FileElement("foo");
    FileElement key2 = new FileElement("bar");
    String value1 = "aString";
    String value2 = "anotherString";
    String value3 = "aThirdString";
    String value4 = "aFourthString";

    String existingValue1 = methodScope1.putIfAbsent(key1, value1);
    assertThat(existingValue1).isNull();
    String existingValue2 = methodScope1.putIfAbsent(key2, value2);
    assertThat(existingValue2).isNull();
    String existingValue3 = fileScope.putIfAbsent(key1, value3);
    assertThat(existingValue3).isEqualTo(value1);
    String existingValue4 = methodScope2.putIfAbsent(key1, value4);
    assertThat(existingValue4).isEqualTo(value1);
  }

  @Test
  public void testGet() {
    Scope scope = new FileScope();
    FileElement key1 = new FileElement("foo");
    FileElement key2 = new FileElement("bar");
    String value1 = "aString";
    String value2 = "anotherString";
    String value3 = "aThirdString";

    assertThat(scope.get(key1)).isNull();
    assertThat(scope.get(key2)).isNull();
    scope.putIfAbsent(key1, value1);
    assertThat(scope.get(key1)).isEqualTo(value1);
    assertThat(scope.get(key2)).isNull();
    scope.putIfAbsent(key2, value2);
    assertThat(scope.get(key1)).isEqualTo(value1);
    assertThat(scope.get(key2)).isEqualTo(value2);
    scope.putIfAbsent(key1, value3);
    assertThat(scope.get(key1)).isEqualTo(value1);
    assertThat(scope.get(key2)).isEqualTo(value2);
  }

  @Test
  public void testGet_methodElementReturnsNullAtFileScope() {
    Scope scope = new FileScope();
    MethodElement key = new MethodElement("foo");

    assertThat(scope.get(key)).isNull();
  }

  @Test
  public void testGet_methodElementFetchedFromMethodScope() {
    Scope fileScope = new FileScope();
    Scope methodScope1 = new MethodScope(fileScope);
    Scope methodScope2 = new MethodScope(fileScope);
    MethodElement key = new MethodElement("foo");
    assertThat(fileScope.get(key)).isNull();
    assertThat(methodScope1.get(key)).isNull();
    assertThat(methodScope2.get(key)).isNull();
    methodScope1.putIfAbsent(key, 1);
    assertThat(fileScope.get(key)).isNull();
    assertThat(methodScope1.get(key)).isEqualTo(1);
    assertThat(methodScope2.get(key)).isNull();
    methodScope1.putIfAbsent(key, 2);
    assertThat(fileScope.get(key)).isNull();
    assertThat(methodScope1.get(key)).isEqualTo(1);
    assertThat(methodScope2.get(key)).isNull();
    methodScope2.putIfAbsent(key, 3);
    assertThat(fileScope.get(key)).isNull();
    assertThat(methodScope1.get(key)).isEqualTo(1);
    assertThat(methodScope2.get(key)).isEqualTo(3);
  }

  @Test
  public void testGet_fileElementFetchedFromFileScope() {
    Scope fileScope = new FileScope();
    Scope methodScope1 = new MethodScope(fileScope);
    Scope methodScope2 = new MethodScope(fileScope);
    FileElement key1 = new FileElement("foo");
    FileElement key2 = new FileElement("bar");
    String value1 = "aString";
    String value2 = "anotherString";

    fileScope.putIfAbsent(key1, value1);
    methodScope1.putIfAbsent(key2, value2);
    assertThat(fileScope.get(key1)).isEqualTo(value1);
    assertThat(fileScope.get(key2)).isEqualTo(value2);
    assertThat(methodScope1.get(key1)).isEqualTo(value1);
    assertThat(methodScope1.get(key2)).isEqualTo(value2);
    assertThat(methodScope2.get(key1)).isEqualTo(value1);
    assertThat(methodScope2.get(key2)).isEqualTo(value2);
  }

  @Test
  public void keysOfType_returnsKeysFromAllScopes() {
    FileScope fileScope = new FileScope();
    Scope methodScope = new MethodScope(fileScope);
    FileElement key1 = new FileElement("foo");
    FileElement key2 = new FileElement("bar");
    MethodElement key3 = new MethodElement("up");
    MethodElement key4 = new MethodElement("down");

    methodScope.putIfAbsent(key1, "something");
    methodScope.putIfAbsent(key2, "something else");
    methodScope.putIfAbsent(key3, 11);
    methodScope.putIfAbsent(key4, 19);

    assertThat(fileScope.keysOfType(FileElement.class)).containsExactly(key1, key2);
    assertThat(fileScope.keysOfType(MethodElement.class)).isEmpty();
    assertThat(methodScope.keysOfType(FileElement.class)).containsExactly(key1, key2);
    assertThat(methodScope.keysOfType(MethodElement.class)).containsExactly(key3, key4);
  }
}
