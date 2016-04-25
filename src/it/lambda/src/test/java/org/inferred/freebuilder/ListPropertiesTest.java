package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ListPropertiesTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testMutate() {
    ListPropertyType value = new ListPropertyType.Builder()
        .addNames("Alan", "Bob", "Chris", "Diana", "Emma", "Fred")
        .mutateNames(names -> names.subList(2, 4).clear())
        .build();
    assertEquals(ImmutableList.of("Alan", "Bob", "Emma", "Fred"), value.getNames());
  }
}
