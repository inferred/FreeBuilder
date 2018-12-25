package org.inferred.freebuilder.processor;

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.IntStream;

@SuppressWarnings("rawtypes")
public enum SetType {
  SET(Set.class, ImmutableSet.class) {
    @Override
    public int[] inOrder(int... exampleIds) {
      return exampleIds;
    }

    @Override
    public String intsInOrder(int... examples) {
      return IntStream.of(examples).mapToObj(Integer::toString).collect(joining(", "));
    }
  },
  SORTED_SET(SortedSet.class, ImmutableSortedSet.class) {
    @Override
    public int[] inOrder(int... exampleIds) {
      int[] result = exampleIds.clone();
      Arrays.sort(result);
      return result;
    }

    @Override
    public String intsInOrder(int... examples) {
      return IntStream.of(examples).sorted().mapToObj(Integer::toString).collect(joining(", "));
    }
  };

  private final Class<? extends Set> setType;
  private final Class<? extends ImmutableSet> immutableSetType;

  SetType(Class<? extends Set> setType, Class<? extends ImmutableSet> immutableSetType) {
    this.setType = setType;
    this.immutableSetType = immutableSetType;
    checkState(setType.isAssignableFrom(immutableSetType));
  }

  public Class<?> type() {
    return setType;
  }

  public Class<?> immutableType() {
    return immutableSetType;
  }

  public boolean isSorted() {
    return SortedSet.class.isAssignableFrom(setType);
  }

  public abstract int[] inOrder(int... exampleIds);

  public abstract String intsInOrder(int... examples);

  @Override
  public String toString() {
    return setType.getSimpleName();
  }
}