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

import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * Static utility method for finding all methods, declared and inherited, on a type.
 */
public class MethodFinder {

  @FunctionalInterface
  public interface ErrorTypeHandling<E extends Exception> {
    void handleErrorType(ErrorType type) throws E;
  }

  /**
   * Returns all methods, declared and inherited, on {@code type}, except those specified by
   * {@link Object}.
   *
   * <p>If method B overrides method A, only method B will be included in the return set.
   * Additionally, if methods A and B have the same signature, but are on unrelated interfaces,
   * one will be arbitrarily picked to be returned.
   */
  public static <E extends Exception> ImmutableSet<ExecutableElement> methodsOn(
      TypeElement type,
      Elements elements,
      ErrorTypeHandling<E> errorTypeHandling) throws E {
    TypeElement objectType = elements.getTypeElement(Object.class.getCanonicalName());
    Map<Signature, ExecutableElement> objectMethods = Maps.uniqueIndex(
        methodsIn(objectType.getEnclosedElements()), Signature::new);
    SetMultimap<Signature, ExecutableElement> methods = LinkedHashMultimap.create();
    for (TypeElement supertype : getSupertypes(type, errorTypeHandling)) {
      for (ExecutableElement method : methodsIn(supertype.getEnclosedElements())) {
        Signature signature = new Signature(method);
        if (method.getEnclosingElement().equals(objectType)) {
          continue;  // Skip methods specified by Object.
        }
        if (objectMethods.containsKey(signature)
            && method.getEnclosingElement().getKind() == ElementKind.INTERFACE
            && method.getModifiers().contains(Modifier.ABSTRACT)
            && elements.overrides(method, objectMethods.get(signature), type)) {
          continue;  // Skip abstract methods on interfaces redelaring Object methods.
        }
        Iterator<ExecutableElement> iterator = methods.get(signature).iterator();
        while (iterator.hasNext()) {
          ExecutableElement otherMethod = iterator.next();
          if (elements.overrides(method, otherMethod, type)
              || method.getParameters().equals(otherMethod.getParameters())) {
            iterator.remove();
          }
        }
        methods.put(signature, method);
      }
    }
    return ImmutableSet.copyOf(methods.values());
  }

  private static <E extends Exception> ImmutableSet<TypeElement> getSupertypes(
      TypeElement type,
      ErrorTypeHandling<E> errorTypeHandling) throws E {
    Set<TypeElement> supertypes = new LinkedHashSet<>();
    addSupertypesToSet(type, supertypes, errorTypeHandling);
    return ImmutableSet.copyOf(supertypes);
  }

  private static <E extends Exception> void addSupertypesToSet(
      TypeElement type,
      Set<TypeElement> mutableSet,
      ErrorTypeHandling<E> errorTypeHandling) throws E {
    for (TypeMirror iface : type.getInterfaces()) {
      TypeElement typeElement = maybeTypeElement(iface, errorTypeHandling).orElse(null);
      if (typeElement != null) {
        addSupertypesToSet(typeElement, mutableSet, errorTypeHandling);
      }
    }
    TypeElement superclassElement =
        maybeTypeElement(type.getSuperclass(), errorTypeHandling).orElse(null);
    if (superclassElement != null) {
      addSupertypesToSet(superclassElement, mutableSet, errorTypeHandling);
    }
    mutableSet.add(type);
  }

  private static <E extends Exception> Optional<TypeElement> maybeTypeElement(
      TypeMirror mirror, ErrorTypeHandling<E> errorTypeHandling) throws E {
    if (mirror.getKind() == TypeKind.ERROR) {
      errorTypeHandling.handleErrorType((ErrorType) mirror);
    }
    return ModelUtils.maybeAsTypeElement(mirror);
  }

  /**
   * Key type. Two methods with different {@code Signature}s will never return true when passed to
   * {@link Elements#overrides}.
   */
  private static class Signature {

    final Name name;
    final int params;

    Signature(ExecutableElement method) {
      name = method.getSimpleName();
      params = method.getParameters().size();
    }

    @Override
    public int hashCode() {
      return name.hashCode() * 31 + params;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Signature)) {
        return false;
      }
      Signature other = (Signature) obj;
      return (name.equals(other.name) && params == other.params);
    }
  }

  private MethodFinder() {}
}
