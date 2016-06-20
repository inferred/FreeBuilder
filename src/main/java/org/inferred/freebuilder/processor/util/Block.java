package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newLinkedHashMap;

import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureType;

import java.util.Map;

/**
 * A Block contains a preamble of lazily-added declarations followed by a body.
 */
public class Block extends Excerpt implements SourceBuilder {

  private final Map<String, Excerpt> declarations = newLinkedHashMap();
  private final SourceStringBuilder body;

  public Block(SourceBuilder parent) {
    body = parent.subBuilder();
  }

  /**
   * Declare {@code name} in this block's preamble, returning an Excerpt to use it. Duplicate
   * declarations will be elided.
   *
   * @throws IllegalStateException if {@code name} has already been added to this block with a
   *     different declaration
   */
  public Excerpt declare(final String name, final String declfmt, final Object... declArgs) {
    Excerpt declaration = Excerpts.add(declfmt, declArgs);
    Excerpt existingDeclaration = declarations.put(name, declaration);
    checkState(existingDeclaration == null || declaration.equals(existingDeclaration),
        "Incompatible declaration for '%s': %s vs %s",
        name,
        declaration,
        existingDeclaration);
    return Excerpts.add("%s", name);
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
  public void addTo(SourceBuilder source) {
    for (Excerpt declaration : declarations.values()) {
      source.add(declaration);
    }
    source.add("%s", body);
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("declarations", declarations);
    fields.add("body", body.toString());
  }
}