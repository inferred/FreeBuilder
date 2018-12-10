package org.inferred.freebuilder.processor;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.List;

public final class FeatureSets {

  /** For tests valid in any environment. */
  public static final List<FeatureSet> ALL = ImmutableList.of(
      new StaticFeatureSet(),
      new StaticFeatureSet(GuavaLibrary.AVAILABLE));

  /** For tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA = ImmutableList.of(
      new StaticFeatureSet(GuavaLibrary.AVAILABLE));

  private FeatureSets() {}
}
