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
package org.inferred.freebuilder.processor.util;

public class FieldAccess extends Excerpt implements Scope.Element {

  private static class ExplicitFieldAccess extends Excerpt {
    private final Object obj;
    private final String fieldName;

    ExplicitFieldAccess(Object obj, String fieldName) {
      this.obj = obj;
      this.fieldName = fieldName;
    }

    @Override
    public void addTo(SourceBuilder source) {
      source.add("%s", obj).add(".").add(fieldName);
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      throw new UnsupportedOperationException();
    }
  }

  private final String fieldName;

  public FieldAccess(String fieldName) {
    this.fieldName = fieldName;
  }

  @Override
  public void addTo(SourceBuilder source) {
    if (source.scope().contains(new Variable(fieldName))) {
      source.add("this.");
    } else {
      // Prevent a new variable being declared and obscuring this field access
      source.scope().add(this);
    }
    source.add(fieldName);
  }

  public Excerpt on(Object obj) {
    return new ExplicitFieldAccess(obj, fieldName);
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("fieldName", fieldName);
  }
}
