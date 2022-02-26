package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MapPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMutate() {
    MapPropertyType value =
        new MapPropertyType.Builder()
            .putNumbers(1, "one")
            .putNumbers(2, "two")
            .mutateNumbers(
                numbers -> numbers.replaceAll((i, s) -> s.toUpperCase() + " (" + i + ")"))
            .build();
    assertEquals(ImmutableMap.of(1, "ONE (1)", 2, "TWO (2)"), value.getNumbers());
  }
}
