package org.inferred.freebuilder.processor;

import org.inferred.freebuilder.processor.BuilderFactory.TypeInference;
import org.inferred.freebuilder.processor.util.Block;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.Excerpts;

import java.util.Optional;

class Declarations {

  /**
   * Upcasts a Builder instance to the generated superclass, to allow access to private fields.
   *
   * @param block the {@link Block} to add the declaration to
   * @param datatype metadata about the user type the builder is being generated for
   * @param builder the Builder instance to upcast
   * @returns an Excerpt referencing the upcasted instance
   */
  public static Excerpt upcastToGeneratedBuilder(Block block, Datatype datatype, String builder) {
    return block.declare(
        Excerpts.add(
            "// Upcast to access private fields; otherwise, oddly, we get an access violation.%n%s",
            datatype.getGeneratedBuilder()),
        "base",
        Excerpts.add(builder));
  }

  /**
   * Declares a fresh Builder to copy default property values from.
   *
   * @returns an Excerpt referencing a fresh Builder, if a no-args factory method is available to
   *     create one with
   */
  public static Optional<Excerpt> freshBuilder(Block block, Datatype datatype) {
    if (!datatype.getBuilderFactory().isPresent()) {
      return Optional.empty();
    }
    Excerpt defaults = block.declare(
        datatype.getGeneratedBuilder(),
        "_defaults",
        datatype.getBuilderFactory().get()
            .newBuilder(datatype.getBuilder(), TypeInference.INFERRED_TYPES));
    return Optional.of(defaults);
  }

  private Declarations() {}

}
