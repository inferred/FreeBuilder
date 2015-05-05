package org.inferred.freebuilder.processor.util;

import static org.junit.Assert.assertEquals;

import javax.lang.model.SourceVersion;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SourceLevelTest {

  @Test
  public void testFrom() {
    // Tests are currently run with JDK 7, so we can test up to RELEASE_7.
    assertEquals(SourceLevel.JAVA_6, SourceLevel.from(SourceVersion.RELEASE_6));
    assertEquals(SourceLevel.JAVA_7, SourceLevel.from(SourceVersion.RELEASE_7));
  }
}
