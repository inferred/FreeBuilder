package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMultiset;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MultisetPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMutate() {
    MultisetPropertyType value =
        new MultisetPropertyType.Builder()
            .addNames("Alan", "Bob", "Chris", "Diana", "Emma", "Fred")
            .mutateNames(names -> names.removeIf(name -> name.matches("[CD].*")))
            .build();
    assertEquals(ImmutableMultiset.of("Alan", "Bob", "Emma", "Fred"), value.getNames());
  }
}
