package org.inferred.freebuilder.processor.excerpt;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Excerpts defining a set implementation that delegates to a provided add method to perform
 * element validation and insertion into a backing set.
 */
public class CheckedSet extends Excerpt {

  public static final LazyName TYPE = LazyName.of("CheckedSet", new CheckedSet());

  private CheckedSet() {}

  @Override
  public void addTo(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * A set implementation that delegates to a provided add method")
        .addLine(" * to perform element validation and insertion into a backing set.")
        .addLine(" */")
        .addLine("private static class %s<E> extends %s<E> {", TYPE, AbstractSet.class)
        .addLine("")
        .addLine("  private final %s<E> set;", Set.class)
        .addLine("  private final %s<E> add;", Consumer.class)
        .addLine("")
        .addLine("  %s(%s<E> set, %s<E> add) {", TYPE, Set.class, Consumer.class)
        .addLine("    this.set = set;")
        .addLine("    this.add = add;")
        .addLine("  }")
        .addLine("")
        .addLine("")
        .addLine("  @Override public %s<E> iterator() {", Iterator.class)
        .addLine("    return set.iterator();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public int size() {")
        .addLine("    return set.size();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public boolean contains(Object e) {")
        .addLine("    return set.contains(e);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public boolean add(E e) {")
        .addLine("    if (!set.contains(e)) {")
        .addLine("      add.accept(e);")
        .addLine("      return true;")
        .addLine("    } else {")
        .addLine("      return false;")
        .addLine("    }")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public boolean remove(Object e) {")
        .addLine("    return set.remove(e);")
        .addLine("  }")
        .addLine("}");
  }

  @Override
  protected void addFields(FieldReceiver fields) {}
}
