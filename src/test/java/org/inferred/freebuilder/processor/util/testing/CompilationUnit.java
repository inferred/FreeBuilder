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

import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.QualifiedName;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import javax.tools.SimpleJavaFileObject;

/** Simple in-memory implementation of {@link javax.tools.JavaFileObject JavaFileObject}. */
class CompilationUnit extends SimpleJavaFileObject {

  private final QualifiedName typename;
  private final String code;

  /** Returns a dummy URI for the given type name. */
  static URI uriForClass(String typeName) {
    try {
      return new URI("mem:///" + typeName.replaceAll("\\.", "/") + ".java");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  CompilationUnit(SourceBuilder unit) {
    super(uriForClass(unit.typename().toString()), Kind.SOURCE);
    this.typename = unit.typename();
    this.code = unit.toString();
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return code;
  }

  @Override
  public String toString() {
    return typename.toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(typename, code);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CompilationUnit)) {
      return false;
    }
    CompilationUnit other = (CompilationUnit) obj;
    return typename.equals(other.typename) && code.equals(other.code);
  }
}
