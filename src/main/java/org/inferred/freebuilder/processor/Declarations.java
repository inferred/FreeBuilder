package org.inferred.freebuilder.processor;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.BuilderFactory.TypeInference;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;

class Declarations {

  /**
   * Declares a fresh Builder to copy default property values from.
   *
   * @returns an Excerpt referencing a fresh Builder, if a no-args factory method is available to
   *     create one with
   */
  public static Optional<Excerpt> freshBuilder(Block block, Metadata metadata) {
    if (!metadata.getBuilderFactory().isPresent()) {
      return Optional.absent();
    }
    Excerpt defaults = block.declare("_defaults", "%s _defaults = %s;",
          metadata.getGeneratedBuilder(),
          metadata.getBuilderFactory().get()
              .newBuilder(metadata.getBuilder(), TypeInference.INFERRED_TYPES));
    return Optional.of(defaults);
  }

  private Declarations() {}

}
