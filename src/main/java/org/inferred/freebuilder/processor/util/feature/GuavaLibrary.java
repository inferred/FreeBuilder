package org.inferred.freebuilder.processor.util.feature;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Chars;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import org.inferred.freebuilder.processor.util.Shading;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.Arrays;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

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

  private static Class<?> primitiveUtils(TypeMirror elementType) {
    switch (elementType.getKind()) {
    case BOOLEAN:
      return Booleans.class;
    case BYTE:
      return Bytes.class;
    case CHAR:
      return Chars.class;
    case DOUBLE:
      return Doubles.class;
    case FLOAT:
      return Floats.class;
    case INT:
      return Ints.class;
    case LONG:
      return Longs.class;
    case SHORT:
      return Shorts.class;
    default:
      throw new IllegalArgumentException("Unexpected primitive type " + elementType);
    }
  }

  /**
   * Returns a type with an {@code asList} method suitable for an array of {@code elementType}
   * elements, if one exists.
   *
   * <p>For non-primitive element types, this is just {@link Arrays}. For primitive element types,
   * it will be one of the Guava utility classes like {@link Ints}, if Guava is available.
   */
  public Optional<Class<?>> arrayUtils(TypeMirror elementType) {
    if (!elementType.getKind().isPrimitive()) {
      return Optional.<Class<?>>of(Arrays.class);
    } else if (isAvailable()) {
      return Optional.<Class<?>>fromNullable(primitiveUtils(elementType));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public String toString() {
    return humanReadableFormat;
  }
}