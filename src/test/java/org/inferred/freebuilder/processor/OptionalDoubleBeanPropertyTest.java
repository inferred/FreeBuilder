package org.inferred.freebuilder.processor;

import java.util.OptionalDouble;

public class OptionalDoubleBeanPropertyTest extends PrimitiveOptionalBeanPropertyTest {
  @Override
  protected Class<?> type() {
    return OptionalDouble.class;
  }

  @Override
  protected String primitive() {
    return "double";
  }

  @Override
  protected Class<? extends Number> wrapper() {
    return Double.class;
  }
}
