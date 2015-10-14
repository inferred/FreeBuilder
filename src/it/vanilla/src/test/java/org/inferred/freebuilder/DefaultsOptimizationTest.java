package org.inferred.freebuilder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DefaultsOptimizationTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testNoDefaultsNoOptimisation() throws NoSuchFieldException, SecurityException {
    RequiredPropertiesType.Builder.class.getSuperclass().getDeclaredField("_unsetProperties");
  }

  @Test
  public void testDefaultsOptimisation() throws NoSuchFieldException, SecurityException {
    thrown.expect(NoSuchFieldException.class);
    DefaultedPropertiesType.Builder.class.getSuperclass().getDeclaredField("_unsetProperties");
  }
}
