package org.inferred.freebuilder.processor.util;

/**
 * Utility methods related to &#64;FreeBuilder dependencies being relocated as part of shading.
 */
public class Shading {

  private static final String SHADE_PACKAGE = "org.inferred.freebuilder.shaded.";

  public static String unshadedName(String qualifiedName) {
    if (qualifiedName.startsWith(Shading.SHADE_PACKAGE)) {
      return qualifiedName.substring(Shading.SHADE_PACKAGE.length());
    } else {
      return qualifiedName;
    }
  }
}
