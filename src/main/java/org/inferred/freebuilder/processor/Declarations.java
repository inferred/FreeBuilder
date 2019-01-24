package org.inferred.freebuilder.processor;

import org.inferred.freebuilder.processor.BuilderFactory.TypeInference;
import org.inferred.freebuilder.processor.source.Scope;
import org.inferred.freebuilder.processor.source.Scope.Level;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.Variable;

import java.util.Optional;

public class Declarations {

  private static final String UPCAST_COMMENT =
      "// Upcast to access private fields; otherwise, oddly, we get an access violation.";

  private enum Declaration implements Scope.Key<Variable> {
    UPCAST, FRESH_BUILDER;

    @Override
    public Level level() {
      return Level.METHOD;
    }
  }

  /**
   * Upcasts a Builder instance to the generated superclass, to allow access to private fields.
   *
   * <p>Reuses an existing upcast instance if one was already declared in this scope.
   *
   * @param code the {@link SourceBuilder} to add the declaration to
   * @param datatype metadata about the user type the builder is being generated for
   * @param builder the Builder instance to upcast
   * @returns a variable holding the upcasted instance
   */
  public static Variable upcastToGeneratedBuilder(
      SourceBuilder code, Datatype datatype, String builder) {
    return code.scope().computeIfAbsent(Declaration.UPCAST, () -> {
      Variable base = new Variable("base");
      code.addLine(UPCAST_COMMENT)
          .addLine("%s %s = %s;", datatype.getGeneratedBuilder(), base, builder);
      return base;
    });
  }

  /**
   * Declares a fresh Builder to copy default property values from.
   *
   * <p>Reuses an existing fresh Builder instance if one was already declared in this scope.
   *
   * @returns a variable holding a fresh Builder, if a no-args factory method is available to
   *     create one with
   */
  public static Optional<Variable> freshBuilder(SourceBuilder code, Datatype datatype) {
    if (!datatype.getBuilderFactory().isPresent()) {
      return Optional.empty();
    }
    return Optional.of(code.scope().computeIfAbsent(Declaration.FRESH_BUILDER, () -> {
      Variable defaults = new Variable("defaults");
      code.addLine("%s %s = %s;",
          datatype.getGeneratedBuilder(),
          defaults,
          datatype.getBuilderFactory().get()
              .newBuilder(datatype.getBuilder(), TypeInference.INFERRED_TYPES));
      return defaults;
    }));
  }

  private Declarations() {}

}
