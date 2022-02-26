package org.inferred.freebuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * {@link FreeBuilder} will not check properties annotated {@code @IgnoredByEquals} when comparing
 * objects in its generated {@link Object#equals(Object)}.
 *
 * <p>To maintain the contract of {@link Object#hashCode()}), the value of these properties will
 * also be ignored in the generated implementation of that method.
 *
 * <p><b>Warning</b>: Dropping properties from equality checks makes it very easy to accidentally
 * write broken unit tests (and hard to write good ones). If you find yourself wanting to use this
 * annotation, consider first whether you actually want a different collection type (typicaly a
 * {@link Map} rather than a {@link Set}, for instance), or whether you can use an explicit
 * field-ignoring {@link Comparator} in the parts of the code that need it.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface IgnoredByEquals {}
