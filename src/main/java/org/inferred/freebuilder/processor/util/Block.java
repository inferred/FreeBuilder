package org.inferred.freebuilder.processor.util;

import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureType;

/**
 * A Block encapsulates a method scope, including any parameters it declares.
 */
public class Block extends Excerpt implements SourceBuilder {

  public static Block methodBody(SourceBuilder parent, String... paramNames) {
    return new Block(parent);
  }

  private final SourceStringBuilder body;

  private Block(SourceBuilder parent) {
    body = parent.subBuilder();
  }

  @Override
  public Block add(String fmt, Object... args) {
    body.add(fmt, args);
    return this;
  }

  @Override
  public Block addLine(String fmt, Object... args) {
    body.addLine(fmt, args);
    return this;
  }

  @Override
  public Block add(Excerpt excerpt) {
    body.add(excerpt);
    return this;
  }

  @Override
  public SourceStringBuilder subBuilder() {
    return body.subBuilder();
  }

  @Override
  public <T extends Feature<T>> T feature(FeatureType<T> featureType) {
    return body.feature(featureType);
  }

  @Override
  public Scope scope() {
    return body.scope();
  }

  @Override
  public void addTo(SourceBuilder source) {
    source.add("%s", body);
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("body", body.toString());
  }
}