package org.inferred.freebuilder.processor.excerpt;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import org.inferred.freebuilder.processor.source.Excerpt;
import org.inferred.freebuilder.processor.source.LazyName;
import org.inferred.freebuilder.processor.source.SourceBuilder;
import org.inferred.freebuilder.processor.source.ValueType;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

public class CheckedBiMap extends ValueType implements Excerpt {

  public static final LazyName TYPE = LazyName.of("CheckedBiMap", new CheckedBiMap());

  private CheckedBiMap() {
  }

  @Override
  public void addTo(SourceBuilder code) {
    code.addLine("")
        .addLine("/**")
        .addLine(" * A bimap implementation that delegates to a provided put method")
        .addLine(" * to perform entry validation and insertion into a backing bimap.")
        .addLine(" */")
        .addLine("private static class %s<K, V> extends %s<K, V> implements %s<K, V> {",
            TYPE, AbstractMap.class, BiMap.class
        )
        .addLine("")
        .addLine("  private final %s<K, V> biMap;", BiMap.class)
        .addLine("  private final %s<K, V> put;", BiConsumer.class)
        .addLine("")
        .addLine("  %s(%s<K, V> biMap, %s<K, V> put) {", TYPE, BiMap.class, BiConsumer.class)
        .addLine("    this.biMap = biMap;")
        .addLine("    this.put = put;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public V get(Object key) {")
        .addLine("    return biMap.get(key);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public boolean containsKey(Object key) {")
        .addLine("    return biMap.containsKey(key);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public V put(K key, V value) {")
        .addLine(
            "  %s.checkArgument(!biMap.containsValue(value), "
                + "\"value already present: %s\","
                + " value);",
            Preconditions.class,
            "%s"
        )
        .addLine("    V oldValue = biMap.get(key);")
        .addLine("    put.accept(key, value);")
        .addLine("    return oldValue;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public V forcePut(K key, V value) {")
        .addLine("    V oldValue = biMap.get(key);")
        .addLine("    put.accept(key, value);")
        .addLine("    return oldValue;")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public V remove(Object key) {")
        .addLine("    return biMap.remove(key);")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public void clear() {")
        .addLine("    biMap.clear();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<%s<K, V>> entrySet() {",
            Set.class, Map.Entry.class
        )
        .addLine("    return new %s<>(biMap, put);", CheckedBiMap.CheckedEntrySet.TYPE)
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<V,K> inverse() {", BiMap.class)
        .addLine("    return new %s<V, K>(biMap.inverse(), "
            + "(value, key) -> put.accept(key, value));",
            TYPE
        )
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public %s<V> values() {", Set.class)
        .addLine("    return biMap.values();")
        .addLine("  }")
        .addLine("")
        .addLine("  @Override public void putAll(%s<? extends K, ? extends V> map) {", Map.class)
        .addLine("    for (%s<? extends K, ? extends V> entry : map.entrySet()) {", Map.Entry.class)
        .addLine("      put(entry.getKey(), entry.getValue());")
        .addLine("    }")
        .addLine("  }")
        .addLine("}");
  }

  @Override
  protected void addFields(FieldReceiver fields) {
  }

  private static class CheckedEntry extends ValueType implements Excerpt {

    static final LazyName TYPE = LazyName.of("CheckedEntry", new CheckedBiMap.CheckedEntry());

    private CheckedEntry() {
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static class %s<K, V> implements %s<K, V> {", TYPE, Map.Entry.class)
          .addLine("")
          .addLine("  private final %s<K, V> biMap;", BiMap.class)
          .addLine("  private final K key;")
          .addLine("  private V value;")
          .addLine("  private final %s<K, V> put;", BiConsumer.class)
          .addLine("")
          .addLine("  %s(", TYPE)
          .addLine("      %s<K, V> biMap,", BiMap.class)
          .addLine("      %s<K, V> entry, ", Map.Entry.class)
          .addLine("      %s<K, V> put) {", BiConsumer.class)
          .addLine("    this.biMap = biMap;")
          .addLine("    this.key = entry.getKey();")
          .addLine("    this.value = entry.getValue();")
          .addLine("    this.put = put;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public K getKey() {")
          .addLine("    return key;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public V getValue() {")
          .addLine("    return value;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public V setValue(V value) {")
          .addLine(
              "  %s.checkArgument(!biMap.containsValue(value), "
                  + "\"value already present: %s\","
                  + " value);",
              Preconditions.class,
              "%s"
          )
          .addLine("    V oldValue = value;")
          .addLine("    this.value = %s.requireNonNull(value);", Objects.class)
          .addLine("    put.accept(key, value);")
          .addLine("    return oldValue;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean equals(Object o) {")
          .addLine("    if (!(o instanceof %s)) {", TYPE)
          .addLine("      return false;")
          .addLine("    }")
          .addLine("    final %1$s other = (%1$s) o;", TYPE)
          .addLine("    return getKey().equals(other.getKey())")
          .addLine("        && getValue().equals(other.getValue());")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public int hashCode() {")
          .addLine("    return %s.hash(key, value);", Objects.class)
          .addLine("  }")
          .addLine("}");
    }

    @Override
    protected void addFields(FieldReceiver fields) {
    }
  }

  private static class CheckedEntryIterator extends ValueType implements Excerpt {

    static final LazyName TYPE = LazyName.of(
        "CheckedEntryIterator",
        new CheckedBiMap.CheckedEntryIterator());

    private CheckedEntryIterator() {
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static class %s<K, V> implements %s<%s<K, V>> {",
              TYPE, Iterator.class, Map.Entry.class
          )
          .addLine("")
          .addLine("  private final %s<K, V> biMap;", BiMap.class)
          .addLine("  private final %s<%s<K, V>> iterator;", Iterator.class, Map.Entry.class)
          .addLine("  private final %s<K, V> put;", BiConsumer.class)
          .addLine("")
          .addLine("  %s(", TYPE)
          .addLine("      %s<K, V> biMap,", BiMap.class)
          .addLine("      %s<%s<K, V>> iterator,", Iterator.class, Map.Entry.class)
          .addLine("      %s<K, V> put) {", BiConsumer.class)
          .addLine("    this.biMap = biMap;")
          .addLine("    this.iterator = iterator;")
          .addLine("    this.put = put;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public boolean hasNext() {")
          .addLine("    return iterator.hasNext();")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public %s<K, V> next() {", Map.Entry.class)
          .addLine("    return new %s<K, V>(biMap, iterator.next(), put);",
              CheckedBiMap.CheckedEntry.TYPE)
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public void remove() {")
          .addLine("    iterator.remove();")
          .addLine("  }")
          .addLine("}");
    }

    @Override
    protected void addFields(FieldReceiver fields) {
    }
  }

  private static class CheckedEntrySet extends ValueType implements Excerpt {

    static final LazyName TYPE = LazyName.of("CheckedEntrySet", new CheckedBiMap.CheckedEntrySet());

    private CheckedEntrySet() {
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("")
          .addLine("private static class %s<K, V> extends %s<%s<K, V>> {",
              TYPE, AbstractSet.class, Map.Entry.class
          )
          .addLine("")
          .addLine("  private final %s<K, V> biMap;", BiMap.class)
          .addLine("  private final %s<%s<K, V>> set;", Set.class, Map.Entry.class)
          .addLine("  private final %s<K, V> put;", BiConsumer.class)
          .addLine("")
          .addLine("  %s(", TYPE)
          .addLine("      %s<K, V> biMap,", BiMap.class)
          .addLine("      %s<K, V> put) {", BiConsumer.class)
          .addLine("    this.biMap = biMap;")
          .addLine("    this.set = biMap.entrySet();")
          .addLine("    this.put = put;")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public int size() {")
          .addLine("    return set.size();")
          .addLine("  }")
          .addLine("")
          .addLine("  @Override public %s<%s<K, V>> iterator() {",
              Iterator.class, BiMap.Entry.class
          )
          .addLine(
              "    return new %s<K, V>(biMap, set.iterator(), put);",
              CheckedBiMap.CheckedEntryIterator.TYPE
          )
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
    protected void addFields(FieldReceiver fields) {
    }
  }
}