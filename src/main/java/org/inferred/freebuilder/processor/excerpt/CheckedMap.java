package org.inferred.freebuilder.processor.excerpt;

import static org.inferred.freebuilder.processor.util.StaticExcerpt.Type.TYPE;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.util.ParameterizedType;
import org.inferred.freebuilder.processor.util.PreconditionExcerpts;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.StaticExcerpt;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Excerpts defining a map implementation that delegates to a provided put method to perform entry
 * validation and insertion into a backing map.
 */
public class CheckedMap {

  public static final Set<StaticExcerpt> excerpts() {
    return ImmutableSet.of(CHECKED_ENTRY, CHECKED_ENTRY_ITERATOR, CHECKED_ENTRY_SET, CHECKED_MAP);
  }

  private static final StaticExcerpt CHECKED_ENTRY = new StaticExcerpt(TYPE, "CheckedEntry") {
    @Override
    public void addTo(SourceBuilder code) {
      ParameterizedType biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer().orNull();
      if (biConsumer == null) {
        return;
      }
      code.addLine("")
          .addLine("private static class CheckedEntry<K, V> implements %s<K, V> {",
              Map.Entry.class)
          .addLine("")
          .addLine("  private final %s<K, V> entry;", Map.Entry.class)
          .addLine("  private final %s<K, V> put;", biConsumer.getQualifiedName())
          .addLine("")
          .addLine("  CheckedEntry(%s<K, V> entry, %s<K, V> put) {",
              Map.Entry.class, biConsumer.getQualifiedName())
          .addLine("    this.entry = entry;")
          .addLine("    this.put = put;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public K getKey() {")
          .addLine("    return entry.getKey();")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public V getValue() {")
          .addLine("    return entry.getValue();")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public V setValue(V value) {")
          .add(PreconditionExcerpts.checkNotNull("value"))
          .addLine("    V oldValue = entry.getValue();")
          .addLine("    put.accept(entry.getKey(), value);")
          .addLine("    return oldValue;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean equals(Object o) {")
          .addLine("    return entry.equals(o);")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public int hashCode() {")
          .addLine("    return entry.hashCode();")
          .addLine("  }")
          .addLine("}");
    }
  };

  private static final StaticExcerpt CHECKED_ENTRY_ITERATOR =
      new StaticExcerpt(TYPE, "CheckedEntryIterator") {
        @Override
        public void addTo(SourceBuilder code) {
          ParameterizedType biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer().orNull();
          if (biConsumer == null) {
            return;
          }
          code.addLine("")
              .addLine("private static class CheckedEntryIterator<K, V> implements %s<%s<K, V>> {",
                  Iterator.class, Map.Entry.class)
              .addLine("")
              .addLine("  private final %s<%s<K, V>> iterator;", Iterator.class, Map.Entry.class)
              .addLine("  private final %s<K, V> put;", biConsumer.getQualifiedName())
              .addLine("")
              .addLine("  CheckedEntryIterator(")
              .addLine("      %s<%s<K, V>> iterator,", Iterator.class, Map.Entry.class)
              .addLine("      %s<K, V> put) {", biConsumer.getQualifiedName())
              .addLine("    this.iterator = iterator;")
              .addLine("    this.put = put;")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean hasNext() {")
              .addLine("    return iterator.hasNext();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public %s<K, V> next() {", Map.Entry.class)
              .addLine("    return new CheckedEntry<K, V>(iterator.next(), put);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public void remove() {")
              .addLine("    iterator.remove();")
              .addLine("  }")
              .addLine("}");
        }
      };

  private static final StaticExcerpt CHECKED_ENTRY_SET =
      new StaticExcerpt(TYPE, "CheckedEntrySet") {
        @Override
        public void addTo(SourceBuilder code) {
          ParameterizedType biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer().orNull();
          if (biConsumer == null) {
            return;
          }
          code.addLine("")
              .addLine("private static class CheckedEntrySet<K, V> extends %s<%s<K, V>> {",
                  AbstractSet.class, Map.Entry.class)
              .addLine("")
              .addLine("  private final %s<%s<K, V>> set;", Set.class, Map.Entry.class)
              .addLine("  private final %s<K, V> put;", biConsumer.getQualifiedName())
              .addLine("")
              .addLine("  CheckedEntrySet(%s<%s<K, V>> set, %s<K, V> put) {",
                  Set.class, Map.Entry.class, biConsumer.getQualifiedName())
              .addLine("    this.set = set;")
              .addLine("    this.put = put;")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public int size() {")
              .addLine("    return set.size();")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public %s<%s<K, V>> iterator() {",
                  Iterator.class, Map.Entry.class)
              .addLine("    return new CheckedEntryIterator<K, V>(set.iterator(), put);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean contains(Object o) {")
              .addLine("    return set.contains(o);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public boolean remove(Object o) {")
              .addLine("    return set.remove(o);")
              .addLine("  }")
              .addLine("")
              .addLine("  @Override public void clear() {")
              .addLine("    set.clear();")
              .addLine("  }")
              .addLine("}");
        }
      };

  private static final StaticExcerpt CHECKED_MAP = new StaticExcerpt(TYPE, "CheckedMap") {
    @Override
    public void addTo(SourceBuilder code) {
      ParameterizedType biConsumer = code.feature(FUNCTION_PACKAGE).biConsumer().orNull();
      if (biConsumer == null) {
        return;
      }
      code.addLine("")
          .addLine("/**")
          .addLine(" * A map implementation that delegates to a provided put method")
          .addLine(" * to perform entry validation and insertion into a backing map.")
          .addLine(" */")
          .addLine("private static class CheckedMap<K, V> extends %s<K, V> {",
              AbstractMap.class)
          .addLine("")
          .addLine("  private final %s<K, V> map;", Map.class)
          .addLine("  private final %s<K, V> put;", biConsumer.getQualifiedName())
          .addLine("")
          .addLine("  CheckedMap(%s<K, V> map, %s<K, V> put) {",
              Map.class, biConsumer.getQualifiedName())
          .addLine("    this.map = map;")
          .addLine("    this.put = put;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public V get(Object key) {")
          .addLine("    return map.get(key);")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean containsKey(Object key) {")
          .addLine("    return map.containsKey(key);")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public V put(K key, V value) {")
          .addLine("    V oldValue = map.get(key);")
          .addLine("    put.accept(key, value);")
          .addLine("    return oldValue;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public V remove(Object key) {")
          .addLine("    return map.remove(key);")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public void clear() {")
          .addLine("    map.clear();")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public %s<%s<K, V>> entrySet() {",
              Set.class, Map.Entry.class)
          .addLine("    return new CheckedEntrySet<>(map.entrySet(), put);")
          .addLine("  }")
          .addLine("}");
    }
  };
}