/*
 * Copyright 2016 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor.util;

import static com.google.common.truth.Truth.assertThat;
import static org.inferred.freebuilder.processor.util.Block.methodBody;
import static org.junit.rules.ExpectedException.none;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BlockTest {

  @Rule public ExpectedException thrown = none();

  private final SourceBuilder code = SourceStringBuilder.simple();
  private final Block block = methodBody(code, "param", "otherParam", "_otherParam");

  @Test
  public void testSingleDeclaration() {
    block.addLine("foo();");
    Excerpt reference = block.declare(Excerpts.add("int"), "bar", Excerpts.add("baz()"));
    block.addLine("foobar(%s);", reference);
    code.add(block);
    assertThat(code.toString()).isEqualTo("int bar = baz();\nfoo();\nfoobar(bar);\n");
  }

  @Test
  public void testAvoidsNameClashByPrependingUnderscore() {
    block.addLine("foo();");
    Excerpt reference = block.declare(Excerpts.add("int"), "param", Excerpts.add("baz()"));
    block.addLine("foobar(%s);", reference);
    code.add(block);
    assertThat(code.toString()).isEqualTo("int _param = baz();\nfoo();\nfoobar(_param);\n");
  }

  @Test
  public void testAvoidsUnderscoreNameClashByAppendingDigit() {
    block.addLine("foo();");
    Excerpt reference = block.declare(Excerpts.add("int"), "otherParam", Excerpts.add("baz()"));
    block.addLine("foobar(%s);", reference);
    code.add(block);
    assertThat(code.toString())
        .isEqualTo("int _otherParam2 = baz();\nfoo();\nfoobar(_otherParam2);\n");
  }

  @Test
  public void testTwoDeclarations() {
    block.addLine("foo();");
    Excerpt reference = block.declare(Excerpts.add("int"), "one", Excerpts.add("1"));
    Excerpt reference2 = block.declare(Excerpts.add("double"), "two", Excerpts.add("2.0"));
    block.addLine("bar(%s);", reference);
    block.addLine("baz(%s);", reference2);
    code.add(block);
    assertThat(code.toString())
        .isEqualTo("int one = 1;\ndouble two = 2.0;\nfoo();\nbar(one);\nbaz(two);\n");
  }

  @Test
  public void testDuplicateDeclaration() {
    block.addLine("foo();");
    Excerpt reference = block.declare(Excerpts.add("int"), "bar", Excerpts.add("baz()"));
    Excerpt reference2 = block.declare(Excerpts.add("int"), "bar", Excerpts.add("baz()"));
    block.addLine("foobar(%s);", reference);
    block.addLine("foobaz(%s);", reference2);
    code.add(block);
    assertThat(code.toString()).isEqualTo("int bar = baz();\nfoo();\nfoobar(bar);\nfoobaz(bar);\n");
  }

  @Test
  public void testIncompatibleTypes() {
    block.declare(Excerpts.add("int"), "variable", Excerpts.add("value"));
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Incompatible declaration for 'variable'");
    block.declare(Excerpts.add("float"), "variable", Excerpts.add("value"));
  }

  @Test
  public void testIncompatibleValues() {
    block.declare(Excerpts.add("int"), "variable", Excerpts.add("value"));
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Incompatible declaration for 'variable'");
    block.declare(Excerpts.add("int"), "variable", Excerpts.add("otherValue"));
  }

  @Test
  public void testFieldReferenceQualifiedIfClashesWithVariable() {
    Excerpt reference = block.declare(Excerpts.add("int"), "foo", Excerpts.add("bar()"));
    block.addLine("%s = %s;", new FieldAccess("foo"), reference);
    code.add(block);
    assertThat(code.toString()).isEqualTo("int foo = bar();\nthis.foo = foo;\n");
  }

  @Test
  public void testAppendsUnderscoreIfVariableNameClashesWithPreviousFieldAccess() {
    block.addLine("%s = 10;", new FieldAccess("foo"));
    block.declare(Excerpts.add("int"), "foo", Excerpts.add("bar()"));
    code.add(block);
    assertThat(code.toString()).isEqualTo("int _foo = bar();\nfoo = 10;\n");
  }

  @Test
  public void testQualifiesFieldAccessInAssignmentToVariableOfSameName() {
    block.declare(Excerpts.add("int"), "foo", new FieldAccess("foo"));
    code.add(block);
    assertThat(code.toString()).isEqualTo("int foo = this.foo;\n");
  }
}
