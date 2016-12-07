/*
 * Copyright 2015 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.util.feature;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.Shading;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;

/**
 * Compliance levels which are idiomatically supported by this processor.
 *
 * <p>{@link SourceVersion} is problematic to use, as the constants themselves will be missing
 * on compilers that do not support them (e.g. "RELEASE_8" is not available on javac v6 or v7).
 * Additionally, {@code sourceLevel.javaUtilObjects().isPresent()} is far more readable than
 * {@code sourceVersion.compareTo(SourceLevel.RELEASE_7) >= 0}.
 */
public enum SourceLevel implements Feature<SourceLevel> {

  JAVA_6("Java 6"), JAVA_7("Java 7"), JAVA_8("Java 8+");

  /**
   * Constant to pass to {@link SourceBuilder#feature(FeatureType)} to get the current
   * {@link SourceLevel}.
   */
  public static final FeatureType<SourceLevel> SOURCE_LEVEL = new FeatureType<SourceLevel>() {

    @Override
    protected SourceLevel testDefault(FeatureSet features) {
      return JAVA_6;
    }

    @Override
    protected SourceLevel forEnvironment(ProcessingEnvironment env, FeatureSet features) {
      int sourceVersion = env.getSourceVersion().ordinal();

      if (sourceVersion <= 6) {
        // RELEASE_6 is always available, as previous releases did not support annotation processing
        return JAVA_6;
      } else if (sourceVersion >= 8) {
        // Return JAVA_8 for versions 9+ also.
        return JAVA_8;
      } else if (runningInEclipse()) {
        // Some versions of Eclipse erroneously return RELEASE_7 instead of RELEASE_8.
        // Work around this by checking for the presence of java.util.Stream instead.
        return hasType(env.getElementUtils(), STREAM) ? JAVA_8 : JAVA_7;
      } else {
        return JAVA_7;
      }
    }
  };

  private static final QualifiedName STREAM = QualifiedName.of("java.util.stream", "Stream");
  private static final String ECLIPSE_DISPATCHER =
      Shading.unshadedName("org.eclipse.jdt.internal.compiler.apt.dispatch.RoundDispatcher");

  public static Excerpt diamondOperator(final Object type) {
    return new DiamondOperator(type);
  }

  private static final class DiamondOperator extends Excerpt {
    private final Object type;

    private DiamondOperator(Object type) {
      this.type = type;
    }

    @Override
    public void addTo(SourceBuilder source) {
      if (source.feature(SOURCE_LEVEL).compareTo(JAVA_7) >= 0) {
        source.add("<>");
      } else {
        source.add("<%s>", type);
      }
    }

    @Override
    public String toString() {
      return "diamondOperator(" + type + ")";
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("type", type);
    }
  }

  private final String humanReadableFormat;

  SourceLevel(String humanReadableFormat) {
    this.humanReadableFormat = humanReadableFormat;
  }

  public Optional<QualifiedName> javaUtilObjects() {
    switch (this) {
      case JAVA_6:
        return Optional.absent();

      default:
        return Optional.of(QualifiedName.of("java.util", "Objects"));
    }
  }

  public boolean hasLambdas() {
    return compareTo(JAVA_8) >= 0;
  }

  @Override
  public String toString() {
    return humanReadableFormat;
  }

  private static boolean hasType(Elements elements, QualifiedName type) {
    return elements.getTypeElement(type.toString()) != null;
  }

  private static boolean runningInEclipse() {
    // If we're running in Eclipse, we will have been invoked by the Eclipse round dispatcher.
    Throwable t = new Throwable();
    t.fillInStackTrace();
    for (StackTraceElement method : t.getStackTrace()) {
      if (method.getClassName().equals(ECLIPSE_DISPATCHER)) {
        return true;
      } else if (!method.getClassName().startsWith("org.inferred")) {
        return false;
      }
    }
    return false;
  }

}
