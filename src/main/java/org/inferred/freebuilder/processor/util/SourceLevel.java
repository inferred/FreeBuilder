package org.inferred.freebuilder.processor.util;

import javax.lang.model.SourceVersion;

import com.google.common.base.Optional;

/**
 * Compliance levels which are idiomatically supported by this processor.
 *
 * <p>{@link SourceVersion} is problematic to use, as the constants themselves will be missing
 * on compilers that do not support them (e.g. "RELEASE_8" is not available on javac v6 or v7).
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

  public Optional<TypeReference> javaUtilObjects() {
    switch (this) {
      case JAVA_6:
        return Optional.absent();

      default:
        return Optional.of(TypeReference.to("java.util", "Objects"));
    }
  }
}
