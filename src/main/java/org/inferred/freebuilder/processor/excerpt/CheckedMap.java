package org.inferred.freebuilder.processor.excerpt;

import static org.inferred.freebuilder.processor.util.FunctionalType.BI_CONSUMER;

import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.LazyName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Excerpts defining a map implementation that delegates to a provided put method to perform entry
 * validation and insertion into a backing map.
 */
public class CheckedMap extends Excerpt {

  public static final LazyName TYPE = new LazyName("CheckedMap", new CheckedMap());

  private static class CheckedEntry extends Excerpt {

    static final LazyName TYPE = new LazyName("CheckedEntry", new CheckedEntry());

    private CheckedEntry() {}

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static class %s<K, V> implements %s<K, V> {", TYPE, Map.Entry.class)
          .addLine("")
          .addLine("  private final %s<K, V> entry;", Map.Entry.class)
          .addLine("  private final %s<K, V> put;", BI_CONSUMER)
          .addLine("")
          .addLine("  %s(%s<K, V> entry, %s<K, V> put) {", TYPE, Map.Entry.class, BI_CONSUMER)
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
          .addLine("    %s.requireNonNull(value);", Objects.class)
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

    @Override
    protected void addFields(FieldReceiver fields) {}
  }

  private static class CheckedEntryIterator extends Excerpt {

    static final LazyName TYPE = new LazyName("CheckedEntryIterator", new CheckedEntryIterator());

    private CheckedEntryIterator() {}

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static class %s<K, V> implements %s<%s<K, V>> {",
              TYPE, Iterator.class, Map.Entry.class)
          .addLine("")
          .addLine("  private final %s<%s<K, V>> iterator;", Iterator.class, Map.Entry.class)
          .addLine("  private final %s<K, V> put;", BI_CONSUMER)
          .addLine("")
          .addLine("  %s(", TYPE)
          .addLine("      %s<%s<K, V>> iterator,", Iterator.class, Map.Entry.class)
          .addLine("      %s<K, V> put) {", BI_CONSUMER)
          .addLine("    this.iterator = iterator;")
          .addLine("    this.put = put;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean hasNext() {")
          .addLine("    return iterator.hasNext();")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public %s<K, V> next() {", Map.Entry.class)
          .addLine("    return new %s<K, V>(iterator.next(), put);", CheckedEntry.TYPE)
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public void remove() {")
          .addLine("    iterator.remove();")
          .addLine("  }")
          .addLine("}");
    }

    @Override
    protected void addFields(FieldReceiver fields) {}
  }

  private static class CheckedEntrySet extends Excerpt {

    static final LazyName TYPE = new LazyName("CheckedEntrySet", new CheckedEntrySet());

    private CheckedEntrySet() {}

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static class %s<K, V> extends %s<%s<K, V>> {",
              TYPE, AbstractSet.class, Map.Entry.class)
          .addLine("")
          .addLine("  private final %s<%s<K, V>> set;", Set.class, Map.Entry.class)
          .addLine("  private final %s<K, V> put;", BI_CONSUMER)
          .addLine("")
          .addLine("  %s(%s<%s<K, V>> set, %s<K, V> put) {",
              TYPE, Set.class, Map.Entry.class, BI_CONSUMER)
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
          .addLine("    return new %s<K, V>(set.iterator(), put);", CheckedEntryIterator.TYPE)
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

    @Override
    protected void addFields(FieldReceiver fields) {}
  }

  private CheckedMap() {}

  @Override
  public void addTo(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * A map implementation that delegates to a provided put method")
        .addLine(" * to perform entry validation and insertion into a backing map.")
        .addLine(" */")
        .addLine("private static class %s<K, V> extends %s<K, V> {", TYPE, AbstractMap.class)
        .addLine("")
        .addLine("  private final %s<K, V> map;", Map.class)
        .addLine("  private final %s<K, V> put;", BI_CONSUMER)
        .addLine("")
        .addLine("  %s(%s<K, V> map, %s<K, V> put) {", TYPE, Map.class, BI_CONSUMER)
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
        .addLine("    return new %s<>(map.entrySet(), put);", CheckedEntrySet.TYPE)
        .addLine("  }")
        .addLine("}");
  }

  @Override
  protected void addFields(FieldReceiver fields) {}
}