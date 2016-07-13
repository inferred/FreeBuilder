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
import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;

/**
 * Compliance levels which are idiomatically supported by this processor.
 *
 * <p>{@link SourceVersion} is problematic to use, as the constants themselves will be missing
 * on compilers that do not support them (e.g. "RELEASE_8" is not available on javac v6 or v7).
 * Additionally, {@code sourceLevel.supportsDiamondOperator()} is far more readable than
 * {@code sourceVersion.compareTo(SourceLevel.RELEASE_7) >= 0}.
 */
public enum SourceLevel implements Feature<SourceLevel> {

  JAVA_6("Java 6"), JAVA_7("Java 7+");

  /**
   * Constant to pass to {@link SourceBuilder#feature(FeatureType)} to get the current
   * {@link SourceLevel}.
   */
  public static final FeatureType<SourceLevel> SOURCE_LEVEL = new FeatureType<SourceLevel>() {

    @Override
    protected SourceLevel testDefault() {
      return JAVA_6;
    }

    @Override
    protected SourceLevel forEnvironment(ProcessingEnvironment env) {
      // RELEASE_6 is always available, as previous releases did not support annotation processing.
      if (env.getSourceVersion().compareTo(SourceVersion.RELEASE_6) <= 0) {
        return JAVA_6;
      } else {
        return JAVA_7;
      }
    }
  };

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

  @Override
  public String toString() {
    return humanReadableFormat;
  }
}
