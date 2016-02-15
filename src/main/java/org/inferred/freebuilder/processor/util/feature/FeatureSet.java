package org.inferred.freebuilder.processor.util.feature;

/**
 * A set of {@link Feature} instances, indexed by {@link FeatureType}.
 */
public interface FeatureSet {
  /** Returns an instance of {@code featureType}. */
  <T extends Feature<T>> T get(FeatureType<T> featureType);
}
