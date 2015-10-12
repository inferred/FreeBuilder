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
package org.inferred.freebuilder.processor.util;

import javax.lang.model.SourceVersion;

import com.google.common.base.Optional;

/**
 * Compliance levels which are idiomatically supported by this processor.
 *
 * <p>{@link SourceVersion} is problematic to use, as the constants themselves will be missing
 * on compilers that do not support them (e.g. "RELEASE_8" is not available on javac v6 or v7).
 * Additionally, "sourceLevel.supportsDiamondOperator()" is far more readable than
 * "sourceVersion.compareTo(SourceLevel.RELEASE_7) >= 0".
 */
public enum SourceLevel {
  JAVA_6, JAVA_7;

  public static SourceLevel from(SourceVersion sourceVersion) {
    // RELEASE_6 is always available, as previous releases did not support annotation processing.
    if (sourceVersion.compareTo(SourceVersion.RELEASE_6) <= 0) {
      return JAVA_6;
    } else {
      return JAVA_7;
    }
  }

  public Optional<QualifiedName> javaUtilObjects() {
    switch (this) {
      case JAVA_6:
        return Optional.absent();

      default:
        return Optional.of(QualifiedName.of("java.util", "Objects"));
    }
  }

  public boolean supportsDiamondOperator() {
    return this.compareTo(JAVA_7) >= 0;
  }
}
