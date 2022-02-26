package org.inferred.freebuilder.processor.source.feature;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.processing.ProcessingEnvironment;

/**
 * A set of {@link Feature} instances, determined dynamically by calling {@link
 * FeatureType#forEnvironment}.
 */
public class EnvironmentFeatureSet implements FeatureSet {

  private final ProcessingEnvironment env;
  private final Map<FeatureType<?>, Feature<?>> featuresByType = new HashMap<>();

  /** Constructs a feature set using the given processing environment. */
  public EnvironmentFeatureSet(ProcessingEnvironment env) {
    this.env = env;
  }

  @Override
  public <T extends Feature<T>> T get(FeatureType<T> featureType) {
    @SuppressWarnings("unchecked")
    T feature = (T) featuresByType.computeIfAbsent(featureType, $ -> $.forEnvironment(env, this));
    return feature;
  }
}
