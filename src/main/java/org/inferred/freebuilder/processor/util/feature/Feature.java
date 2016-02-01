package org.inferred.freebuilder.processor.util.feature;

import org.inferred.freebuilder.processor.util.SourceBuilder;

/**
 * A feature encapsulates the availability of a type or source level feature that can be used in
 * the source written to a {@link SourceBuilder}, such as Java language-level features, or Guava
 * types and methods.
 *
 * <p>A feature will typically provide a {@link FeatureType} constant that can be passed to
 * {@code SourceBuilder#feature(FeatureType)} to determine the current status of a feature.
 * For instance, to determine whether the diamond operator is available for use:
 *
 * <pre>code.feature({@link SourceLevel#SOURCE_LEVEL
 *     SOURCE_LEVEL}).{@link SourceLevel#supportsDiamondOperator() supportsDiamondOperator()}</pre>
 */
public interface Feature<T extends Feature<T>> {}
