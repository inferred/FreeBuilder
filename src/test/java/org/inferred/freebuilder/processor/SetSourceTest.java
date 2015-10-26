/*
 * Copyright 2015 Google Inc. All rights reserved.
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
package org.inferred.freebuilder.processor;

import static com.google.common.truth.Truth.assertThat;

import static org.inferred.freebuilder.processor.GenericTypeElementImpl.newTopLevelGenericType;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.inferred.freebuilder.processor.util.SourceLevel.JAVA_6;
import static org.inferred.freebuilder.processor.util.SourceLevel.JAVA_7;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.GenericTypeElementImpl.GenericTypeMirrorImpl;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.ClassTypeImpl;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.type.TypeMirror;

@RunWith(JUnit4.class)
public class SetSourceTest {

  @Test
  public void test_j6() {
    SourceStringBuilder sourceBuilder = SourceStringBuilder.simple(JAVA_6);
    new CodeGenerator().writeBuilderSource(sourceBuilder, createMetadata());

    assertThat(sourceBuilder.toString()).isEqualTo(Joiner.on('\n').join(
        "/**",
        " * Auto-generated superclass of {@link Person.Builder},",
        " * derived from the API of {@link Person}.",
        " */",
        "@Generated(\"org.inferred.freebuilder.processor.CodeGenerator\")",
        "abstract class Person_Builder {",
        "",
        "  private final LinkedHashSet<String> name = new LinkedHashSet<String>();",
        "",
        "  /**",
        "   * Adds {@code element} to the set to be returned from {@link Person#getName()}.",
        "   * If the set already contains {@code element}, then {@code addName}",
        "   * has no effect (only the previously added element is retained).",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code element} is null",
        "   */",
        "  public Person.Builder addName(String element) {",
        "    this.name.add(Preconditions.checkNotNull(element));",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Adds each element of {@code elements} to the set to be returned from",
        "   * {@link Person#getName()}, ignoring duplicate elements",
        "   * (only the first duplicate element is added).",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code elements} is null or contains a",
        "   *     null element",
        "   */",
        "  public Person.Builder addName(String... elements) {",
        "    for (String element : elements) {",
        "      addName(element);",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Adds each element of {@code elements} to the set to be returned from",
        "   * {@link Person#getName()}, ignoring duplicate elements",
        "   * (only the first duplicate element is added).",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code elements} is null or contains a",
        "   *     null element",
        "   */",
        "  public Person.Builder addAllName(Iterable<? extends String> elements) {",
        "    for (String element : elements) {",
        "      addName(element);",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Clears the set to be returned from {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder clearName() {",
        "    name.clear();",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Returns an unmodifiable view of the set that will be returned by",
        "   * {@link Person#getName()}.",
        "   * Changes to this builder will be reflected in the view.",
        "   */",
        "  public Set<String> getName() {",
        "    return Collections.unmodifiableSet(name);",
        "  }",
        "",
        "  /**",
        "   * Sets all property values using the given {@code Person} as a template.",
        "   */",
        "  public Person.Builder mergeFrom(Person value) {",
        "    addAllName(value.getName());",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Builder}.",
        "   */",
        "  public Person.Builder mergeFrom(Person.Builder template) {",
        "    addAllName(((Person_Builder) template).name);",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Resets the state of this builder.",
        "   */",
        "  public Person.Builder clear() {",
        "    name.clear();",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created {@link Person} based on the contents of the {@code Builder}.",
        "   */",
        "  public Person build() {",
        "    return new Person_Builder.Value(this);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person}",
        "   * based on the contents of the {@code Builder}.",
        "   * State checking will not be performed.",
        "   *",
        "   * <p>Partials should only ever be used in tests.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person buildPartial() {",
        "    return new Person_Builder.Partial(this);",
        "  }",
        "",
        "  private static final class Value extends Person {",
        "    private final Set<String> name;",
        "",
        "    private Value(Person_Builder builder) {",
        "      this.name = ImmutableSet.copyOf(builder.name);",
        "    }",
        "",
        "    @Override",
        "    public Set<String> getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Value)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Value other = (Person_Builder.Value) obj;",
        "      if (!name.equals(other.name)) {",
        "        return false;",
        "      }",
        "      return true;",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Arrays.hashCode(new Object[] { name });",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{name=\" + name + \"}\";",
        "    }",
        "  }",
        "",
        "  private static final class Partial extends Person {",
        "    private final Set<String> name;",
        "",
        "    Partial(Person_Builder builder) {",
        "      this.name = ImmutableSet.copyOf(builder.name);",
        "    }",
        "",
        "    @Override",
        "    public Set<String> getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial other = (Person_Builder.Partial) obj;",
        "      if (!name.equals(other.name)) {",
        "        return false;",
        "      }",
        "      return true;",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Arrays.hashCode(new Object[] { name });",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{name=\" + name + \"}\";",
        "    }",
        "  }",
        "}\n"));
  }

  @Test
  public void test_j7() {
    SourceStringBuilder sourceBuilder = SourceStringBuilder.simple(JAVA_7);
    new CodeGenerator().writeBuilderSource(sourceBuilder, createMetadata());

    assertThat(sourceBuilder.toString()).isEqualTo(Joiner.on('\n').join(
        "/**",
        " * Auto-generated superclass of {@link Person.Builder},",
        " * derived from the API of {@link Person}.",
        " */",
        "@Generated(\"org.inferred.freebuilder.processor.CodeGenerator\")",
        "abstract class Person_Builder {",
        "",
        "  private final LinkedHashSet<String> name = new LinkedHashSet<>();",
        "",
        "  /**",
        "   * Adds {@code element} to the set to be returned from {@link Person#getName()}.",
        "   * If the set already contains {@code element}, then {@code addName}",
        "   * has no effect (only the previously added element is retained).",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code element} is null",
        "   */",
        "  public Person.Builder addName(String element) {",
        "    this.name.add(Preconditions.checkNotNull(element));",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Adds each element of {@code elements} to the set to be returned from",
        "   * {@link Person#getName()}, ignoring duplicate elements",
        "   * (only the first duplicate element is added).",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code elements} is null or contains a",
        "   *     null element",
        "   */",
        "  public Person.Builder addName(String... elements) {",
        "    for (String element : elements) {",
        "      addName(element);",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Adds each element of {@code elements} to the set to be returned from",
        "   * {@link Person#getName()}, ignoring duplicate elements",
        "   * (only the first duplicate element is added).",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code elements} is null or contains a",
        "   *     null element",
        "   */",
        "  public Person.Builder addAllName(Iterable<? extends String> elements) {",
        "    for (String element : elements) {",
        "      addName(element);",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Clears the set to be returned from {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder clearName() {",
        "    name.clear();",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Returns an unmodifiable view of the set that will be returned by",
        "   * {@link Person#getName()}.",
        "   * Changes to this builder will be reflected in the view.",
        "   */",
        "  public Set<String> getName() {",
        "    return Collections.unmodifiableSet(name);",
        "  }",
        "",
        "  /**",
        "   * Sets all property values using the given {@code Person} as a template.",
        "   */",
        "  public Person.Builder mergeFrom(Person value) {",
        "    addAllName(value.getName());",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Builder}.",
        "   */",
        "  public Person.Builder mergeFrom(Person.Builder template) {",
        "    addAllName(((Person_Builder) template).name);",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Resets the state of this builder.",
        "   */",
        "  public Person.Builder clear() {",
        "    name.clear();",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created {@link Person} based on the contents of the {@code Builder}.",
        "   */",
        "  public Person build() {",
        "    return new Person_Builder.Value(this);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person}",
        "   * based on the contents of the {@code Builder}.",
        "   * State checking will not be performed.",
        "   *",
        "   * <p>Partials should only ever be used in tests.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person buildPartial() {",
        "    return new Person_Builder.Partial(this);",
        "  }",
        "",
        "  private static final class Value extends Person {",
        "    private final Set<String> name;",
        "",
        "    private Value(Person_Builder builder) {",
        "      this.name = ImmutableSet.copyOf(builder.name);",
        "    }",
        "",
        "    @Override",
        "    public Set<String> getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Value)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Value other = (Person_Builder.Value) obj;",
        "      return Objects.equals(name, other.name);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{name=\" + name + \"}\";",
        "    }",
        "  }",
        "",
        "  private static final class Partial extends Person {",
        "    private final Set<String> name;",
        "",
        "    Partial(Person_Builder builder) {",
        "      this.name = ImmutableSet.copyOf(builder.name);",
        "    }",
        "",
        "    @Override",
        "    public Set<String> getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial other = (Person_Builder.Partial) obj;",
        "      return Objects.equals(name, other.name);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{name=\" + name + \"}\";",
        "    }",
        "  }",
        "}\n"));
  }

  /**
   * Returns a {@link Metadata} instance for a FreeBuilder type with a single property, name, of
   * type {@code Set<String>}.
   */
  private static Metadata createMetadata() {
    GenericTypeElementImpl set = newTopLevelGenericType("java.util.Set");
    ClassTypeImpl string = newTopLevelClass("java.lang.String");
    GenericTypeMirrorImpl setString = set.newMirror(string);
    QualifiedName person = QualifiedName.of("com.example", "Person");
    QualifiedName generatedBuilder = QualifiedName.of("com.example", "Person_Builder");
    Property.Builder name = new Property.Builder()
        .setAllCapsName("NAME")
        .setBoxedType(setString)
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(setString);
    Metadata metadata = new Metadata.Builder()
        .setBuilder(person.nestedType("Builder").withParameters())
        .setBuilderFactory(BuilderFactory.NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(generatedBuilder.withParameters())
        .setGwtCompatible(false)
        .setGwtSerializable(false)
        .setInterfaceType(false)
        .setPartialType(generatedBuilder.nestedType("Partial").withParameters())
        .addProperties(name
            .setCodeGenerator(new SetPropertyFactory.CodeGenerator(
                name.build(), string, Optional.<TypeMirror>absent()))
            .build())
        .setPropertyEnum(generatedBuilder.nestedType("Property").withParameters())
        .setType(person.withParameters())
        .setValueType(generatedBuilder.nestedType("Value").withParameters())
        .build();
    return metadata;
  }

}
