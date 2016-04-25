package org.inferred.freebuilder.processor.excerpt;

import static org.inferred.freebuilder.processor.util.StaticExcerpt.Type.TYPE;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.StaticExcerpt;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

/**
 * Excerpts defining a list implementation that delegates to a provided add method to perform
 * element validation and insertion into a random-access backing list.
 */
public class CheckedList {

  public static List<StaticExcerpt> excerpts() {
    return ImmutableList.<StaticExcerpt>of(CHECKED_LIST);
  }

  private static final StaticExcerpt CHECKED_LIST = new StaticExcerpt(TYPE, "CheckedList") {
    @Override
    public void addTo(SourceBuilder code) {
      ParameterizedType consumer = code.feature(FUNCTION_PACKAGE).consumer().orNull();
      if (consumer == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * A list implementation that delegates to a provided add method to perform")
          .addLine(" * element validation and insertion into a random-access backing list.")
          .addLine(" */")
          .addLine("private static class CheckedList<E> extends %s<E> implements %s {",
              AbstractList.class, RandomAccess.class)
          .addLine("")
          .addLine("  private final %s<E> list;", List.class)
          .addLine("  private final %s<E> add;", consumer.getQualifiedName())
          .addLine("")
          .addLine("  CheckedList(%s<E> list, %s<E> add) {",
              List.class, consumer.getQualifiedName())
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
  };

  private CheckedList() {}
}