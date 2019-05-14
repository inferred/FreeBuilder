package org.inferred.freebuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generation options for {@link Object#equals(Object)} and {@link Object#hashCode()}.
 */
@Target(value = {})
@Retention(RetentionPolicy.SOURCE)
public @interface EqualsAndHashCode {

  /**
   * If present, do not include this property in the generated
   * {@link Object#equals(Object)} and {@link Object#hashCode()}.
   */
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.SOURCE)
  @interface Exclude {
  }
}
