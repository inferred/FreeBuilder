package org.inferred.freebuilder.processor.util.feature;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;

/**
 * Stores a set of {@link Feature} instances, defaulting to {@link FeatureType#testDefault()} when
 * asked for a type that was not explicitly registered.
 */
public class StaticFeatureSet implements FeatureSet {

  @SuppressWarnings("rawtypes")
  private final ImmutableMap<Class<? extends Feature>, Feature<?>> featuresByType;

  /**
   * Creates a feature set which will return {@code features} when {@link #get} is called for the
   * appropriate type.
   */
  public StaticFeatureSet(Feature<?>... features) {
    @SuppressWarnings("rawtypes")
    ImmutableMap.Builder<Class<? extends Feature>, Feature<?>> featuresBuilder =
        ImmutableMap.builder();
    for (Feature<?> feature : features) {
      featuresBuilder.put(feature.getClass(), feature);
    }
    this.featuresByType = featuresBuilder.build();
  }

  /**
   * Returns the registered instance of {@code featureType}, or the value of
   * {@link FeatureType#testDefault()} if no explicit instance was registered with this set.
   */
  @Override
  public <T extends Feature<T>> T get(FeatureType<T> featureType) {
    @SuppressWarnings("unchecked")
    T feature = (T) featuresByType.get(featureType.type());
    if (feature != null) {
      return feature;
    }
    return featureType.testDefault();
  }

  @Override
  public String toString() {
    return Joiner.on(", ").join(featuresByType.values());
  }
}
