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

import static javax.lang.model.util.ElementFilter.typesIn;
import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;
import static org.inferred.freebuilder.processor.util.RoundEnvironments.annotatedElementsIn;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.util.CompilationUnitWriter;
import org.inferred.freebuilder.processor.util.SourceLevel;

import com.google.auto.service.AutoService;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * Processor for the &#64;{@link FreeBuilder} annotation.
 *
 * <p>Processing is split into analysis (owned by the {@link Analyser}) and code generation (owned
 * by the {@link CodeGenerator}), communicating through the metadata object ({@link Metadata}), for
 * testability.
 */
@AutoService(javax.annotation.processing.Processor.class)
public class Processor extends AbstractProcessor {

  private Analyser analyser;
  private final CodeGenerator codeGenerator = new CodeGenerator();

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(FreeBuilder.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    analyser = new Analyser(
        processingEnv.getElementUtils(),
        processingEnv.getMessager(),
        MethodIntrospector.instance(processingEnv),
        processingEnv.getTypeUtils());
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement type : typesIn(annotatedElementsIn(roundEnv, FreeBuilder.class))) {
      try {
        Metadata metadata = analyser.analyse(type);
        CompilationUnitWriter code = new CompilationUnitWriter(
            processingEnv.getFiler(),
            processingEnv.getElementUtils(),
            SourceLevel.from(processingEnv.getSourceVersion()),
            metadata.getGeneratedBuilder(),
            ImmutableSet.of(
                metadata.getPartialType(), metadata.getPropertyEnum(), metadata.getValueType()),
            type);
        try {
          codeGenerator.writeBuilderSource(code, metadata);
        } finally {
          code.close();
        }
      } catch (Analyser.CannotGenerateCodeException e) {
        // Thrown to skip writing the builder source; the error will already have been issued.
      } catch (FilerException e) {
        processingEnv.getMessager().printMessage(
            Kind.WARNING,
            "Error producing Builder: " + e.getMessage(),
            type,
            findAnnotationMirror(type, "org.inferred.freebuilder.FreeBuilder").get());
      } catch (RuntimeException e) {
        processingEnv.getMessager().printMessage(
            Kind.ERROR,
            "Internal error: " + Throwables.getStackTraceAsString(e),
            type,
            findAnnotationMirror(type, "org.inferred.freebuilder.FreeBuilder").get());
      }
    }
    return false;
  }
}
