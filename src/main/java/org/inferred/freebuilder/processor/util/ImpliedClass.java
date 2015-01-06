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

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * {@link TypeElement} representing a class that needs to be generated.
 *
 * <p>Typically created by a {@link javax.annotation.processing.Processor Processor} during user
 * code analysis, and subsequently generated using {@link #openSourceWriter}.
 *
 * <p>This type implements {@link TypeElement}, as the API is stable, well-known, and lets
 * processors treat to-be-generated and user-supplied types the same way. However, not all methods
 * are implemented (those that are not are marked {@link Deprecated @Deprecated}); additionally,
 * compilers are free to implement {@link Elements} and {@link javax.lang.model.util.Types Types}
 * in a manner hostile to third-party classes (e.g. by casting a {@link TypeElement} to a private
 * internal class), so they are, unfortunately, not fully interchangeable at present.
 */
public class ImpliedClass extends AbstractImpliedClass<PackageElement> implements TypeElement {

  private final Element originatingElement;
  private final Elements elementUtils;

  /**
   * Constructor for {@link ImpliedClass}.
   *
   * @param pkg the package the class should be created in
   * @param simpleName the name the class should be given (without package)
   * @param originatingElement the element that triggered the code generation
   * @param elementUtils the compiler-provided {@link Elements} implementation
   */
  public ImpliedClass(
      PackageElement pkg,
      CharSequence simpleName,
      Element originatingElement,
      Elements elementUtils) {
    super(
        pkg,
        simpleName,
        elementUtils);
    this.originatingElement = originatingElement;
    this.elementUtils = elementUtils;
  }

  public ImpliedNestedClass createNestedClass(CharSequence simpleName) {
    return new ImpliedNestedClass(this, simpleName, elementUtils);
  }

  /**
   * Returns a {@link SourceWriter} for creating this class. The file preamble (package and
   * imports) will be generated automatically.
   *
   * @throws FilerException if a Filer guarantee is violated (see the {@link FilerException}
   *     JavaDoc for more information); propagated because this is often seen in GUIDE projects,
   *     so should be downgraded to a warning, whereas runtime exceptions should be flagged as an
   *     internal error to the user
   */
  public SourceWriter openSourceWriter(Filer filer) throws FilerException {
    return new SourceWriter(filer, this, originatingElement);
  }

  /**
   * {@link TypeElement} representing a nested class of a class that needs to be generated.
   *
   * @see ImpliedClass
   */
public static class ImpliedNestedClass
      extends AbstractImpliedClass<TypeElement> implements TypeElement {

    private final Elements elementUtils;

    private ImpliedNestedClass(
        TypeElement enclosingElement,
        CharSequence simpleName,
        Elements elementUtils) {
      super(enclosingElement, simpleName, elementUtils);
      this.elementUtils = elementUtils;
    }

    public ImpliedNestedClass createNestedClass(CharSequence simpleName) {
      return new ImpliedNestedClass(this, simpleName, elementUtils);
    }
  }
}

