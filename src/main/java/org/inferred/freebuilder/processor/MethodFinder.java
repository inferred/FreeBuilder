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

import static javax.lang.model.util.ElementFilter.methodsIn;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;

import org.inferred.freebuilder.processor.Analyser.CannotGenerateCodeException;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

/**
 * Static utility method for finding all methods, declared and inherited, on a type.
 */
public class MethodFinder {

  /**
   * Returns all methods, declared and inherited, on {@code type}, except those specified by
   * {@link Object}.
   *
   * <p>If method B overrides method A, only method B will be included in the return set.
   * Additionally, if methods A and B have the same signature, but are on unrelated interfaces,
   * one will be arbitrarily picked to be returned.
   */
  public static ImmutableSet<ExecutableElement> methodsOn(TypeElement type, Elements elements)
      throws CannotGenerateCodeException {
    TypeElement objectType = elements.getTypeElement(Object.class.getCanonicalName());
    SetMultimap<Signature, ExecutableElement> methods = LinkedHashMultimap.create();
    for (TypeElement supertype : getSupertypes(type)) {
      for (ExecutableElement method : methodsIn(supertype.getEnclosedElements())) {
        if (method.getEnclosingElement().equals(objectType)) {
          continue;  // Skip methods specified by Object.
        }
        Signature signature = new Signature(method);
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

  public static ImmutableSet<TypeElement> getSupertypes(TypeElement type)
      throws CannotGenerateCodeException {
    Set<TypeElement> supertypes = new LinkedHashSet<TypeElement>();
    addSupertypesToSet(type, supertypes);
    return ImmutableSet.copyOf(supertypes);
  }

  private static void addSupertypesToSet(TypeElement type, Set<TypeElement> mutableSet)
      throws CannotGenerateCodeException {
    for (TypeMirror iface : type.getInterfaces()) {
      addSupertypesToSet(asTypeElement(iface), mutableSet);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      addSupertypesToSet(asTypeElement(type.getSuperclass()), mutableSet);
    }
    mutableSet.add(type);
  }

  private static TypeElement asTypeElement(TypeMirror iface) throws CannotGenerateCodeException {
    if (iface.getKind() != TypeKind.DECLARED) {
      throw new CannotGenerateCodeException();
    }
    Element element = ((DeclaredType) iface).asElement();
    if (!(element.getKind().isClass() || element.getKind().isInterface())) {
      throw new CannotGenerateCodeException();
    }
    return (TypeElement) element;
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
