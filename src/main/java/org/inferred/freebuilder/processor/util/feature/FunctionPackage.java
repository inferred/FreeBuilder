package org.inferred.freebuilder.processor.util.feature;

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Types in the java.util.function package, if available. Linked to the {@link SourceLevel} by
 * default in tests.
 */
public enum FunctionPackage implements Feature<FunctionPackage> {

  AVAILABLE("Lambdas"), UNAVAILABLE("No lambdas");

  /**
   * Constant to pass to {@link SourceBuilder#feature(FeatureType)} to get the current status of
   * {@link FunctionPackage}.
   */
  public static final FeatureType<FunctionPackage> FUNCTION_PACKAGE =
      new FeatureType<FunctionPackage>() {

        @Override
        protected FunctionPackage testDefault(FeatureSet features) {
          return determineFromSourceLevel(features);
        }

        @Override
        protected FunctionPackage forEnvironment(ProcessingEnvironment env, FeatureSet features) {
          return determineFromSourceLevel(features);
        }

        private FunctionPackage determineFromSourceLevel(FeatureSet features) {
          boolean isJava8OrHigher = features.get(SOURCE_LEVEL).compareTo(SourceLevel.JAVA_8) >= 0;
          return isJava8OrHigher ? AVAILABLE : UNAVAILABLE;
        }
      };

  private final String humanReadableFormat;

  FunctionPackage(String humanReadableFormat) {
    this.humanReadableFormat = humanReadableFormat;
  }

  public boolean isAvailable() {
    return (this == AVAILABLE);
  }

  @Override
  public String toString() {
    return humanReadableFormat;
  }
}
