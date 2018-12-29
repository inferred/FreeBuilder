/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** FreeBuilder test using {@link MapProperty}. */
@RunWith(JUnit4.class)
public class MapPropertyTest {

  private static final String NAME_1 = "name1";
  private static final String NAME_2 = "name2";
  private static final String NAME_3 = "name3";
  private static final double DISTANCE_1 = 8.7;
  private static final double DISTANCE_2 = 13.1;
  private static final double DISTANCE_3 = 27.0;

  @Test
  public void testAllMethodInteractions() {
    MapProperty.Builder builder = new MapProperty.Builder();
    Map<String, Double> distancesView = builder.getDistances();
    assertEquals(Collections.emptyMap(), distancesView);
    assertEquals(Collections.emptyMap(), builder.build().getDistances());
    builder.putDistances(NAME_1, DISTANCE_1);
    assertEquals(mapOf(NAME_1, DISTANCE_1), distancesView);
    assertEquals(mapOf(NAME_1, DISTANCE_1), builder.build().getDistances());
    builder.putAllDistances(mapOf(NAME_2, DISTANCE_2, NAME_3, DISTANCE_3));
    assertEquals(mapOf(NAME_1, DISTANCE_1, NAME_2, DISTANCE_2, NAME_3, DISTANCE_3),
        distancesView);
    assertEquals(mapOf(NAME_1, DISTANCE_1, NAME_2, DISTANCE_2, NAME_3, DISTANCE_3),
        builder.build().getDistances());
    builder.removeDistances(NAME_2);
    assertEquals(mapOf(NAME_1, DISTANCE_1, NAME_3, DISTANCE_3), distancesView);
    assertEquals(mapOf(NAME_1, DISTANCE_1, NAME_3, DISTANCE_3),
        builder.build().getDistances());
    builder.clearDistances();
    assertEquals(Collections.emptyMap(), distancesView);
    assertEquals(Collections.emptyMap(), builder.build().getDistances());
    builder.putDistances(NAME_1, DISTANCE_1);
    assertEquals(mapOf(NAME_1, DISTANCE_1), distancesView);
    assertEquals(mapOf(NAME_1, DISTANCE_1), builder.build().getDistances());
    builder.clear();
    assertEquals(Collections.emptyMap(), distancesView);
    assertEquals(Collections.emptyMap(), builder.build().getDistances());
  }

  private static <K, V> Map<K, V> mapOf(K key, V value) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  private static <K, V> Map<K, V> mapOf(K key1, V value1, K key2, V value2) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    return map;
  }

  private static <K, V> Map<K, V> mapOf(K key1, V value1, K key2, V value2, K key3, V value3) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(key1, value1);
    map.put(key2, value2);
    map.put(key3, value3);
    return map;
  }
}
