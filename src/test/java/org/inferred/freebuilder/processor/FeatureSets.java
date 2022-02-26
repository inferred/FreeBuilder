package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.source.feature.SourceLevel.JAVA_8;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.inferred.freebuilder.processor.source.feature.FeatureSet;
import org.inferred.freebuilder.processor.source.feature.GuavaLibrary;
import org.inferred.freebuilder.processor.source.feature.StaticFeatureSet;

public final class FeatureSets {

  /** For tests valid in any environment. */
  public static final List<FeatureSet> ALL =
      ImmutableList.of(
          new StaticFeatureSet(JAVA_8), new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  /** For tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA =
      ImmutableList.of(new StaticFeatureSet(JAVA_8, GuavaLibrary.AVAILABLE));

  private FeatureSets() {}
}
