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
package org.inferred.freebuilder.processor.testtype;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class NonComparable extends AbstractNonComparable {

  private final int id;
  private final String name;

  @JsonCreator
  public NonComparable(@JsonProperty("id") int id, @JsonProperty("name") String name) {
    this.id = requireNonNull(id);
    this.name = requireNonNull(name);
  }

  @Override
  @JsonProperty("id")
  public int id() {
    return id;
  }

  @JsonProperty("name")
  public String name() {
    return name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof NonComparable)) {
      return false;
    }
    NonComparable other = (NonComparable) obj;
    return (id == other.id) && (name.equals(other.name));
  }

  @Override
  public String toString() {
    return "NonComparable [id=" + id + ", name=" + name + "]";
  }
}
