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
import static org.inferred.freebuilder.processor.model.ModelUtils.findAnnotationMirror;
import static org.inferred.freebuilder.processor.source.RoundEnvironments.annotatedElementsIn;

import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.source.FilerUtils;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;

/**
 * Processor for the &#64;{@link FreeBuilder} annotation.
 *
 * <p>Processing is split into analysis (owned by the {@link Analyser}) and code generation (owned
 * by the {@link GeneratedBuilder}), for testability.
 */
@AutoService(javax.annotation.processing.Processor.class)
public class Processor extends AbstractProcessor {

  /**
   * Keep track of which processors have been registered to avoid double-processing if FreeBuilder
   * ends up on the processor path twice. While we catch the resulting Filer exceptions and convert
   * them to warnings, it's still cleaner to issue a single NOTE-severity message.
   */
  private static final ConcurrentMap<ProcessingEnvironment, Processor> registeredProcessors =
      new MapMaker().weakKeys().weakValues().concurrencyLevel(1).initialCapacity(1).makeMap();

  private Analyser analyser;
  private final FeatureSet features;

  public Processor() {
    this.features = null;
  }

  @VisibleForTesting
  public Processor(FeatureSet features) {
    this.features = features;
  }

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
    if (registeredProcessors.putIfAbsent(processingEnv, this) != null) {
      processingEnv
          .getMessager()
          .printMessage(
              Kind.NOTE, "FreeBuilder processor registered twice; disabling duplicate instance");
      return;
    }
    analyser = new Analyser(processingEnv, processingEnv.getMessager());
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (analyser == null) {
      // Another FreeBuilder Processor is already registered; skip processing
      return false;
    }
    for (TypeElement type : typesIn(annotatedElementsIn(roundEnv, FreeBuilder.class))) {
      try {
        SourceBuilder code = SourceBuilder.forEnvironment(processingEnv, features);
        code.add(analyser.analyse(type));
        FilerUtils.writeCompilationUnit(processingEnv.getFiler(), code, type);
      } catch (Analyser.CannotGenerateCodeException e) {
        // Thrown to skip writing the builder source; the error will already have been issued.
      } catch (FilerException e) {
        processingEnv
            .getMessager()
            .printMessage(
                Kind.WARNING,
                "Error producing Builder: " + e.getMessage(),
                type,
                findAnnotationMirror(type, "org.inferred.freebuilder.FreeBuilder").get());
      } catch (IOException e) {
        processingEnv
            .getMessager()
            .printMessage(
                Kind.ERROR,
                "I/O error: " + Throwables.getStackTraceAsString(e),
                type,
                findAnnotationMirror(type, "org.inferred.freebuilder.FreeBuilder").get());
      } catch (RuntimeException e) {
        processingEnv
            .getMessager()
            .printMessage(
                Kind.ERROR,
                "Internal error: " + Throwables.getStackTraceAsString(e),
                type,
                findAnnotationMirror(type, "org.inferred.freebuilder.FreeBuilder").get());
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Processor)) {
      return false;
    }
    Processor other = (Processor) obj;
    return Objects.equal(features, other.features);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(Processor.class, features);
  }
}
