package org.inferred.freebuilder.processor.source;

import org.inferred.freebuilder.processor.source.feature.JavaxPackage;

public class Excerpts {

  public static final Excerpt EMPTY = code -> {};

  public static Excerpt add(String fmt, Object... args) {
    return code -> code.add(fmt, args);
  }

  /**
   * Returns an excerpt of the {@link javax.annotation.Generated} annotation, if available, with
   * value set to the full name of the {@code generator} class as recommended.
   */
  public static Excerpt generated(Class<?> generator) {
    return code -> {
      code.feature(JavaxPackage.JAVAX)
          .generated()
          .ifPresent(
              generated -> {
                code.addLine("@%s(\"%s\")", generated, generator.getName());
              });
    };
  }

  public static Excerpt join(String separator, Iterable<?> excerpts) {
    return code -> appendJoined(code, separator, excerpts);
  }

  private static void appendJoined(SourceBuilder source, String separator, Iterable<?> excerpts) {
    String itemPrefix = "";
    for (Object object : excerpts) {
      source.add("%s%s", itemPrefix, object);
      itemPrefix = separator;
    }
  }

  private Excerpts() {}
}
