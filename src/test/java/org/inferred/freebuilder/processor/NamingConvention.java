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
package org.inferred.freebuilder.processor;

public enum NamingConvention {
  PREFIXLESS("prefixless") {
    @Override
    String accessor(String prefix, String fieldName) {
      return fieldName;
    }
  },
  BEAN("bean") {
    @Override
    String accessor(String prefix, String fieldName) {
      return prefix + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }
  };

  private final String name;

  NamingConvention(String name) {
    this.name = name;
  }

  public String getter() {
    return getter("items");
  }

  public String setter(String fieldName) {
    return accessor("set", fieldName);
  }

  public String getter(String fieldName) {
    return accessor("get", fieldName) + "()";
  }

  abstract String accessor(String prefix, String fieldName);

  @Override
  public String toString() {
    return name;
  }
}
