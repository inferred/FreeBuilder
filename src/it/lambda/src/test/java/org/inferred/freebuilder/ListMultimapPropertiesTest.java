package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableListMultimap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ListMultimapPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMutate() {
    ListMultimapPropertyType value = new ListMultimapPropertyType.Builder()
        .putNumbers(1, "one")
        .putNumbers(1, "uno")
        .putNumbers(2, "two")
        .putNumbers(2, "dos")
        .mutateNumbers(numbers -> numbers.entries().forEach(entry -> entry.setValue(
            entry.getValue().toUpperCase() + " (" + entry.getKey() + ")")))
        .build();
    assertEquals(ImmutableListMultimap.of(1, "ONE (1)", 1, "UNO (1)", 2, "TWO (2)", 2, "DOS (2)"),
        value.getNumbers());
  }
}
