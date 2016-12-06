/*
 * Copyright 2016 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.naming;

import static com.google.common.base.MoreObjects.firstNonNull;
import static javax.tools.Diagnostic.Kind.ERROR;
import static org.inferred.freebuilder.processor.naming.BeanConvention.GETTER_PATTERN;
import static org.inferred.freebuilder.processor.naming.BeanConvention.GET_PREFIX;

import java.util.regex.Matcher;

import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;

public class NamingConventions {

  /**
   * Determine whether the user has followed bean-like naming convention or not.
   */
  public static NamingConvention determineNamingConvention(
      TypeElement type,
      Iterable<ExecutableElement> methods,
      Messager messager,
      Types types) {
    ExecutableElement beanMethod = null;
    ExecutableElement prefixlessMethod = null;
    for (ExecutableElement method : methods) {
      switch (methodNameConvention(method)) {
      case BEAN:
        beanMethod = firstNonNull(beanMethod, method);
        break;
      case PREFIXLESS:
        prefixlessMethod = firstNonNull(prefixlessMethod, method);
        break;
      default:
        break;
      }
    }
    if (prefixlessMethod != null) {
      if (beanMethod != null) {
        messager.printMessage(
            ERROR,
            "Type contains an illegal mix of get-prefixed and unprefixed getter methods, e.g. '"
                + beanMethod.getSimpleName() + "' and '" + prefixlessMethod.getSimpleName() + "'",
                type);
      }
      return new PrefixlessConvention(messager, types);
    } else {
      return new BeanConvention(messager, types);
    }
  }

  private enum Convention {
    BEAN, PREFIXLESS, UNKNOWN;
  }

  private static Convention methodNameConvention(ExecutableElement method) {
    String name = method.getSimpleName().toString();
    Matcher getterMatcher = GETTER_PATTERN.matcher(name);
    if (!getterMatcher.matches()) {
      return Convention.PREFIXLESS;
    }
    String prefix = getterMatcher.group(1);
    if (prefix.equals(GET_PREFIX)) {
      return Convention.BEAN;
    } else {
      return Convention.UNKNOWN;
    }
  }

  private NamingConventions() {}
}
