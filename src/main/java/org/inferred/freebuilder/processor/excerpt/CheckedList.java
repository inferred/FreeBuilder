package org.inferred.freebuilder.processor.excerpt;

import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.LazyName;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.ValueType;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;
import java.util.function.Consumer;

/**
 * Excerpts defining a list implementation that delegates to a provided add method to perform
 * element validation and insertion into a random-access backing list.
 */
public class CheckedList extends ValueType implements Excerpt {

  public static final LazyName TYPE = LazyName.of("CheckedList", new CheckedList());

  private CheckedList() {}

  @Override
  public void addTo(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * A list implementation that delegates to a provided add method to perform")
        .addLine(" * element validation and insertion into a random-access backing list.")
        .addLine(" */")
        .addLine("private static class %s<E> extends %s<E> implements %s {",
            TYPE, AbstractList.class, RandomAccess.class)
        .addLine("")
        .addLine("  private final %s<E> list;", List.class)
        .addLine("  private final %s<E> add;", Consumer.class)
        .addLine("")
        .addLine("  %s(%s<E> list, %s<E> add) {", TYPE, List.class, Consumer.class)
        .addLine("    this.list = list;")
        .addLine("    this.add = add;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public int size() {")
        .addLine("    return list.size();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E get(int index) {")
        .addLine("    return list.get(index);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E set(int index, E element) {")
        .addLine("    add.accept(element);")
        .addLine("    return list.set(index, list.remove(list.size() - 1));")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public void add(int index, E element) {")
        .addLine("    // Append to the end of the list with add, then move the inserted element")
        .addLine("    // to the desired location.")
        .addLine("    int endIndex = list.size();")
        .addLine("    add.accept(element);")
        .addLine("    if (index != endIndex) {")
        .addLine("      list.add(index, list.remove(endIndex));")
        .addLine("    }")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public E remove(int index) {")
        .addLine("    return list.remove(index);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public void clear() {")
        .addLine("    list.clear();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override protected void removeRange(int fromIndex, int toIndex) {")
        .addLine("    list.subList(fromIndex, toIndex).clear();")
        .addLine("  }")
        .addLine("}");
  }

  @Override
  protected void addFields(FieldReceiver fields) {}
}
