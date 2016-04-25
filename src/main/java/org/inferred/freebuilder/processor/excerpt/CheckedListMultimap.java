package org.inferred.freebuilder.processor.excerpt;

import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ForwardingListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Excerpts defining a multimap implementation that delegates to a provided put method to perform
 * entry validation and insertion into a backing multimap.
 */
public class CheckedListMultimap {

  public static List<Excerpt> excerpts() {
    return ImmutableList.<Excerpt>builder()
        .addAll(CheckedList.excerpts())
        .add(CHECKED_LIST_MULTIMAP)
        .build();
  }

  private static final Excerpt CHECKED_LIST_MULTIMAP = new Excerpt() {
    @Override
    public void addTo(SourceBuilder code) {
      ParameterizedType biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer().orNull();
      if (biConsumer == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * A multimap implementation that delegates to a provided put method")
          .addLine(" * to perform entry validation and insertion into a backing multimap.")
          .addLine(" */")
          .addLine("private static class CheckedListMultimap<K, V> extends %s<K, V> {",
              ForwardingListMultimap.class)
          .addLine("")
          .addLine("  private final %s<K, V> multimap;", ListMultimap.class)
          .addLine("  private final %s<K, V> put;", biConsumer.getQualifiedName())
          .addLine("")
          .addLine("  CheckedListMultimap(%s<K, V> multimap, %s<K, V> put) {",
              ListMultimap.class, biConsumer.getQualifiedName())
          .addLine("    this.multimap = multimap;")
          .addLine("    this.put = put;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override protected %s<K, V> delegate() {", ListMultimap.class)
          .addLine("    return multimap;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean put(@%1$s K key, @%1$s V value) {", Nullable.class)
          .addLine("    put.accept(key, value);")
          .addLine("    return true;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean putAll(@%s K key, %s<? extends V> values) {",
              Nullable.class, Iterable.class)
          .addLine("    boolean anyModified = false;")
          .addLine("    for (V value : values) {")
          .addLine("      put.accept(key, value);")
          .addLine("      anyModified = true;")
          .addLine("    }")
          .addLine("    return anyModified;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean putAll(%s<? extends K, ? extends V> multimap) {",
              Multimap.class)
          .addLine("    boolean changed = false;")
          .addLine("    for (%s<? extends K, ? extends V> entry : multimap.entries()) {",
              Map.Entry.class)
          .addLine("      put.accept(entry.getKey(), entry.getValue());")
          .addLine("      changed = true;")
          .addLine("    }")
          .addLine("    return changed;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override")
          .addLine("  public %s<V> replaceValues(@%s K key, %s<? extends V> values) {",
              List.class, Nullable.class, Iterable.class)
          .addLine("    %s.checkNotNull(values);", Preconditions.class)
          .addLine("    %s<V> result = removeAll(key);", List.class)
          .addLine("    putAll(key, values);")
          .addLine("    return result;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public %s<V> get(@%s K key) {", List.class, Nullable.class)
          .addLine("    return new CheckedList<>(")
          .addLine("        multimap.get(key), value -> put.accept(key, value));")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public %s<K, %s<V>> asMap() {", Map.class, Collection.class)
          .addLine("    return %s.transformEntries(%s.asMap(multimap), (key, values) -> ",
              Maps.class, Multimaps.class)
          .addLine("        new CheckedList<>(values, value -> put.accept(key, value)));")
          .addLine("  }")
          .addLine("}");
    }
  };

  private CheckedListMultimap() {}
}