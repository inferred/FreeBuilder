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

import com.google.common.testing.EqualsTester;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ValueTypeTest {
  private final class Empty extends ValueType {
    @Override
    protected void addFields(FieldReceiver fields) { }
  }

  private final class Name extends ValueType {
    private final String family;
    private final String given;

    Name(String family, String given) {
      this.family = family;
      this.given = given;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("family", family);
      fields.add("given", given);
    }
  }

  private final class PairThatOmitsNull extends ValueType {
    private final Object first;
    private final Object second;

    PairThatOmitsNull(Object first, Object second) {
      this.first = first;
      this.second = second;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      if (first != null) {
        fields.add("first", first);
      }
      if (second != null) {
        fields.add("second", second);
      }
    }
  }

  @Test
  public void toString_noValues() {
    assertEquals("Empty{}", new Empty().toString());
  }

  @Test
  public void toString_basic() {
    assertEquals("Name{family=Doe, given=John}", new Name("Doe", "John").toString());
  }

  @Test
  public void toString_hasNull() {
    assertEquals("Name{family=Doe, given=null}", new Name("Doe", null).toString());
  }

  @Test
  public void hashAndEquals() {
    EqualsTester tester = new EqualsTester();
    tester.addEqualityGroup(new Empty(), new Empty());
    tester.addEqualityGroup(new Name("Foo", "Bar"), new Name("Foo", "Bar"));
    tester.addEqualityGroup(new Name("Foo", "NotBar"));
    tester.addEqualityGroup(new Name("NotFoo", "Bar"));
    tester.addEqualityGroup(new Name("Foo", null), new Name("Foo", null));
    tester.addEqualityGroup(new Name(null, "Bar"), new Name(null, "Bar"));
    tester.addEqualityGroup(new PairThatOmitsNull(null, 42), new PairThatOmitsNull(null, 42));
    tester.addEqualityGroup(new PairThatOmitsNull(42, null), new PairThatOmitsNull(42, null));
    tester.addEqualityGroup(new PairThatOmitsNull(42, 42), new PairThatOmitsNull(42, 42));
    tester.testEquals();
  }
}

