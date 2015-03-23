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
package org.inferred.freebuilder.processor;

import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;

import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

/** Standard ways of constructing a default Builder. */
public enum BuilderFactory {

  /** A new Builder can be made by calling the class' no-args constructor. */
  NO_ARGS_CONSTRUCTOR {
    @Override public void addNewBuilder(
        SourceBuilder code,
        ParameterizedType builderType) {
      code.add("%s()", builderType.constructor());
    }
  },

  /** The enclosing class provides a static builder() factory method. */
  BUILDER_METHOD {
    @Override public void addNewBuilder(
        SourceBuilder code,
        ParameterizedType builderType) {
      code.add("%s.%sbuilder()",
          builderType.getQualifiedName().getEnclosingType(), builderType.typeParameters());
    }
  },

  /** The enclosing class provides a static newBuilder() factory method. */
  NEW_BUILDER_METHOD {
    @Override public void addNewBuilder(
        SourceBuilder code,
        ParameterizedType builderType) {
      code.add("%s.%snewBuilder()",
          builderType.getQualifiedName().getEnclosingType(), builderType.typeParameters());
    }
  };

  /** Determines the correct way of constructing a default {@code builderType} instance, if any. */
  public static Optional<BuilderFactory> from(TypeElement builderType) {
    ImmutableSet<String> staticMethods = findPotentialStaticFactoryMethods(builderType);
    if (staticMethods.contains("builder")) {
      return Optional.of(BUILDER_METHOD);
    } else if (staticMethods.contains("newBuilder")) {
      return Optional.of(NEW_BUILDER_METHOD);
    } else if (hasExplicitNoArgsConstructor(builderType)) {
      return Optional.of(NO_ARGS_CONSTRUCTOR);
    } else {
      return Optional.absent();
    }
  }

  /** Adds a code snippet calling the Builder factory method. */
  public abstract void addNewBuilder(SourceBuilder code, ParameterizedType builderType);

  private static boolean hasExplicitNoArgsConstructor(TypeElement type) {
    for (ExecutableElement constructor : constructorsIn(type.getEnclosedElements())) {
      if (constructor.getParameters().isEmpty()
          && !constructor.getModifiers().contains(Modifier.PRIVATE)) {
        return true;
      }
    }
    return false;
  }

  private static ImmutableSet<String> findPotentialStaticFactoryMethods(TypeElement builderType) {
    ImmutableSet.Builder<String> resultBuilder = ImmutableSet.builder();
    Element valueType = builderType.getEnclosingElement();
    for (ExecutableElement method : methodsIn(valueType.getEnclosedElements())) {
      Set<Modifier> modifiers = method.getModifiers();
      if (modifiers.contains(Modifier.STATIC)
          && !modifiers.contains(Modifier.PRIVATE)
          && method.getParameters().isEmpty()
          && builderType.asType().toString().equals(method.getReturnType().toString())) {
        resultBuilder.add(method.getSimpleName().toString());
      }
    }
    return resultBuilder.build();
  }
}
