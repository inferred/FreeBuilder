package org.inferred.freebuilder.processor.util;

import static com.google.common.truth.Truth.assertThat;
import static org.inferred.freebuilder.processor.util.StaticExcerpt.Type.METHOD;
import static org.inferred.freebuilder.processor.util.StaticExcerpt.Type.TYPE;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class StaticExcerptTest {

  public static class DummyStaticExcerpt extends StaticExcerpt {

    DummyStaticExcerpt(Type type, String name) {
      super(type, name);
    }

    @Override public void addTo(SourceBuilder source) {}
  }

  @Test
  public void testOrdering() {
    StaticExcerpt method1 = new DummyStaticExcerpt(METHOD, "bar");
    StaticExcerpt method2 = new DummyStaticExcerpt(METHOD, "foo");
    StaticExcerpt type1 = new DummyStaticExcerpt(TYPE, "Bar");
    StaticExcerpt type2 = new DummyStaticExcerpt(TYPE, "Foo");

    List<StaticExcerpt> excerpts = new ArrayList<>();
    excerpts.add(method2);
    excerpts.add(type1);
    excerpts.add(method1);
    excerpts.add(type2);
    Collections.sort(excerpts);

    assertThat(excerpts).containsExactly(method1, method2, type1, type2).inOrder();
  }
}
