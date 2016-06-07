package org.inferred.freebuilder.processor.util;

import com.google.common.collect.ComparisonChain;

public abstract class StaticExcerpt extends Excerpt implements Comparable<StaticExcerpt> {

  public enum Type { METHOD, TYPE }

  private final Type type;
  private final String name;

  protected StaticExcerpt(Type type, String name) {
    this.type = type;
    this.name = name;
  }

  public Type getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  @Override
  public int compareTo(StaticExcerpt o) {
    return ComparisonChain.start()
        .compare(getType(), o.getType())
        .compare(getName(), o.getName())
        .result();
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("type", type);
    fields.add("name", name);
  }
}
