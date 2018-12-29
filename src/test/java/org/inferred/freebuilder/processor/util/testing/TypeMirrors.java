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
package org.inferred.freebuilder.processor.util.testing;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/** Static utility methods pertaining to {@link TypeMirror} instances. */
public class TypeMirrors {

  /** e.g. '%1' */
  private static final Pattern ARG_REF_PATTERN = Pattern.compile("%(\\d+)");
  /** e.g. '%1[]' or '%1<%2,%3>' */
  private static final Pattern GENERIC_OR_ARRAY_PATTERN = Pattern.compile(
      "(%\\d+)\\s*(?:(\\[\\s*\\])|<\\s*((?:%\\d+)\\s*(?:,\\s*(?:%\\d+)\\s*)*)>)");
  /**  e.g. '><', '][' or ']<' */
  private static final Pattern INVALID_TYPE_SNIPPET_PATTERN = Pattern.compile(
      ">\\s*[\\w<]|\\]\\s*[\\w<\\[]");
  /** e.g. 'java.lang.String' */
  private static final Pattern RAW_TYPE_PATTERN = Pattern.compile(
      "[^\\W\\d]\\w*(\\s*[.]\\s*[^\\W\\d]\\w*)*");

  /** Returns a {@link TypeMirror} for the given class (raw T, not T&lt;?&gt;, if T is generic). */
  public static TypeMirror typeMirror(
      Types typeUtils,
      Elements elementUtils,
      Class<?> cls) {
    if (cls.equals(void.class)) {
      return typeUtils.getNoType(TypeKind.VOID);
    } else if (cls.isPrimitive()) {
      return typeUtils.getPrimitiveType(TypeKind.valueOf(cls.getSimpleName().toUpperCase()));
    } else if (cls.isArray()) {
      return typeUtils.getArrayType(typeMirror(typeUtils, elementUtils, cls.getComponentType()));
    } else {
      return rawType(typeUtils, elementUtils, cls.getCanonicalName());
    }
  }

  /** Returns a {@link TypeMirror} for the given type. */
  public static TypeMirror typeMirror(Types typeUtils, Elements elementUtils, TypeToken<?> type) {
    return typeMirror(typeUtils, elementUtils, type.getType());
  }

  /** Returns a {@link TypeMirror} for the given type. */
  public static TypeMirror typeMirror(Types typeUtils, Elements elementUtils, Type type) {
    if (type instanceof Class) {
      return typeMirror(typeUtils, elementUtils, (Class<?>) type);
    } else if (type instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) type).getGenericComponentType();
      return typeUtils.getArrayType(typeMirror(typeUtils, elementUtils, componentType));
    } else if (type instanceof ParameterizedType) {
      ParameterizedType pType = (ParameterizedType) type;
      DeclaredType rawType = (DeclaredType) typeMirror(typeUtils, elementUtils, pType.getRawType());
      List<TypeMirror> typeArgumentMirrors = new ArrayList<TypeMirror>();
      for (Type typeArgument : pType.getActualTypeArguments()) {
        typeArgumentMirrors.add(typeMirror(typeUtils, elementUtils, typeArgument));
      }
      DeclaredType owner = (DeclaredType) typeMirror(typeUtils, elementUtils, pType.getOwnerType());
      return typeUtils.getDeclaredType(
          owner,
          (TypeElement) rawType.asElement(),
          typeArgumentMirrors.toArray(new TypeMirror[typeArgumentMirrors.size()]));
    } else if (type instanceof WildcardType) {
      Type lowerBound = getOnlyType(((WildcardType) type).getLowerBounds());
      Type upperBound = getOnlyType(((WildcardType) type).getUpperBounds());
      if (Object.class.equals(upperBound)) {
        upperBound = null;
      }
      return typeUtils.getWildcardType(
          typeMirror(typeUtils, elementUtils, upperBound),
          typeMirror(typeUtils, elementUtils, lowerBound));
    } else if (type == null) {
      return null;
    } else if (type instanceof TypeVariable) {
      throw new IllegalArgumentException("Type variables not supported");
    } else {
      throw new IllegalArgumentException("Unrecognized Type subclass " + type.getClass());
    }
  }

  /**
   * Returns a {@link TypeMirror} for the given type, substituting any provided arguments for
   * %1, %2, etc.
   *
   * <p>e.g. {@code typeMirror(types, elements, "java.util.List<%1>",
   * typeMirror(types, elements, String.class))} will return the same thing as
   * {@code typeMirror(types, elements, "java.util.List<java.lang.String>")}
   *
   * @param typeUtils an implementation of {@link Types}
   * @param elementUtils an implementation of {@link Elements}
   * @param typeSnippet the type, represented as a snippet of Java code, e.g.
   *     {@code "java.lang.String"}, {@code "java.util.Map<%1, %2>"}
   * @param args existing {@link TypeMirror} instances to be substituted into the type
   */
  public static TypeMirror typeMirror(
      Types typeUtils,
      Elements elementUtils,
      String typeSnippet,
      TypeMirror... args) {
    checkArgReferences(typeSnippet, (args == null) ? 0 : args.length);

    // Check for illegal patterns that the substitution algorithm may invalidly accept
    Preconditions.checkArgument(
        !INVALID_TYPE_SNIPPET_PATTERN.matcher(typeSnippet).find(),
        "Invalid type string '%s'", typeSnippet);

    Substitutions substitutions = new Substitutions(args);
    MutableString mutableSnippet = new MutableString(typeSnippet.trim());
    substituteRawTypes(typeUtils, elementUtils, mutableSnippet, substitutions);
    substituteGenericsAndArrays(typeUtils, mutableSnippet, substitutions);

    // Either we have only a '%d' left, or the input was invalid
    Preconditions.checkArgument(
        substitutions.containsKey(mutableSnippet.toString()),
        "Invalid type string '%s'", typeSnippet);
    return substitutions.get(mutableSnippet.toString());
  }

  private static Type getOnlyType(Type[] types) {
    checkArgument(types.length <= 1, "Wildcard types with multiple bounds not supported");
    return (types.length == 0) ? null : types[0];
  }

  /**
   * Evaluate raw types, and substitute new %d strings in their place.
   *
   * <p>e.g. {@code "Map<String,List<Integer>>"} &#x27fc; {@code "%1<%2,%3<%4>>"}
   */
  private static void substituteRawTypes(
      Types typeUtils,
      Elements elementUtils,
      MutableString snippet,
      Substitutions substitutions) {
    for (MatchResult m : snippet.instancesOf(RAW_TYPE_PATTERN)) {
      snippet.replace(m, substitutions.put(rawType(typeUtils, elementUtils, m.group(0))));
    }
  }

  /**
   * Evaluate generics and arrays depth-first, and substitute %d strings in their place.
   *   e.g.  %1<%2,%3<%4>>  -->  %1<%2,%5>  -->  %6
   */
  private static void substituteGenericsAndArrays(
      Types typeUtils,
      MutableString snippet,
      Substitutions substitutions) {
    for (MatchResult m : snippet.instancesOf(GENERIC_OR_ARRAY_PATTERN)) {
      // Group 1 is the type on the left, e.g. '%1' in '%1<%2,%5>'
      // Group 2 contains the array brackets if this is an array, e.g. '[]' in '%1[]'
      // Group 3 contains the type list if this is a generic type, e.g. '%2,%5' in '%1<%2,%5>'
      TypeMirror type = substitutions.get(m.group(1));
      if (Strings.isNullOrEmpty(m.group(2))) {
        List<TypeMirror> argTypes = Lists.transform(
            Splitter.on(",").trimResults().splitToList(m.group(3)),
            substitutions.asFunction());
        snippet.replace(m, substitutions.put(parameterisedType(typeUtils, type, argTypes)));
      } else {
        snippet.replace(m, substitutions.put(typeUtils.getArrayType(type)));
      }
    }
  }

  /**
   * Returns a parameterised generic type.
   *
   * @throws IllegalArgumentException if {@code rawType} is not in fact a raw type, or if
   *     the number of given parameters does not match the number declared on the raw type.
   */
  private static DeclaredType parameterisedType(
      Types typeUtils,
      TypeMirror rawType,
      List<TypeMirror> paramTypes) {
    Preconditions.checkArgument(
        rawType.getKind() == TypeKind.DECLARED
            && ((DeclaredType) rawType).getTypeArguments().isEmpty(),
        "Expected raw type, got '%s'",
        rawType);
    TypeElement genericType = (TypeElement) typeUtils.asElement(rawType);
    Preconditions.checkArgument(
        genericType.getTypeParameters().size() == paramTypes.size(),
        "Incorrect number of arguments for %s (expected %s, got %s)",
        genericType,
        genericType.getTypeParameters().size(),
        paramTypes.size());
    DeclaredType declaredType = typeUtils.getDeclaredType(
        genericType, paramTypes.toArray(new TypeMirror[paramTypes.size()]));
    return declaredType;
  }

  /** Checks that all %d references in the given type snippet are within bounds. */
  private static void checkArgReferences(String typeSnippet, int numberOfArgs) {
    Matcher argRefMatcher = ARG_REF_PATTERN.matcher(typeSnippet);
    while (argRefMatcher.find()) {
      int index = Integer.parseInt(argRefMatcher.group(1), 10) - 1;
      Preconditions.checkArgument(index >= 0,
          "%s not allowed, indices start at 1", argRefMatcher.group(0));
      Preconditions.checkArgument(index < numberOfArgs,
          "%s too large for number of provided type mirrors", argRefMatcher.group(0));
    }
  }

  private static TypeMirror rawType(
      Types typeUtils,
      Elements elementUtils,
      String typeSnippet) {
    TypeElement typeElement = elementUtils.getTypeElement(typeSnippet);
    if (typeElement == null && !typeSnippet.contains(".")) {
      typeElement = elementUtils.getTypeElement("java.lang." + typeSnippet);
    }
    Preconditions.checkArgument(typeElement != null, "Unrecognised type '%s'", typeSnippet);
    return typeUtils.erasure(typeElement.asType());
  }

  /** Stores a mutable string, with easy regular expression search-and-replacement. */
  private static class MutableString {
    private String value;

    MutableString(String value) {
      this.value = Preconditions.checkNotNull(value);
    }

    /** Iterates through instances of the given pattern, resetting every time the string mutates. */
    Iterable<MatchResult> instancesOf(Pattern pattern) {
      return new Iterable<MatchResult>() {
        @Override public Iterator<MatchResult> iterator() {
          return new AbstractIterator<MatchResult>() {
            Matcher matcher;
            String matchingAgainst;

            @Override protected MatchResult computeNext() {
              if (matchingAgainst != value) {
                matchingAgainst = value;
                matcher = pattern.matcher(matchingAgainst);
              }
              if (matcher.find()) {
                return matcher.toMatchResult();
              }
              return endOfData();
            }
          };
        }
      };
    }

    /** Replaces the given match with the given replacement string. */
    void replace(MatchResult match, String replacement) {
      Preconditions.checkArgument(
          match.group().equals(value.substring(match.start(), match.end())),
          "MatchResult does not match the current value of this string");
      value = value.substring(0, match.start()) + replacement + value.substring(match.end());
    }

    /** Returns this mutable string's current value. */
    @Override
    public String toString() {
      return value;
    }
  }

  /** Stores substitute strings of the form %d, mapped to the TypeMirrors that they represent. */
  private static class Substitutions {
    private final Map<String, TypeMirror> substitutions = Maps.newHashMap();

    Substitutions(TypeMirror... args) {
      for (int i = 0; args != null && i < args.length; ++i) {
        substitutions.put("%" + (i + 1), args[i]);
      }
    }

    boolean containsKey(String substitute) {
      return substitutions.containsKey(substitute);
    }

    TypeMirror get(String substitute) {
      return substitutions.get(substitute);
    }

    Function<String, TypeMirror> asFunction() {
      return Functions.forMap(substitutions);
    }

    String put(TypeMirror t) {
      String pattern = "%" + (substitutions.size() + 1);
      substitutions.put(pattern, t);
      return pattern;
    }
  }
}
