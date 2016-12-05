package org.inferred.freebuilder.processor.util.feature;

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
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

  private static final ParameterizedType CONSUMER =
      QualifiedName.of("java.util.function", "Consumer").withParameters("T");
  private static final ParameterizedType BI_CONSUMER =
      QualifiedName.of("java.util.function", "BiConsumer").withParameters("T", "U");
  private static final ParameterizedType UNARY_OPERATOR =
      QualifiedName.of("java.util.function", "UnaryOperator").withParameters("T");

  private final String humanReadableFormat;

  FunctionPackage(String humanReadableFormat) {
    this.humanReadableFormat = humanReadableFormat;
  }

  /**
   * Parameterized type for {@code java.util.function.Consumer<T>}, if available.
   */
  public Optional<ParameterizedType> consumer() {
    return ifAvailable(CONSUMER);
  }

  /**
   * Parameterized type for {@code java.util.function.BiConsumer<T>}, if available.
   */
  public Optional<ParameterizedType> biConsumer() {
    return ifAvailable(BI_CONSUMER);
  }

  /**
   * Parameterized type for {@code java.util.function.UnaryOperator<T>}, if available.
   */
  public Optional<ParameterizedType> unaryOperator() {
    return ifAvailable(UNARY_OPERATOR);
  }

  @Override
  public String toString() {
    return humanReadableFormat;
  }

  private <T> Optional<T> ifAvailable(T value) {
    return (this == AVAILABLE) ? Optional.of(value) : Optional.<T>absent();
  }
}
