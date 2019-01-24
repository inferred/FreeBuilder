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

import static org.inferred.freebuilder.processor.util.IsInvalidTypeVisitor.isLegalType;

import static javax.tools.Diagnostic.Kind.ERROR;

import org.inferred.freebuilder.processor.property.Property;
import org.inferred.freebuilder.processor.util.ModelUtils;

import java.beans.Introspector;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

class BeanConvention implements NamingConvention {

  /**
   * Regular expression matching bean-convention method names.
   *
   * <p>We deviate slightly from the JavaBean convention by insisting that there must be a
   * non-lowercase character immediately following the get/is prefix; this prevents ugly cases like
   * 'get()' or 'getter()'.
   */
  static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is)([^\\p{javaLowerCase}].*)");
  static final String GET_PREFIX = "get";
  static final String IS_PREFIX = "is";

  BeanConvention(Messager messager, Types types) {
    this.messager = messager;
    this.types = types;
  }

  private final Messager messager;
  private final Types types;

  /**
   * Verifies {@code method} is an abstract getter following the JavaBean convention. Any
   * deviations will be logged as an error.
   */
  @Override
  public Optional<Property.Builder> getPropertyNames(
      TypeElement valueType, ExecutableElement method) {
    boolean declaredOnValueType = method.getEnclosingElement().equals(valueType);
    String name = method.getSimpleName().toString();
    Matcher getterMatcher = GETTER_PATTERN.matcher(name);
    if (!getterMatcher.matches()) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR,
            "Only getter methods (starting with '" + GET_PREFIX
                + "' or '" + IS_PREFIX + "') may be declared abstract on FreeBuilder types",
            method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return Optional.empty();
    }
    String prefix = getterMatcher.group(1);
    String capitalizedName = getterMatcher.group(2);
    if (hasUpperCase(capitalizedName.codePointAt(0))) {
      if (declaredOnValueType) {
        String message = new StringBuilder()
            .append("Getter methods cannot have a lowercase character immediately after the '")
            .append(prefix)
            .append("' prefix on FreeBuilder types (did you mean '")
            .append(prefix)
            .appendCodePoint(Character.toUpperCase(capitalizedName.codePointAt(0)))
            .append(capitalizedName.substring(capitalizedName.offsetByCodePoints(0, 1)))
            .append("'?)")
            .toString();
        messager.printMessage(ERROR, message, method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return Optional.empty();
    }
    TypeMirror returnType = ModelUtils.getReturnType(valueType, method, types);
    if (returnType.getKind() == TypeKind.VOID) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR, "Getter methods must not be void on FreeBuilder types", method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return Optional.empty();
    }
    if (prefix.equals(IS_PREFIX) && (returnType.getKind() != TypeKind.BOOLEAN)) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR,
            "Getter methods starting with '" + IS_PREFIX
                + "' must return a boolean on FreeBuilder types",
            method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return Optional.empty();
    }
    if (!method.getParameters().isEmpty()) {
      if (declaredOnValueType) {
        messager.printMessage(
            ERROR, "Getter methods cannot take parameters on FreeBuilder types", method);
      } else {
        printNoImplementationMessage(valueType, method);
      }
      return Optional.empty();
    }
    if (!isLegalType(returnType)) {
      // The compiler should already have issued an error.
      return Optional.empty();
    }

    String camelCaseName = Introspector.decapitalize(capitalizedName);
    return Optional.of(new Property.Builder()
        .setUsingBeanConvention(true)
        .setName(camelCaseName)
        .setCapitalizedName(capitalizedName)
        .setGetterName(getterMatcher.group(0)));
  }

  private static boolean hasUpperCase(int codepoint) {
    return Character.toUpperCase(codepoint) != codepoint;
  }

  private void printNoImplementationMessage(TypeElement valueType, ExecutableElement method) {
    messager.printMessage(
        ERROR,
        "No implementation found for non-getter method '" + method + "'; "
            + "cannot generate FreeBuilder implementation",
        valueType);
  }

}
