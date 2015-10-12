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

/**
 * Utility methods related to &#64;FreeBuilder dependencies being relocated as part of shading.
 */
public class Shading {

  private static final String SHADE_PACKAGE = "org.inferred.freebuilder.shaded.";

  public static String unshadedName(String qualifiedName) {
    if (qualifiedName.startsWith(Shading.SHADE_PACKAGE)) {
      return qualifiedName.substring(Shading.SHADE_PACKAGE.length());
    } else {
      return qualifiedName;
    }
  }
}
