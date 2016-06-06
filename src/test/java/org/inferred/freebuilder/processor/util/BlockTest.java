package org.inferred.freebuilder.processor.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.rules.ExpectedException.none;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BlockTest {

  @Rule public ExpectedException thrown = none();

  private final SourceBuilder code = SourceStringBuilder.simple();
  private final Block block = new Block(code);

  @Test
  public void testSingleDeclaration() {
    block.add("Line 2\n");
    Excerpt reference = block.declare("Line 3\n", "Line 1\n");
    block.add(reference);
    code.add(block);
    assertThat(code.toString()).isEqualTo("Line 1\nLine 2\nLine 3\n");
  }

  @Test
  public void testTwoDeclarations() {
    block.add("Line 3\n");
    Excerpt reference = block.declare("Line 5\n", "Line 1\n");
    Excerpt reference2 = block.declare("Line 4\n", "Line 2\n");
    block.add(reference2);
    block.add(reference);
    code.add(block);
    assertThat(code.toString()).isEqualTo("Line 1\nLine 2\nLine 3\nLine 4\nLine 5\n");
  }

  @Test
  public void testDuplicateDeclaration() {
    block.add("Line 2\n");
    Excerpt reference = block.declare("Line 3\n", "Line 1\n");
    Excerpt reference2 = block.declare("Line 3\n", "Line 1\n");
    block.add(reference);
    block.add(reference2);
    code.add(block);
    assertThat(code.toString()).isEqualTo("Line 1\nLine 2\nLine 3\nLine 3\n");
  }

  @Test
  public void testIncompatibleDeclarations() {
    block.declare("variable", "definition 1");
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Incompatible declaration for 'variable'");
    block.declare("variable", "definition 2");
  }
}
