package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SetPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMutate() {
    SetPropertyType value =
        new SetPropertyType.Builder()
            .addNames("Alan", "Bob", "Chris", "Diana", "Emma", "Fred")
            .mutateNames(names -> names.removeIf(name -> name.matches("[CD].*")))
            .build();
    assertEquals(ImmutableSet.of("Alan", "Bob", "Emma", "Fred"), value.getNames());
  }
}
