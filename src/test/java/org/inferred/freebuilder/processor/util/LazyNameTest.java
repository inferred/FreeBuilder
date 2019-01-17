/*
 * Copyright 2017 Google Inc. All rights reserved.
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

import org.junit.Test;

public class LazyNameTest {

  @Test
  public void selectsUniqueName() {
    Excerpt excerpt1 = Excerpts.add("excerpt 1%n");
    LazyName name1 = LazyName.of("foobar", excerpt1);
    Excerpt excerpt2 = Excerpts.add("excerpt 2%n");
    LazyName name2 = LazyName.of("foobar", excerpt2);
    LazyName name1Duplicate = LazyName.of("foobar", excerpt1);

    SourceBuilder code = SourceStringBuilder.simple();
    code.add("%s %s %s", name1, name2, name1Duplicate);
    assertThat(code.toString()).isEqualTo("foobar foobar2 foobar");
  }

  @Test
  public void addsDefinitionsInAlphabeticOrder() {
    Excerpt excerpt1 = Excerpts.add("excerpt 1%n");
    LazyName name1 = LazyName.of("foobar", excerpt1);
    Excerpt excerpt2 = Excerpts.add("excerpt 2%n");
    LazyName name2 = LazyName.of("BazBam", excerpt2);

    SourceBuilder code = SourceStringBuilder.simple();
    code.addLine("%s", name1);
    code.addLine("%s", name2);
    LazyName.addLazyDefinitions(code);

    assertThat(code.toString()).isEqualTo("foobar\nBazBam\nexcerpt 2\nexcerpt 1\n");
  }

  @Test
  public void addsDefinitionsAddedByOtherDefinitions() {
    Excerpt excerpt1 = Excerpts.add("excerpt%n");
    LazyName name1 = LazyName.of("foobar", excerpt1);
    Excerpt excerpt2 = Excerpts.add("%s%n", name1);
    LazyName name2 = LazyName.of("hoolah", excerpt2);

    SourceBuilder code = SourceStringBuilder.simple();
    code.addLine("%s", name2);
    LazyName.addLazyDefinitions(code);

    assertThat(code.toString()).isEqualTo("hoolah\nexcerpt\nfoobar\n");
  }
}
