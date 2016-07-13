package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.JAVA_6;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.JAVA_7;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.FunctionPackage;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.List;

final class FeatureSets {

  /** For tests valid in any environment. */
  public static final List<FeatureSet> ALL = ImmutableList.of(
      new StaticFeatureSet(JAVA_6),
      new StaticFeatureSet(JAVA_7),
      new StaticFeatureSet(JAVA_7, FunctionPackage.AVAILABLE),
      new StaticFeatureSet(JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  /** For mapper and mutate method tests. */
  public static final List<FeatureSet> WITH_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(JAVA_7, FunctionPackage.AVAILABLE),
      new StaticFeatureSet(JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  /** For tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA = ImmutableList.of(
      new StaticFeatureSet(JAVA_6, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, GuavaLibrary.AVAILABLE),
      new StaticFeatureSet(JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  /** For mutate method tests using Guava types. */
  public static final List<FeatureSet> WITH_GUAVA_AND_LAMBDAS = ImmutableList.of(
      new StaticFeatureSet(JAVA_7, FunctionPackage.AVAILABLE, GuavaLibrary.AVAILABLE));

  private FeatureSets() {}
}
