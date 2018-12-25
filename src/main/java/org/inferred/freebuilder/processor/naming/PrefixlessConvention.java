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

import static javax.tools.Diagnostic.Kind.ERROR;
import static org.inferred.freebuilder.processor.util.ModelUtils.getReturnType;

import org.inferred.freebuilder.processor.Metadata.Property;

import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

class PrefixlessConvention implements NamingConvention {

  PrefixlessConvention(Messager messager, Types types) {
    this.messager = messager;
    this.types = types;
  }

  private final Messager messager;
  private final Types types;

  @Override
  public Optional<Property.Builder> getPropertyNames(
      TypeElement valueType, ExecutableElement method) {
    if (!methodIsAbstractGetter(valueType, method)) {
      return Optional.empty();
    }
    String name = method.getSimpleName().toString();
    String capitalizedName = name.substring(0, 1).toUpperCase() + name.substring(1);
    return Optional.of(new Property.Builder()
        .setUsingBeanConvention(false)
        .setName(name)
        .setCapitalizedName(capitalizedName)
        .setGetterName(name));
  }

  /**
   * Verifies {@code method} is an abstract getter. Any deviations will be logged as an error.
   */
  private boolean methodIsAbstractGetter(TypeElement valueType, ExecutableElement method) {
    Set<Modifier> modifiers = method.getModifiers();
    if (!modifiers.contains(Modifier.ABSTRACT)) {
      return false;
    }
    boolean declaredOnValueType = method.getEnclosingElement().equals(valueType);
    TypeMirror returnType = getReturnType(valueType, method, types);
    if (returnType.getKind() == TypeKind.VOID || !method.getParameters().isEmpty()) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR,
            "Only getter methods may be declared abstract on @FreeBuilder types",
            method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return false;
    }
    return true;
  }

  private void printNoImplementationMessage(TypeElement valueType, ExecutableElement method) {
    messager.printMessage(
        ERROR,
        "No implementation found for non-getter method '" + method + "'; "
            + "cannot generate @FreeBuilder implementation",
        valueType);
  }
}
