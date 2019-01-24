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

import org.inferred.freebuilder.processor.source.Scope.Level;

/**
 * Maps Java identifiers to their usage (e.g. a parameter, a variable) in the current method scope.
 */
class IdKey extends ValueType implements Scope.Key<Object> {

  private final String name;

  IdKey(String name) {
    this.name = name;
  }

  String name() {
    return name;
  }

  @Override
  public Level level() {
    return Level.METHOD;
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("name", name);
  }
}
