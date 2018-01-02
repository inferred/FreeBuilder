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

  public static Block methodBody(SourceBuilder parent, String... paramNames) {
    Scope methodScope = new Scope.MethodScope(parent.scope());
    for (String paramName : paramNames) {
      methodScope.add(new VariableName(paramName));
    }
    return new Block(parent, methodScope);
  }

  private final Map<String, String> variableNames = newLinkedHashMap();
  private final Map<String, Excerpt> declarations = newLinkedHashMap();
  private final SourceStringBuilder declarationsBlock;
  private final SourceStringBuilder body;

  public Block(SourceBuilder parent) {
    declarationsBlock = parent.subBuilder();
    body = parent.subBuilder();
  }

  private Block(SourceBuilder parent, Scope newScope) {
    declarationsBlock = parent.subScope(newScope);
    body = parent.subScope(newScope);
  }

  /**
   * Declare a variable, preferably named {@code preferredName} but renamed if necessary to avoid
   * collisions, in this block's preamble, returning an Excerpt to use it. Duplicate declarations
   * will be elided.
   *
   * @throws IllegalStateException if {@code preferredName} has already been added to this block
   *     with a different declaration
   */
  public Excerpt declare(Excerpt typeAndPreamble, String preferredName, Excerpt value) {
    String name;
    if (variableNames.containsKey(preferredName)) {
      name = variableNames.get(preferredName);
      Excerpt declaration = Excerpts.add("%s %s = %s;%n", typeAndPreamble, name, value);
      Excerpt existingDeclaration = declarations.get(name);
      checkState(declaration.equals(existingDeclaration),
          "Incompatible declaration for '%s': %s vs %s",
          name,
          declaration,
          existingDeclaration);
    } else {
      name = pickName(preferredName);
      variableNames.put(preferredName, name);
      body.scope().add(new VariableName(name));
      Excerpt declaration = Excerpts.add("%s %s = %s;%n", typeAndPreamble, name, value);
      declarations.put(name, declaration);
      declarationsBlock.add(declaration);
    }
    return Excerpts.add("%s", name);
  }

  /**
   * Contains variable declaration to an inner block.
   */
  public Block innerBlock() {
    Scope innerScope = new Scope.MethodScope(scope());
    return new Block(this, innerScope);
  }

  private String pickName(String preferredName) {
    if (!nameCollides(preferredName)) {
      return preferredName;
    }
    if (!nameCollides("_" + preferredName)) {
      return "_" + preferredName;
    }
    int suffix = 2;
    while (nameCollides("_" + preferredName + suffix)) {
      suffix++;
    }
    return "_" + preferredName + suffix;
  }

  private boolean nameCollides(String name) {
    return body.scope().contains(new VariableName(name))
        || body.scope().contains(new FieldAccess(name));
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
  public SourceStringBuilder subScope(Scope newScope) {
    return body.subScope(newScope);
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
    source.add("%s%s", declarationsBlock, body);
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("declarations", declarations);
    fields.add("body", body.toString());
  }
}