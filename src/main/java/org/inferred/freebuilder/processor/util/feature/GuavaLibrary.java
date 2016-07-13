package org.inferred.freebuilder.processor.util.feature;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.Shading;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Whether the Guava library is available or not. Defaults to {@link #UNAVAILABLE} in tests.
 */
public enum GuavaLibrary implements Feature<GuavaLibrary> {

  AVAILABLE("Guava"), UNAVAILABLE("No Guava");

  /**
   * Constant to pass to {@link SourceBuilder#feature(FeatureType)} to get the current status of
   * {@link GuavaLibrary}.
   */
  public static final FeatureType<GuavaLibrary> GUAVA = new FeatureType<GuavaLibrary>() {

    @Override
    protected GuavaLibrary testDefault() {
      return UNAVAILABLE;
    }

    @Override
    protected GuavaLibrary forEnvironment(ProcessingEnvironment env) {
      String name = Shading.unshadedName(ImmutableList.class.getName());
      TypeElement element = env.getElementUtils().getTypeElement(name);
      return (element != null) ? AVAILABLE : UNAVAILABLE;
    }
  };

  private final String humanReadableFormat;

  GuavaLibrary(String humanReadableFormat) {
    this.humanReadableFormat = humanReadableFormat;
  }

  public boolean isAvailable() {
    return this != UNAVAILABLE;
  }

  @Override
  public String toString() {
    return humanReadableFormat;
  }
}