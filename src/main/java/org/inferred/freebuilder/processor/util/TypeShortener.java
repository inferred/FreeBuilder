/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import java.io.IOException;

/**
 * Produces type references for use in source code.
 */
interface TypeShortener {

  void appendShortened(Appendable a, QualifiedName type) throws IOException;

  /** A {@link TypeShortener} that always shortens types, even if that causes conflicts. */
  class AlwaysShorten implements TypeShortener {
    @Override
    public void appendShortened(Appendable a, QualifiedName type) throws IOException {
      String separator = "";
      for (String simpleName : type.getSimpleNames()) {
        a.append(separator).append(simpleName);
        separator = ".";
      }
    }
  }
}
