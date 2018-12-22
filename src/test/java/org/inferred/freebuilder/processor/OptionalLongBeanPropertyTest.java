package org.inferred.freebuilder.processor;

import java.util.OptionalLong;

public class OptionalLongBeanPropertyTest extends PrimitiveOptionalBeanPropertyTest {
  @Override
  protected Class<?> type() {
    return OptionalLong.class;
  }

  @Override
  protected String primitive() {
    return "long";
  }

  @Override
  protected Class<? extends Number> wrapper() {
    return Long.class;
  }

  @Override
  protected Number num(Integer value) {
    return value.longValue();
  }
}
