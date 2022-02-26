package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableSetMultimap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SetMultimapPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMutate() {
    SetMultimapPropertyType value =
        new SetMultimapPropertyType.Builder()
            .putNumbers(1, "one")
            .putNumbers(1, "uno")
            .putNumbers(2, "two")
            .putNumbers(2, "dos")
            .mutateNumbers(numbers -> numbers.removeAll(2))
            .build();
    assertEquals(ImmutableSetMultimap.of(1, "one", 1, "uno"), value.getNumbers());
  }
}
