/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor;

import static com.google.common.collect.Maps.immutableEntry;
import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Ordered;
import com.google.common.truth.Subject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

/** Basic Truth {@link Subject} for {@link Multimap} instances. */
public class MultimapSubject<K, V> extends Subject<MultimapSubject<K, V>, Multimap<K, V>> {

  public static <K, V> MultimapSubject<K, V> assertThat(Multimap<K, V> subject) {
    return new MultimapSubject<K, V>(THROW_ASSERTION_ERROR, subject);
  }

  /**
   * Fails if the subject is not empty.
   */
  public void isEmpty() {
    if (!getSubject().isEmpty()) {
      fail("is empty");
    }
  }

  /**
   * Fails if the subject is empty.
   */
  public void isNotEmpty() {
    if (getSubject().isEmpty()) {
      fail("is empty");
    }
  }

  /**
   * Fails if the subject does not contain the given key-value pair.
   *
   * <p>First part of a fluent API for containment assertions. Example:<pre><code>
   * assertThat(multimap)
   *     .contains("fore", 3.22)
   *     .and("aft", 4.4)
   *     .andNothingElse()
   *     .inOrder();</pre></code>
   */
  public ContainmentAssertion<K, V> contains(K key, V value) {
    return new InOrderContainmentAssertion().and(key, value);
  }

  public interface ContainmentAssertion<K, V> {
    /**
     * Fails if the subject does not contain the given key-value pair, <em>in addition</em> to any
     * entries found by previous {@link #contains} and {@link #and} calls.
     *
     * @see #contains
     */
    ContainmentAssertion<K, V> and(K key, V value);

    /**
     * Fails if the subject contains any entries that have not already been matched by previous
     * {@link #contains} and {@link #and} calls. Call {@link Ordered#inOrder()} on the result to
     * further assert that all entries were matched in the same order specified by the test.
     *
     * @see #contains
     */
    Ordered andNothingElse();
  }

  private MultimapSubject(FailureStrategy failureStrategy, Multimap<K, V> subject) {
    super(failureStrategy, subject);
  }

  /** Implementation of {@code ContainmentAssertion} for a subject that may still be in order. */
  private class InOrderContainmentAssertion implements ContainmentAssertion<K, V> {

    final Iterator<Entry<K, V>> entryIterator;
    final List<Entry<K, V>> expected = new ArrayList<Entry<K, V>>();

    InOrderContainmentAssertion() {
      entryIterator = getSubject().entries().iterator();
    }

    @Override public ContainmentAssertion<K, V> and(K key, V value) {
      return and(immutableEntry(key, value));
    }

    ContainmentAssertion<K, V> and(Entry<K, V> entry) {
      expected.add(entry);
      if (!entryIterator.hasNext()) {
        fail("contains", expected, "is missing", entry);
        return new FailedContainmentAssertion();
      }
      Entry<K, V> actualEntry = entryIterator.next();
      if (entry.equals(actualEntry)) {
        return this;
      }

      // The subject does not match the order specified by the user, but the entry may still be
      // present. Delegate to an OutOfOrderContainmentAssertion.
      Multimap<K, V> remaining = ArrayListMultimap.create();
      remaining.put(actualEntry.getKey(), actualEntry.getValue());
      while (entryIterator.hasNext()) {
        actualEntry = entryIterator.next();
        remaining.put(actualEntry.getKey(), actualEntry.getValue());
      }
      expected.remove(expected.size() - 1);
      return new OutOfOrderContainmentAssertion(expected, remaining).and(entry);
    }

    @Override public Ordered andNothingElse() {
      if (entryIterator.hasNext()) {
        failWithBadResults(
            "contains", expected, "has unexpected items", ImmutableList.copyOf(entryIterator));
      }
      return IN_ORDER;
    }
  }

  /** Implementation of {@code ContainmentAssertion} for a subject that is mis-ordered. */
  private class OutOfOrderContainmentAssertion implements ContainmentAssertion<K, V> {

    final List<Entry<K, V>> expected;
    final Multimap<K, V> remaining;

    OutOfOrderContainmentAssertion(List<Entry<K, V>> expected, Multimap<K, V> remaining) {
      this.expected = expected;
      this.remaining = remaining;
    }

    @Override public ContainmentAssertion<K, V> and(K key, V value) {
      return and(immutableEntry(key, value));
    }

    ContainmentAssertion<K, V> and(Entry<K, V> entry) {
      expected.add(entry);
      if (!remaining.remove(entry.getKey(), entry.getValue())) {
        fail("contains", expected, "is missing", entry);
        return new FailedContainmentAssertion();
      }
      return this;
    }

    @Override public Ordered andNothingElse() {
      if (!remaining.isEmpty()) {
        failWithBadResults(
            "contains", expected, "has unexpected items", remaining.entries());
      }
      return new NotInOrder("contains only these elements in order", expected);
    }
  }

  /** Implementation of {@code ContainmentAssertion} for a subject that has already failed. */
  private class FailedContainmentAssertion implements ContainmentAssertion<K, V> {

    @Override public ContainmentAssertion<K, V> and(K key, V value) {
      return this;
    }

    @Override public Ordered andNothingElse() {
      return IN_ORDER;
    }
  }

  /** Ordered implementation that does nothing because it's already known to be true. */
  private static final Ordered IN_ORDER = new Ordered() {
    @Override public void inOrder() {}
  };

  /** Ordered implementation that always fails. */
  private class NotInOrder implements Ordered {
    final String check;
    final Iterable<?> required;

    NotInOrder(String check, Iterable<?> required) {
      this.check = check;
      this.required = required;
    }

    @Override public void inOrder() {
      fail(check, required);
    }
  }
}
