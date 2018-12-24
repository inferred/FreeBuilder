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

import com.google.common.collect.Sets;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;

/** Utility class for {@link RoundEnvironment}. */
public class RoundEnvironments {

  /**
   * Sanitizes the result of {@link RoundEnvironment#getElementsAnnotatedWith}, which otherwise
   * can contain elements annotated with annotations of ERROR type.
   *
   * <p>The canonical example is forgetting to import &#64;Nullable.
   */
  public static Set<? extends Element> annotatedElementsIn(
      RoundEnvironment roundEnv, final Class<? extends Annotation> a) {
    return Sets.filter(roundEnv.getElementsAnnotatedWith(a),
        element -> element.getAnnotation(a) != null);
  }

  private RoundEnvironments() { }  // COV_NF_LINE
}
