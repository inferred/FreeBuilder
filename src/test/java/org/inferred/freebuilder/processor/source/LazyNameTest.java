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
package org.inferred.freebuilder.processor.source;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.Arrays;

public class LazyNameTest {

  private static class AddExcerpt extends ValueType implements Excerpt {

    private final String fmt;
    private final Object[] args;

    AddExcerpt(String fmt, Object... args) {
      this.fmt = fmt;
      this.args = args;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.add(fmt, args);
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("excerpt", fmt);
      fields.add("args", Arrays.asList(args));
    }
  }

  @Test
  public void selectsUniqueName() {
    AddExcerpt excerpt1 = new AddExcerpt("excerpt 1%n");
    LazyName name1 = LazyName.of("foobar", excerpt1);
    AddExcerpt excerpt2 = new AddExcerpt("excerpt 2%n");
    LazyName name2 = LazyName.of("foobar", excerpt2);
    LazyName name1Duplicate = LazyName.of("foobar", excerpt1);

    SourceBuilder code = SourceBuilder.forTesting();
    code.add("%s %s %s", name1, name2, name1Duplicate);
    assertThat(code.toString()).isEqualTo("foobar foobar2 foobar");
  }

  @Test
  public void addsDefinitionsInAlphabeticOrder() {
    AddExcerpt excerpt1 = new AddExcerpt("excerpt 1%n");
    LazyName name1 = LazyName.of("foobar", excerpt1);
    AddExcerpt excerpt2 = new AddExcerpt("excerpt 2%n");
    LazyName name2 = LazyName.of("BazBam", excerpt2);

    SourceBuilder code = SourceBuilder.forTesting();
    code.addLine("%s", name1);
    code.addLine("%s", name2);
    LazyName.addLazyDefinitions(code);

    assertThat(code.toString()).isEqualTo("foobar\nBazBam\nexcerpt 2\nexcerpt 1\n");
  }

  @Test
  public void addsDefinitionsAddedByOtherDefinitions() {
    AddExcerpt excerpt1 = new AddExcerpt("excerpt%n");
    LazyName name1 = LazyName.of("foobar", excerpt1);
    AddExcerpt excerpt2 = new AddExcerpt("%s%n", name1);
    LazyName name2 = LazyName.of("hoolah", excerpt2);

    SourceBuilder code = SourceBuilder.forTesting();
    code.addLine("%s", name2);
    LazyName.addLazyDefinitions(code);

    assertThat(code.toString()).isEqualTo("hoolah\nfoobar\nexcerpt\n");
  }
}
