/*
 * Copyright 2018 Google Inc. All rights reserved.
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

import org.inferred.freebuilder.processor.util.Scope.Level;

public class Variable extends ValueType implements Excerpt, Scope.Element<IdKey> {

  private final String preferredName;

  public Variable(String preferredName) {
    this.preferredName = preferredName;
  }

  @Override
  public Level level() {
    return Level.METHOD;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public void addTo(SourceBuilder code) {
    IdKey name = code.scope().computeIfAbsent(this, () -> new IdKey(pickName(code)));
    code.add("%s", name.name());
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("preferredName", preferredName);
  }

  private String pickName(SourceBuilder code) {
    if (registerName(code, preferredName)) {
      return preferredName;
    }
    if (registerName(code, "_" + preferredName)) {
      return "_" + preferredName;
    }
    int suffix = 2;
    while (!registerName(code, "_" + preferredName + suffix)) {
      suffix++;
    }
    return "_" + preferredName + suffix;
  }

  private boolean registerName(SourceBuilder code, String name) {
    return code.scope().putIfAbsent(new IdKey(name), this) == null;
  }
}
