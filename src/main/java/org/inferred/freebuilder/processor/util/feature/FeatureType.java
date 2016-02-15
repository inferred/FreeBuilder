package org.inferred.freebuilder.processor.util.feature;

import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Algorithm to select the correct instance of a given feature type for a processing environment,
 * and the default to use in tests when an explicit value has not been registered for that feature.
 *
 * <p>Each feature class should expose a single {@code FeatureType} constant for the user to pass
 * to {@link SourceBuilder#feature(FeatureType)}, e.g. {@link SourceLevel#SOURCE_LEVEL}.
 */
public abstract class FeatureType<F extends Feature<F>> {

  /** Returns the instance of {@code F} to use by default in tests. */
  protected abstract F testDefault();

  /** Returns the instance of {@code F} to use in {@code env}. */
  protected abstract F forEnvironment(ProcessingEnvironment env);

  @SuppressWarnings("unchecked")
  protected Class<F> type() {
    return (Class<F>) testDefault().getClass();
  }
}
