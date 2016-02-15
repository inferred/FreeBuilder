package org.inferred.freebuilder.processor.util.feature;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * A set of {@link Feature} instances, determined dynamically by calling
 * {@link FeatureType#forEnvironment(ProcessingEnvironment)}.
 */
public class EnvironmentFeatureSet implements FeatureSet {

  private static class FeatureFromEnvironmentLoader
      extends CacheLoader<FeatureType<?>, Feature<?>> {
    private final ProcessingEnvironment env;

    /** <pre>featureType -> featureType.forEnvironment(env)</pre> */
    private FeatureFromEnvironmentLoader(ProcessingEnvironment env) {
      this.env = env;
    }

    @Override
    public Feature<?> load(FeatureType<?> featureType) {
      return featureType.forEnvironment(env);
    }
  }

  private final LoadingCache<FeatureType<?>, Feature<?>> featuresByType;

  /** Constructs a feature set using the given processing environment. */
  public EnvironmentFeatureSet(ProcessingEnvironment env) {
    featuresByType = CacheBuilder.newBuilder()
        .concurrencyLevel(1)
        .build(new FeatureFromEnvironmentLoader(env));
  }

  @Override
  public <T extends Feature<T>> T get(FeatureType<T> featureType) {
    @SuppressWarnings("unchecked")
    T feature = (T) featuresByType.getUnchecked(featureType);
    return feature;
  }
}
