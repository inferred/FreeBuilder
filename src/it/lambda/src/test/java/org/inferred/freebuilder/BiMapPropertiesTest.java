package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableBiMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BiMapPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMutate() {
    BiMapPropertyType value =
        new BiMapPropertyType.Builder()
            .putNumbers(1, "one")
            .putNumbers(2, "two")
            .mutateNumbers(
                numbers -> numbers.replaceAll((i, s) -> s.toUpperCase() + " (" + i + ")"))
            .build();
    assertEquals(ImmutableBiMap.of(1, "ONE (1)", 2, "TWO (2)"), value.getNumbers());
  }
}
