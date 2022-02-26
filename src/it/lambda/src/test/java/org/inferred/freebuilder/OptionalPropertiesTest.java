package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class OptionalPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMap() {
    OptionalPropertiesType value =
        new OptionalPropertiesType.Builder()
            .setFirstName("joe")
            .setSurname("bloggs")
            .mapFirstName(CAPITALIZE)
            .mapSurname(CAPITALIZE)
            .build();
    assertEquals(Optional.of("Joe"), value.getFirstName());
    assertEquals(Optional.of("Bloggs"), value.getSurname());
  }

  private static final UnaryOperator<String> CAPITALIZE =
      s -> s.substring(0, 1).toUpperCase() + s.substring(1, s.length());
}
