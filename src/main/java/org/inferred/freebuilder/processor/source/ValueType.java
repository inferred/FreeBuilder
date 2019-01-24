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
package org.inferred.freebuilder.processor.source;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an instance that compares in {@link Object#hashCode()} and
 * {@link Object#equals(Object)} using a sequence of fields. If two instances have the same runtime
 * class and the same sequence of fields, then they are considered equal.
 *
 * <p>This class implements the two {@link Object} methods mentioned above as well as
 * {@link Object#toString()}.
 */
public abstract class ValueType {
  /**
   * An object that receives fields (names and current values) for processing. It is assumed that
   * for a given class, the order of fields is always the same, although individual fields may be
   * omitted.
   */
  public interface FieldReceiver {
    void add(String name, Object value);
  }

  /** A receiver that adds all received field names and values into a list in order. */
  private static final class ReceiverIntoList implements FieldReceiver {
    private final List<Object> list = new ArrayList<>();

    @Override
    public void add(String name, Object value) {
      list.add(name);
      list.add(value);
    }

    public List<Object> get() {
      return list;
    }
  }

  /** A receiver that uses each received name and value to calculate a hash code. */
  private static final class ReceiverIntoHashCode implements FieldReceiver {
    private int hashCode = 1;

    private void add(Object value) {
      hashCode *= 31;
      hashCode += (value == null) ? 0 : value.hashCode();
    }

    @Override
    public void add(String name, Object value) {
      add(name);
      add(value);
    }

    public int get() {
      return hashCode;
    }
  }

  /**
   * A receiver that puts each name and value into a {@link StringBuilder} that generates a human-
   * readable representation of the value.
   */
  private static final class ReceiverIntoStringBuilder implements FieldReceiver {
    private final StringBuilder builder;
    private String delimiter;

    ReceiverIntoStringBuilder(StringBuilder builder) {
      this.builder = builder;
      this.delimiter = "";
    }

    @Override
    public void add(String name, Object value) {
      builder.append(delimiter);
      delimiter = ", ";
      builder.append(name).append("=").append(value);
    }
  }

  /** Implement this method to report the name and value of each field. */
  protected abstract void addFields(FieldReceiver fields);

  @Override
  public boolean equals(Object obj) {
    if ((obj == null) || (obj.getClass() != this.getClass())) {
      return false;
    }
    ReceiverIntoList a = new ReceiverIntoList();
    ReceiverIntoList b = new ReceiverIntoList();
    this.addFields(a);
    ((ValueType) obj).addFields(b);
    return a.get().equals(b.get());
  }

  @Override
  public int hashCode() {
    ReceiverIntoHashCode receiver = new ReceiverIntoHashCode();
    addFields(receiver);
    return receiver.get();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(getClass().getSimpleName())
        .append("{");

    addFields(new ReceiverIntoStringBuilder(builder));

    return builder.append("}")
        .toString();
  }
}

