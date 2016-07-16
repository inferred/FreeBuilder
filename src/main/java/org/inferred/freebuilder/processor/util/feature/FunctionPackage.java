package org.inferred.freebuilder.processor.util.feature;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.Shading;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;

/**
 * Types in the java.util.function package, if available. Defaults to {@link #UNAVAILABLE} in tests.
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
        protected FunctionPackage testDefault() {
          return UNAVAILABLE;
        }

        @Override
        protected FunctionPackage forEnvironment(ProcessingEnvironment env) {
          if (runningInEclipse()) {
            // Eclipse is bugged: sourceVersion will never be > 7.
            // Work around this by checking for the presence of java.util.function.Consumer instead.
            return hasType(env.getElementUtils(), CONSUMER) ? AVAILABLE : UNAVAILABLE;
          } else {
            return hasLambdas(env.getSourceVersion()) ? AVAILABLE : UNAVAILABLE;
          }
        }
      };

  private static final String ECLIPSE_DISPATCHER =
      Shading.unshadedName("org.eclipse.jdt.internal.compiler.apt.dispatch.RoundDispatcher");
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

  private static boolean hasLambdas(SourceVersion version) {
    return version.ordinal() >= 8;
  }

  private static boolean hasType(Elements elements, ParameterizedType type) {
    return elements.getTypeElement(type.getQualifiedName().toString()) != null;
  }

  private <T> Optional<T> ifAvailable(T value) {
    return (this == AVAILABLE) ? Optional.of(value) : Optional.<T>absent();
  }
}
