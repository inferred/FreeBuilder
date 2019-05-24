package org.inferred.freebuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * {@link FreeBuilder} will not include properties annotated {@code @NotInToString} in the output of
 * its generated {@link Object#toString()} implementation.
 */
@Target(ElementType.METHOD)
public @interface NotInToString {
}
