package org.inferred.freebuilder.processor.excerpt;

import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;

/**
 * Excerpts defining a navigable set implementation that delegates to a provided add method to
 * perform element validation and insertion into a backing set.
 */
public class CheckedNavigableSet extends Excerpt {

  public static final LazyName TYPE =
      new LazyName("CheckedNavigableSet", new CheckedNavigableSet());

  private CheckedNavigableSet() {}

  @Override
  public void addTo(SourceBuilder code) {
    ParameterizedType consumer = code.feature(FUNCTION_PACKAGE).consumer().get();
    code.addLine("")
        .addLine("/**")
        .addLine(" * A set implementation that delegates to a provided add method")
        .addLine(" * to perform element validation and insertion into a backing set.")
        .addLine(" */")
        .addLine("private static class %s<E> extends %s<E> implements %s<E> {",
            TYPE, AbstractSet.class, NavigableSet.class)
        .addLine("")
        .addLine("  private final %s<E> set;", NavigableSet.class)
        .addLine("  private final %s<E> add;", consumer.getQualifiedName())
        .addLine("  private final E fromElement;")
        .addLine("  private final boolean fromInclusive;")
        .addLine("  private final E toElement;")
        .addLine("  private final boolean toInclusive;")
        .addLine("")
        .addLine("  %s(%s<E> set, %s<E> add) {",
            TYPE, NavigableSet.class, consumer.getQualifiedName())
        .addLine("    this.set = set;")
        .addLine("    this.add = add;")
        .addLine("    this.fromElement = null;")
        .addLine("    this.fromInclusive = false;")
        .addLine("    this.toElement = null;")
        .addLine("    this.toInclusive = false;")
        .addLine("  }")
        .addLine("")
        .addLine("  %s(", TYPE)
        .addLine("      %s<E> set,", NavigableSet.class)
        .addLine("      %s<E> add,", consumer.getQualifiedName())
        .addLine("      E fromElement,")
        .addLine("      boolean fromInclusive,")
        .addLine("      E toElement,")
        .addLine("      boolean toInclusive) {")
        .addLine("    this.set = set;")
        .addLine("    this.add = add;")
        .addLine("    this.fromElement = fromElement;")
        .addLine("    this.fromInclusive = fromInclusive;")
        .addLine("    this.toElement = toElement;")
        .addLine("    this.toInclusive = toInclusive;")
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
        .addLine("  }");
    addAddMethod(code);
    code.addLine("")
        .addLine("  @Override public boolean remove(Object e) {")
        .addLine("    return set.remove(e);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<? super E> comparator() {", Comparator.class)
        .addLine("    return set.comparator();")
        .addLine("  }");
    addSubSetMethod(code);
    code.addLine("")
        .addLine("  @Override public E first() {")
        .addLine("    return set.first();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E last() {")
        .addLine("    return set.last();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E lower(E element) {")
        .addLine("    return set.lower(element);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E floor(E element) {")
        .addLine("    return set.floor(element);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E ceiling(E element) {")
        .addLine("    return set.ceiling(element);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E higher(E element) {")
        .addLine("    return set.higher(element);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E pollFirst() {")
        .addLine("    return set.pollFirst();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E pollLast() {")
        .addLine("    return set.pollLast();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<E> descendingSet() {", NavigableSet.class)
        .addLine("    %s<E> descendingSet = set.descendingSet();", NavigableSet.class)
        .addLine("    return new %s(", TYPE)
        .addLine("        descendingSet, add, toElement, toInclusive, fromElement, fromInclusive);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<E> descendingIterator() {", Iterator.class)
        .addLine("    return set.descendingIterator();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<E> subSet(", NavigableSet.class)
        .addLine("      E fromElement,")
        .addLine("      boolean fromInclusive,")
        .addLine("      E toElement,")
        .addLine("      boolean toInclusive) {")
        .add(PreconditionExcerpts.checkNotNull("fromElement"))
        .add(PreconditionExcerpts.checkNotNull("toElement"))
        .addLine("    %s<E> subSet = set.subSet(", NavigableSet.class)
        .addLine("        fromElement, fromInclusive, toElement, toInclusive);")
        .addLine("    return new %s<>(", TYPE)
        .addLine("        subSet, add, fromElement, fromInclusive, toElement, toInclusive);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<E> headSet(", NavigableSet.class)
        .addLine("      E toElement,")
        .addLine("      boolean inclusive) {")
        .add(PreconditionExcerpts.checkNotNull("toElement"))
        .addLine("    %s<E> headSet = set.headSet(toElement, inclusive);",
            NavigableSet.class)
        .addLine("    return new %s<>(", TYPE)
        .addLine("        headSet, add, fromElement, fromInclusive, toElement, inclusive);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<E> tailSet(", NavigableSet.class)
        .addLine("      E fromElement,")
        .addLine("      boolean inclusive) {")
        .add(PreconditionExcerpts.checkNotNull("fromElement"))
        .addLine("    %s<E> tailSet = set.tailSet(fromElement, inclusive);",
            NavigableSet.class)
        .addLine("    return new %s<>(", TYPE)
        .addLine("        tailSet, add, fromElement, inclusive, toElement, toInclusive);")
        .addLine("  }")
        .addLine("}");
  }

  private static void addSubSetMethod(SourceBuilder code) {
    code.addLine("")
        .addLine("  @Override public %s<E> subSet(E fromElement, E toElement) {",
            NavigableSet.class)
        .add(PreconditionExcerpts.checkNotNull("fromElement"))
        .add(PreconditionExcerpts.checkNotNull("toElement"))
        .addLine("    %s<E> subSet = set.subSet(fromElement, true, toElement, false);",
            NavigableSet.class)
        .addLine("    return new %s<>(", TYPE)
        .addLine("        subSet, add, fromElement, true, toElement, false);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<E> headSet(E toElement) {", NavigableSet.class)
        .add(PreconditionExcerpts.checkNotNull("toElement"))
        .addLine("    %s<E> headSet = set.headSet(toElement, false);", NavigableSet.class)
        .addLine("    return new %s<>(", TYPE)
        .addLine("        headSet, add, fromElement, fromInclusive, toElement, false);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<E> tailSet(E fromElement) {", NavigableSet.class)
        .add(PreconditionExcerpts.checkNotNull("fromElement"))
        .addLine("    %s<E> tailSet = set.tailSet(fromElement, true);", NavigableSet.class)
        .addLine("    return new %s<>(", TYPE)
        .addLine("        tailSet, add, fromElement, true, toElement, toInclusive);")
        .addLine("  }");
  }

  private static void addAddMethod(SourceBuilder code) {
    code.addLine("")
        .addLine("  @Override public boolean add(E e) {")
        .addLine("    if (fromElement != null || toElement != null) {")
        .addLine("      %s<? super E> comparator = set.comparator();", Comparator.class)
        .addLine("      if (comparator == null) {")
        .addLine("        @SuppressWarnings(\"unchecked\")")
        .addLine("        %1$s<? super E> lowerBound = (%1$s<? super E>) fromElement;",
            Comparable.class)
        .addLine("        @SuppressWarnings(\"unchecked\")")
        .addLine("        %1$s<? super E> upperBound = (%1$s<? super E>) toElement;",
            Comparable.class)
        .add(PreconditionExcerpts.checkArgument(
            "lowerBound == null || lowerBound.compareTo(e) <= (fromInclusive ? 0 : -1)",
            "element must be %s %s (got %s)",
            "(fromInclusive ? \"at least\" : \"greater than\")",
            "lowerBound",
            "e"))
        .add(PreconditionExcerpts.checkArgument(
            "upperBound == null || upperBound.compareTo(e) >= (toInclusive ? 0 : 1)",
            "element must be %s %s (got %s)",
            "(toInclusive ? \"at most\" : \"less than\")",
            "upperBound",
            "e"))
        .addLine("      } else {")
        .add(PreconditionExcerpts.checkArgument(
            "fromElement == null "
                + "|| comparator.compare(fromElement, e) <= (fromInclusive ? 0 : -1)",
            "element must be %s %s (got %s) using comparator %s",
            "(fromInclusive ? \"at least\" : \"greater than\")",
            "fromElement",
            "e",
            "comparator"))
        .add(PreconditionExcerpts.checkArgument(
            "toElement == null "
                + "|| comparator.compare(toElement, e) >= (toInclusive ? 0 : 1)",
            "element must be %s %s (got %s) using comparator %s",
            "(toInclusive ? \"at most\" : \"less than\")",
            "toElement",
            "e",
            "comparator"))
        .addLine("      }")
        .addLine("    }")
        .addLine("    if (!set.contains(e)) {")
        .addLine("      add.accept(e);")
        .addLine("      return true;")
        .addLine("    } else {")
        .addLine("      return false;")
        .addLine("    }")
        .addLine("  }");
  }

  @Override
  protected void addFields(FieldReceiver fields) {}
}
