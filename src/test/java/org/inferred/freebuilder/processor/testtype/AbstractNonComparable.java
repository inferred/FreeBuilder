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

import java.util.Comparator;

public abstract class AbstractNonComparable {

  public abstract int id();

  public static final class ReverseIdComparator implements Comparator<AbstractNonComparable> {
    @Override
    public int compare(AbstractNonComparable o1, AbstractNonComparable o2) {
      return Integer.compare(o2.id(), o1.id());
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof ReverseIdComparator);
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }
}
