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
import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.Shading;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;

/**
 * Compliance levels which are idiomatically supported by this processor.
 *
 * <p>{@link SourceVersion} is problematic to use, as the constants themselves will be missing
 * on compilers that do not support them (e.g. "RELEASE_8" is not available on javac v6 or v7).
 * Additionally, {@code sourceLevel.javaUtilObjects().isPresent()} is far more readable than
 * {@code sourceVersion.compareTo(SourceLevel.RELEASE_7) >= 0}.
 */
public enum SourceLevel implements Feature<SourceLevel> {

  JAVA_8("Java 8+", 8);

  /**
   * Constant to pass to {@link SourceBuilder#feature(FeatureType)} to get the current
   * {@link SourceLevel}.
   */
  public static final FeatureType<SourceLevel> SOURCE_LEVEL = new FeatureType<SourceLevel>() {

    @Override
    protected SourceLevel testDefault(FeatureSet features) {
      return JAVA_8;
    }

    @Override
    protected SourceLevel forEnvironment(ProcessingEnvironment env, FeatureSet features) {
      return JAVA_8;
    }
  };

  private static final QualifiedName STREAM = QualifiedName.of("java.util.stream", "Stream");

  private final String humanReadableFormat;
  private final int version;

  SourceLevel(String humanReadableFormat, int version) {
    this.humanReadableFormat = humanReadableFormat;
    this.version = version;
  }

  public Optional<QualifiedName> baseStream() {
    return Optional.of(QualifiedName.of("java.util.stream", "BaseStream"));
  }

  public Optional<QualifiedName> stream() {
    return Optional.of(STREAM);
  }

  public Optional<QualifiedName> spliterator() {
    return Optional.of(QualifiedName.of("java.util", "Spliterator"));
  }

  public List<String> javacArguments() {
    return ImmutableList.of("-source", Integer.toString(version));
  }

  @Override
  public String toString() {
    return humanReadableFormat;
  }

}
