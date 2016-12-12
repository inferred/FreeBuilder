package org.inferred.freebuilder.processor;

import com.google.common.collect.ImmutableList;

import java.util.Iterator;

/** Throws a {@link NullPointerException} the second time {@link #iterator()} is called. */
public class DodgyIterable<E> implements Iterable<E> {
  private ImmutableList<E> values;

  @SafeVarargs
  public DodgyIterable(E... values) {
    this.values = ImmutableList.copyOf(values);
  }

  @Override
  public Iterator<E> iterator() {
    try {
      return values.iterator();
    } finally {
      values = null;
    }
  }
}