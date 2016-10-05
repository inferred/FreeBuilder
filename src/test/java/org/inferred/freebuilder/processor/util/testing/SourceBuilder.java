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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Throwables;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

/** Simple builder API for an in-memory {@link JavaFileObject}.  */
public class SourceBuilder {

  private static final Pattern TYPE_NAME_PATTERN =
      Pattern.compile("(class|[@]?interface|enum)\\s+(\\w+)");
  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("package\\s+(\\w+(\\s*\\.\\s*\\w+)*)\\s*;");

  private String name;
  private final StringBuilder code = new StringBuilder();

  public SourceBuilder named(String name) {
    this.name = name;
    return this;
  }

  /**
   * Appends a formatted line of code to the source. Formatting is done by {@link String#format},
   * except that {@link Class} instances use their entity's name unadorned, rather than the usual
   * toString implementation.
   */
  public SourceBuilder addLine(String fmt, Object... args) {
    Object[] substituteArgs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      substituteArgs[i] = substitute(args[i]);
    }
    code.append(String.format(fmt, substituteArgs)).append("\n");
    return this;
  }

  /**
   * Returns a {@link JavaFileObject} for the source added to the builder.
   */
  public JavaFileObject build() {
    String source = code.toString();
    String typeName = getTypeNameFromSource(source);
    URI uri = uriForClass(typeName);
    return new Source(source, firstNonNull(name, typeName), uri);
  }

  /** Substitutes the given object with one that has a better toString() for code generation. */
  static Object substitute(Object arg) {
    if (arg instanceof Class<?>) {
      return ((Class<?>) arg).getCanonicalName();
    } else {
      return arg;
    }
  }

  /** Parses the given source code and returns the name of the type it defines. */
  static String getTypeNameFromSource(CharSequence source) {
    Matcher packageMatcher = SourceBuilder.PACKAGE_PATTERN.matcher(source);
    Matcher typeNameMatcher = SourceBuilder.TYPE_NAME_PATTERN.matcher(source);
    checkArgument(packageMatcher.find(), "Source contains no package definition");
    checkArgument(typeNameMatcher.find(), "Source contains no type definition");
    String typeName = packageMatcher.group(1) + "." + typeNameMatcher.group(2);
    typeName = typeName.replaceAll("\\s+", "");
    return typeName;
  }

  /** Returns a dummy URI for the given type name. */
  static URI uriForClass(String typeName) {
    try {
      return new URI("mem:///" + typeName.replaceAll("\\.", "/") + ".java");
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }
  }

  /** Simple in-memory implementation of {@link javax.tools.JavaFileObject JavaFileObject}. */
  private static class Source extends SimpleJavaFileObject {

    private final String name;
    private final String content;

    /**
     * Creates a new {@link javax.tools.JavaFileObject JavaFileObject} containing the supplied
     * source code. File name is derived from the source code's package and type name.
     */
    Source(String source, String name, URI uri) {
      super(uri, Kind.SOURCE);
      this.content = source;
      this.name = name;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return content;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public int hashCode() {
      return Objects.hash(Source.class, content);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Source)) {
        return false;
      }
      Source other = (Source) obj;
      return Objects.equals(content, other.content);
    }
  }
}
