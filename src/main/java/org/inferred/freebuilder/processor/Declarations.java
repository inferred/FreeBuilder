package org.inferred.freebuilder.processor;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.BuilderFactory.TypeInference;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;

class Declarations {

  /**
   * Upcasts a Builder instance to the generated superclass, to allow access to private fields.
   *
   * @param block the {@link Block} to add the declaration to
   * @param metadata metadata about the builder being generated
   * @param builder the Builder instance to upcast
   * @returns an Excerpt referencing the upcasted instance
   */
  public static Excerpt upcastToGeneratedBuilder(Block block, Metadata metadata, String builder) {
    return block.declare(
        "base",
        "// Upcast to access private fields; otherwise, oddly, we get an access violation.%n"
            + "%1$s base = (%1$s) %2$s;",
        metadata.getGeneratedBuilder(),
        builder);
  }

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
