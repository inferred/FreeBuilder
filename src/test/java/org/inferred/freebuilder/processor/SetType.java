package org.inferred.freebuilder.processor;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;

@SuppressWarnings("rawtypes")
public enum SetType {
  SET(Set.class, ImmutableSet.class) {
    @Override
    public int[] inOrder(int... exampleIds) {
      return exampleIds;
    }

    @Override
    public String intsInOrder(int... examples) {
      return Joiner.on(", ").join(Ints.asList(examples));
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
      int[] sortedExamples = examples.clone();
      Arrays.sort(sortedExamples);
      return Joiner.on(", ").join(Ints.asList(sortedExamples));
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

  public abstract int[] inOrder(int... exampleIds);

  public abstract String intsInOrder(int... examples);

  @Override
  public String toString() {
    return setType.getSimpleName();
  }
}