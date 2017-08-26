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

import static com.google.common.collect.Sets.newLinkedHashSet;

import java.util.Set;

public interface Scope {

  interface Element {}

  boolean contains(Element element);

  void add(Element element);

  class FileScope implements Scope {

    @Override
    public boolean contains(Element element) {
      return false;
    }

    @Override
    public void add(Element element) {
    }
  }

  class MethodScope implements Scope {

    private final Scope parent;
    private final Set<Element> elements;

    public MethodScope(Scope parent) {
      this.parent = parent;
      this.elements = newLinkedHashSet();
    }

    @Override
    public boolean contains(Element element) {
      return elements.contains(element) || parent.contains(element);
    }

    @Override
    public void add(Element element) {
      elements.add(element);
    }
  }
}
