package org.inferred.freebuilder.processor.property;

import com.google.common.collect.ImmutableList;

import java.util.List;

public class Factories {

  /**
   * Factories of {@link PropertyCodeGenerator} instances. Note: order is important; the default
   * factory should always be last.
   */
  public static final List<PropertyCodeGenerator.Factory> PROPERTY_FACTORIES = ImmutableList.of(
      new NullableProperty.Factory(), // Must be first, as no other factory supports nulls
      new BuildableListProperty.Factory(), // Must be before ListProperty
      new ListProperty.Factory(),
      new SetProperty.Factory(),
      new SortedSetProperty.Factory(),
      new MapProperty.Factory(),
      new BiMapProperty.Factory(),
      new MultisetProperty.Factory(),
      new ListMultimapProperty.Factory(),
      new SetMultimapProperty.Factory(),
      new PrimitiveOptionalProperty.Factory(),
      new OptionalProperty.Factory(),
      new BuildableProperty.Factory(),
      new DefaultProperty.Factory()); // Must be last, as it will always return a CodeGenerator

  private Factories() { }
}
