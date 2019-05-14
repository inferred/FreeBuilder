package org.inferred.freebuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generation options for {@link Object#toString()}.
 */
@Target(value = {})
@Retention(RetentionPolicy.SOURCE)
public @interface ToString {
  /**
   * If present, do not include this property in the generated {@link Object#toString()}.
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.SOURCE)
  @interface Exclude {
  }
}