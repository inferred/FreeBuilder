package org.inferred.freebuilder.processor.excerpt;

import com.google.common.base.Preconditions;
import com.google.common.collect.ForwardingListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.ValueType;
import org.inferred.freebuilder.processor.util.feature.Jsr305;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Excerpts defining a multimap implementation that delegates to a provided put method to perform
 * entry validation and insertion into a backing multimap.
 */
public class CheckedListMultimap extends ValueType implements Excerpt {

  public static final LazyName TYPE =
      LazyName.of("CheckedListMultimap", new CheckedListMultimap());

  private CheckedListMultimap() {}

  @Override
  public void addTo(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * A multimap implementation that delegates to a provided put method")
        .addLine(" * to perform entry validation and insertion into a backing multimap.")
        .addLine(" */")
        .addLine("private static class %s<K, V> extends %s<K, V> {",
            TYPE, ForwardingListMultimap.class)
        .addLine("")
        .addLine("  private final %s<K, V> multimap;", ListMultimap.class)
        .addLine("  private final %s<K, V> put;", BiConsumer.class)
        .addLine("")
        .addLine("  %s(%s<K, V> multimap, %s<K, V> put) {",
            TYPE, ListMultimap.class, BiConsumer.class)
        .addLine("    this.multimap = multimap;")
        .addLine("    this.put = put;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override protected %s<K, V> delegate() {", ListMultimap.class)
        .addLine("    return multimap;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public boolean put(%1$s K key, %1$s V value) {",
            Jsr305.nullable())
        .addLine("    put.accept(key, value);")
        .addLine("    return true;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public boolean putAll(%s K key, %s<? extends V> values) {",
            Jsr305.nullable(), Iterable.class)
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
        .addLine("  public %s<V> replaceValues(%s K key, %s<? extends V> values) {",
            List.class, Jsr305.nullable(), Iterable.class)
        .addLine("    %s.checkNotNull(values);", Preconditions.class)
        .addLine("    %s<V> result = removeAll(key);", List.class)
        .addLine("    putAll(key, values);")
        .addLine("    return result;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<V> get(%s K key) {", List.class, Jsr305.nullable())
        .addLine("    return new %s<>(", CheckedList.TYPE)
        .addLine("        multimap.get(key), value -> put.accept(key, value));")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<K, %s<V>> asMap() {", Map.class, Collection.class)
        .addLine("    return %s.transformEntries(%s.asMap(multimap), (key, values) -> ",
            Maps.class, Multimaps.class)
        .addLine("        new %s<>(values, value -> put.accept(key, value)));", CheckedList.TYPE)
        .addLine("  }")
        .addLine("}");
  }

  @Override
  protected void addFields(FieldReceiver fields) {}
}
