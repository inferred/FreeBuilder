package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableSet;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

public class DefaultsOptimizationTest {

  @Test
  public void testNoDefaultsNoOptimisation() {
    assertEquals(
        ImmutableSet.of("firstName", "surname", "_unsetProperties"),
        fieldsOn(RequiredPropertiesType.Builder.class.getSuperclass()));
  }

  @Test
  public void testDefaultsOptimisation() {
    assertEquals(
        ImmutableSet.of("firstName", "surname"),
        fieldsOn(DefaultedPropertiesType.Builder.class.getSuperclass()));
  }

  private static Set<String> fieldsOn(Class<?> cls) {
    Set<String> generatedFields = new HashSet<String>();
    for (Field field : cls.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())) {
        generatedFields.add(field.getName());
      }
    }
    return generatedFields;
  }
}
