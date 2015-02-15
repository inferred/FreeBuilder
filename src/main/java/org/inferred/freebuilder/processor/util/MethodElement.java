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

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.addAll;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.inferred.freebuilder.processor.util.TypeShortener.AlwaysShorten;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;

/**
 * A mutable JavaBean implementation of {@link ExecutableElement} for
 * {@link ElementKind#METHOD methods}, encapsulating the logic of writing a readable method
 * signature.
 *
 * <p>Note: does not currently support generic parameters.
 */
public class MethodElement extends ValueType implements ExecutableElement {

  private final ArrayList<AnnotationMirror> annotationMirrors = new ArrayList<AnnotationMirror>();
  private final EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
  private final TypeMirror returnType;
  private final Name simpleName;
  private final ArrayList<ParameterElement> parameters = new ArrayList<ParameterElement>();
  private boolean varArgs = false;
  private final ArrayList<TypeMirror> thrownTypes = new ArrayList<TypeMirror>();

  /**
   * Returns a new MethodElement, named {@code simpleName} and returning {@code returnType},
   * with no annotations, modifiers, parameters or thrown types.
   */
  public MethodElement(TypeMirror returnType, Name simpleName) {
    this.returnType = returnType;
    this.simpleName = simpleName;
  }

  @Override
  public MethodType asType() {
    return new MethodType();
  }

  @Override
  public ElementKind getKind() {
    return ElementKind.METHOD;
  }

  @Override
  public ArrayList<AnnotationMirror> getAnnotationMirrors() {
    return annotationMirrors;
  }

  public MethodElement addAnnotationMirror(AnnotationMirror annotationMirror) {
    annotationMirrors.add(annotationMirror);
    return this;
  }

  /** @deprecated Unsupported. */
  @Override
  @Deprecated
  public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EnumSet<Modifier> getModifiers() {
    return modifiers;
  }

  public MethodElement addModifier(Modifier modifier) {
    modifiers.add(modifier);
    return this;
  }

  public MethodElement removeModifier(Modifier modifier) {
    modifiers.remove(modifier);
    return this;
  }

  public MethodElement setModifiers(Iterable<? extends Modifier> modifiers) {
    this.modifiers.clear();
    addAll(this.modifiers, modifiers);
    return this;
  }

  /** @deprecated Unsupported. */
  @Override
  @Deprecated
  public Element getEnclosingElement() {
    throw new UnsupportedOperationException();
  }

  /** @deprecated Always returns an empty list. */
  @Override
  @Deprecated
  public List<? extends Element> getEnclosedElements() {
    return ImmutableList.of();
  }

  @Override
  public <R, P> R accept(ElementVisitor<R, P> v, P p) {
    return v.visitExecutable(this, p);
  }

  /** @deprecated Always returns an empty list.. */
  @Override
  @Deprecated
  public List<? extends TypeParameterElement> getTypeParameters() {
    return ImmutableList.of();
  }

  @Override
  public TypeMirror getReturnType() {
    return returnType;
  }

  @Override
  public ArrayList<ParameterElement> getParameters() {
    return parameters;
  }

  public MethodElement addParameter(ParameterElement parameter) {
    parameter.setEnclosingElement(this);
    parameters.add(parameter);
    return this;
  }

  @Override
  public boolean isVarArgs() {
    return varArgs;
  }

  public MethodElement setVarArgs(boolean varArgs) {
    this.varArgs = varArgs;
    return this;
  }

  @Override
  public ArrayList<TypeMirror> getThrownTypes() {
    return thrownTypes;
  }

  public MethodElement addThrownType(TypeMirror thrownType) {
    thrownTypes.add(thrownType);
    return this;
  }

  /** @deprecated Always returns null. */
  @Override
  @Deprecated
  public AnnotationValue getDefaultValue() {
    return null;
  }

  @Override
  public Name getSimpleName() {
    return simpleName;
  }

  /**
   * Writes the signature of this {@code MethodElement} out to {@code builder}, and returns another
   * {@link SourceBuilder} to append the method implementation to. The implementation code will be
   * indented, and the terminal brace will be written when the returned SourceBuilder is closed.
   *
   * <p>If the method is abstract, the returned SourceBuilder will throw an exception if it is
   * written to.
   */
  public MethodSourceBuilder startWritingTo(SourceBuilder builder) {
    if (modifiers.contains(Modifier.ABSTRACT)) {
      writeSignature(builder, ";");
      return new ThrowingSourceBuilder();
    } else {
      writeSignature(builder, " {");
      return new MethodSourceBuilderImpl(builder);
    }
  }

  private void writeSignature(SourceBuilder builder, String trailingText) {
    writeMirrors(builder);
    writeMethodNameLine(builder, trailingText);
    writeParameters(builder, trailingText);
    writeThrownTypes(builder, trailingText);
  }

  private void writeMirrors(final SourceBuilder builder) {
    for (AnnotationMirror annotationMirror : annotationMirrors) {
      builder.addLine("  %s", annotationMirror);
    }
  }

  private void writeMethodNameLine(final SourceBuilder builder, String trailingText) {
    builder.add("  ");
    if (!modifiers.isEmpty()) {
      builder.add("%s ", Joiner.on(" ").join(modifiers));
    }
    builder.add("%s %s(", returnType, simpleName);
    // If there are no parameters, we can close the bracket right now.
    if (parameters.isEmpty()) {
      builder.add(")");
      // If there are no thrown types either, we can conclude the signature.
      if (thrownTypes.isEmpty()) {
        builder.add(trailingText);
      }
    }
    builder.add("\n");
  }

  private void writeParameters(final SourceBuilder builder, String trailingText) {
    if (!parameters.isEmpty()) {
      for (int i = 0; i < parameters.size(); ++i) {
        // Write each annotation on its own line if there is more than one of them.
        ParameterElement parameter = parameters.get(i);
        if (parameter.getAnnotationMirrors().size() > 1) {
          for (AnnotationMirror annotationMirror : parameter.getAnnotationMirrors()) {
            builder.addLine("      %s", annotationMirror);
          }
        }
        builder.add("      ");
        // If there is a single annotation, it can go inline.
        if (parameter.getAnnotationMirrors().size() == 1) {
          builder.add("%s ", getOnlyElement(parameter.getAnnotationMirrors()));
        }
        // Write any modifiers.
        if (!parameter.getModifiers().isEmpty()) {
          builder.add("%s ", Joiner.on(" ").join(parameter.getModifiers()));
        }
        // Write the parameter type and name.
        builder.add("%s %s", parameter.asType(), parameter.getSimpleName());
        if (i < parameters.size() - 1) {
          // Each line but the last ends with a comma.
          builder.add(",");
        } else {
          // The last line ends with a close bracket.
          builder.add(")");
          // If there are no thrown types, we can conclude the signature.
          if (thrownTypes.isEmpty()) {
            builder.add(trailingText);
          }
        }
        builder.add("\n");
      }
    }
  }

  private void writeThrownTypes(final SourceBuilder builder, String trailingText) {
    if (!thrownTypes.isEmpty()) {
      for (int i = 0; i < thrownTypes.size(); ++i) {
        builder.add(parameters.isEmpty() ? "      " : "          ");
        if (i == 0) {
          builder.add("throws ");
        } else {
          builder.add("    ");
        }
        builder.add("%s", thrownTypes.get(i));
        if (i < thrownTypes.size() - 1) {
          builder.add(",");
        } else {
          builder.add(trailingText);
        }
        builder.add("\n");
      }
    }
  }

  /** {@link Closeable} {@link SourceBuilder} for writing methods. */
  public abstract static class MethodSourceBuilder implements SourceBuilder, Closeable {
    @Override
    public abstract void close();

    @Override
    public abstract MethodSourceBuilder add(String fmt, Object... args);

    @Override
    public MethodSourceBuilder addLine(String fmt, Object... args) {
      return add(fmt + "\n", args);
    }
  }

  private static final class ThrowingSourceBuilder extends MethodSourceBuilder {

    @Override
    public MethodSourceBuilder add(String fmt, Object... args) {
      throw new IllegalStateException("Cannot write implementation code for an abstract method");
    }

    @Override
    public void close() {}
  }

  private static final class MethodSourceBuilderImpl
      extends MethodSourceBuilder implements Closeable {

    private final SourceBuilder code;
    private boolean startOfLine = true;
    private boolean closed = false;

    MethodSourceBuilderImpl(SourceBuilder code) {
      this.code = code;
    }

    @Override
    public MethodSourceBuilder add(String fmt, Object... args) {
      checkState(!closed, "Cannot call add() after close()");
      String indentedFmt = (startOfLine ? "    " : "") + Joiner.on("\n    ").join(fmt.split("\n"));
      code.add(indentedFmt, args);
      startOfLine = fmt.endsWith("\n");
      if (startOfLine) {
        code.add("\n");
      }
      return this;
    }

    @Override
    public void close() {
      if (!closed) {
        if (!startOfLine) {
          code.add("\n");
        }
        code.addLine("  }");
      }
      closed = true;
    }
  }

  /** An {@link ExecutableType} for a {@link MethodElement}. */
  public class MethodType implements ExecutableType {

    @Override
    public TypeKind getKind() {
      return TypeKind.EXECUTABLE;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
      return v.visitExecutable(this, p);
    }

    /** @deprecated Always returns an empty list. */
    @Override
    @Deprecated
    public List<? extends TypeVariable> getTypeVariables() {
      return ImmutableList.of();
    }

    @Override
    public TypeMirror getReturnType() {
      return returnType;
    }

    @Override
    public List<TypeMirror> getParameterTypes() {
      return Lists.transform(parameters, AS_TYPE);
    }

    @Override
    public ArrayList<TypeMirror> getThrownTypes() {
      return thrownTypes;
    }
  }

  /**
   * A mutable JavaBean implementation of {@link VariableElement} for parameters of
   * {@link MethodElement} instances.
   */
  public static class ParameterElement extends ValueType implements VariableElement {

    private final ArrayList<AnnotationMirror> annotationMirrors = new ArrayList<AnnotationMirror>();
    private final EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
    private final TypeMirror type;
    private final Name simpleName;
    private MethodElement enclosingElement = null;

    public ParameterElement(TypeMirror type, Name simpleName) {
      this.type = type;
      this.simpleName = simpleName;
    }

    @Override
    public TypeMirror asType() {
      return type;
    }

    @Override
    public ElementKind getKind() {
      return ElementKind.PARAMETER;
    }

    @Override
    public ArrayList<AnnotationMirror> getAnnotationMirrors() {
      return annotationMirrors;
    }

    public ParameterElement addAnnotationMirror(AnnotationMirror annotationMirror) {
      this.annotationMirrors.add(annotationMirror);
      return this;
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public EnumSet<Modifier> getModifiers() {
      return modifiers;
    }

    public ParameterElement setFinal() {
      modifiers.add(Modifier.FINAL);
      return this;
    }

    public ParameterElement setModifiers(Iterable<Modifier> modifiers) {
      this.modifiers.clear();
      Iterables.addAll(this.modifiers, modifiers);
      return this;
    }

    @Override
    public Name getSimpleName() {
      return simpleName;
    }

    @Override
    public MethodElement getEnclosingElement() {
      return enclosingElement;
    }

    void setEnclosingElement(MethodElement enclosingElement) {
      checkState(this.enclosingElement == null, "Cannot add the same parameter instance twice");
      this.enclosingElement = enclosingElement;
    }

    @Override
    public List<? extends Element> getEnclosedElements() {
      return ImmutableList.of();
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
      return v.visitVariable(this, p);
    }

    @Override
    public Object getConstantValue() {
      return null;
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("annotationMirrors", annotationMirrors);
      fields.add("modifiers", modifiers);
      fields.add("simpleName", simpleName.toString());
      fields.add("type", type.toString());
    }
  }

  @Override
  public String toString() {
    SourceStringBuilder builder = new SourceStringBuilder(new AlwaysShorten());
    writeSignature(builder, ";");
    return builder.toString();
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("annotationMirrors", annotationMirrors);
    fields.add("modifiers", modifiers);
    fields.add("parameters", parameters);
    fields.add("returnType", returnType.toString());
    fields.add("simpleName", simpleName.toString());
    fields.add("thrownTypes", Lists.transform(thrownTypes, toStringFunction()));
    fields.add("varArgs", varArgs);
  }

  private static final Function<Element, TypeMirror> AS_TYPE = new Function<Element, TypeMirror>() {
    @Override public TypeMirror apply(Element input) {
      return input.asType();
    }
  };
}
