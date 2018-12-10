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
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.ClassTypeImpl;
import org.inferred.freebuilder.processor.util.ClassTypeImpl.ClassElementImpl;
import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NullableSourceTest {

  @Test
  public void testJ8() {
    String source = generateSource(metadata(true), GuavaLibrary.AVAILABLE);
    assertThat(source).isEqualTo(Joiner.on('\n').join(
        "/** Auto-generated superclass of {@link Person.Builder}, "
            + "derived from the API of {@link Person}. */",
        "abstract class Person_Builder {",
        "",
        "  /** Creates a new builder using {@code value} as a template. */",
        "  public static Person.Builder from(Person value) {",
        "    return new Person.Builder().mergeFrom(value);",
        "  }",
        "",
        "  private static final Joiner COMMA_JOINER = Joiner.on(\", \").skipNulls();",
        "",
        "  @Nullable private String name = null;",
        "  @Nullable private Integer age = null;",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setName(@Nullable String name) {",
        "    this.name = name;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#getName()} is not null, "
            + "replaces it by applying",
        "   * {@code mapper} to it and using the result.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapName(UnaryOperator<String> mapper) {",
        "    Preconditions.checkNotNull(mapper);",
        "    String name = getName();",
        "    if (name != null) {",
        "      setName(mapper.apply(name));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#getName()}. */",
        "  @Nullable",
        "  public String getName() {",
        "    return name;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setAge(@Nullable Integer age) {",
        "    this.age = age;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#getAge()} is not null, "
            + "replaces it by applying",
        "   * {@code mapper} to it and using the result.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapAge(UnaryOperator<Integer> mapper) {",
        "    Preconditions.checkNotNull(mapper);",
        "    Integer age = getAge();",
        "    if (age != null) {",
        "      setAge(mapper.apply(age));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#getAge()}. */",
        "  @Nullable",
        "  public Integer getAge() {",
        "    return age;",
        "  }",
        "",
        "  /** Sets all property values using the given {@code Person} as a template. */",
        "  public Person.Builder mergeFrom(Person value) {",
        "    Person_Builder _defaults = new Person.Builder();",
        "    if (!Objects.equals(value.getName(), _defaults.getName())) {",
        "      setName(value.getName());",
        "    }",
        "    if (!Objects.equals(value.getAge(), _defaults.getAge())) {",
        "      setAge(value.getAge());",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Builder}. "
            + "Does not affect any properties not set on the",
        "   * input.",
        "   */",
        "  public Person.Builder mergeFrom(Person.Builder template) {",
        "    Person_Builder _defaults = new Person.Builder();",
        "    if (!Objects.equals(template.getName(), _defaults.getName())) {",
        "      setName(template.getName());",
        "    }",
        "    if (!Objects.equals(template.getAge(), _defaults.getAge())) {",
        "      setAge(template.getAge());",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Resets the state of this builder. */",
        "  public Person.Builder clear() {",
        "    Person_Builder _defaults = new Person.Builder();",
        "    name = _defaults.name;",
        "    age = _defaults.age;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns a newly-created {@link Person} based on the contents of the "
            + "{@code Builder}. */",
        "  public Person build() {",
        "    return new Person_Builder.Value(this);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person} for use in unit tests. "
            + "State checking will not",
        "   * be performed.",
        "   *",
        "   * <p>Partials should only ever be used in tests. "
            + "They permit writing robust test cases that won't",
        "   * fail if this type gains more application-level constraints "
            + "(e.g. new required fields) in",
        "   * future. If you require partially complete values in production code, "
            + "consider using a Builder.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person buildPartial() {",
        "    return new Person_Builder.Partial(this);",
        "  }",
        "",
        "  private static final class Value extends Person {",
        "    @Nullable private final String name;",
        "    @Nullable private final Integer age;",
        "",
        "    private Value(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public String getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public Integer getAge() {",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Value)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Value other = (Person_Builder.Value) obj;",
        "      return Objects.equals(name, other.name) && Objects.equals(age, other.age);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{\"",
        "          + COMMA_JOINER.join(",
        "              (name != null ? \"name=\" + name : null), "
            + "(age != null ? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "",
        "  private static final class Partial extends Person {",
        "    @Nullable private final String name;",
        "    @Nullable private final Integer age;",
        "",
        "    Partial(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public String getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public Integer getAge() {",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial other = (Person_Builder.Partial) obj;",
        "      return Objects.equals(name, other.name) && Objects.equals(age, other.age);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{\"",
        "          + COMMA_JOINER.join(",
        "              (name != null ? \"name=\" + name : null), "
            + "(age != null ? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "}\n"));
  }

  @Test
  public void testPrefixless() {
    String source = generateSource(metadata(false), GuavaLibrary.AVAILABLE);
    assertThat(source).isEqualTo(Joiner.on('\n').join(
        "/** Auto-generated superclass of {@link Person.Builder}, "
            + "derived from the API of {@link Person}. */",
        "abstract class Person_Builder {",
        "",
        "  /** Creates a new builder using {@code value} as a template. */",
        "  public static Person.Builder from(Person value) {",
        "    return new Person.Builder().mergeFrom(value);",
        "  }",
        "",
        "  private static final Joiner COMMA_JOINER = Joiner.on(\", \").skipNulls();",
        "",
        "  @Nullable private String name = null;",
        "  @Nullable private Integer age = null;",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#name()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder name(@Nullable String name) {",
        "    this.name = name;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#name()} is not null, "
            + "replaces it by applying",
        "   * {@code mapper} to it and using the result.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapName(UnaryOperator<String> mapper) {",
        "    Preconditions.checkNotNull(mapper);",
        "    String name = name();",
        "    if (name != null) {",
        "      name(mapper.apply(name));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#name()}. */",
        "  @Nullable",
        "  public String name() {",
        "    return name;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#age()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder age(@Nullable Integer age) {",
        "    this.age = age;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#age()} is not null, "
            + "replaces it by applying {@code",
        "   * mapper} to it and using the result.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapAge(UnaryOperator<Integer> mapper) {",
        "    Preconditions.checkNotNull(mapper);",
        "    Integer age = age();",
        "    if (age != null) {",
        "      age(mapper.apply(age));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#age()}. */",
        "  @Nullable",
        "  public Integer age() {",
        "    return age;",
        "  }",
        "",
        "  /** Sets all property values using the given {@code Person} as a template. */",
        "  public Person.Builder mergeFrom(Person value) {",
        "    Person_Builder _defaults = new Person.Builder();",
        "    if (!Objects.equals(value.name(), _defaults.name())) {",
        "      name(value.name());",
        "    }",
        "    if (!Objects.equals(value.age(), _defaults.age())) {",
        "      age(value.age());",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Builder}. "
            + "Does not affect any properties not set on the",
        "   * input.",
        "   */",
        "  public Person.Builder mergeFrom(Person.Builder template) {",
        "    Person_Builder _defaults = new Person.Builder();",
        "    if (!Objects.equals(template.name(), _defaults.name())) {",
        "      name(template.name());",
        "    }",
        "    if (!Objects.equals(template.age(), _defaults.age())) {",
        "      age(template.age());",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Resets the state of this builder. */",
        "  public Person.Builder clear() {",
        "    Person_Builder _defaults = new Person.Builder();",
        "    name = _defaults.name;",
        "    age = _defaults.age;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns a newly-created {@link Person} based on the contents of the "
            + "{@code Builder}. */",
        "  public Person build() {",
        "    return new Person_Builder.Value(this);",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person} for use in unit tests. "
            + "State checking will not",
        "   * be performed.",
        "   *",
        "   * <p>Partials should only ever be used in tests. "
            + "They permit writing robust test cases that won't",
        "   * fail if this type gains more application-level constraints "
            + "(e.g. new required fields) in",
        "   * future. If you require partially complete values in production code, "
            + "consider using a Builder.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person buildPartial() {",
        "    return new Person_Builder.Partial(this);",
        "  }",
        "",
        "  private static final class Value extends Person {",
        "    @Nullable private final String name;",
        "    @Nullable private final Integer age;",
        "",
        "    private Value(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public String name() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public Integer age() {",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Value)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Value other = (Person_Builder.Value) obj;",
        "      return Objects.equals(name, other.name) && Objects.equals(age, other.age);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{\"",
        "          + COMMA_JOINER.join(",
        "              (name != null ? \"name=\" + name : null), "
            + "(age != null ? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "",
        "  private static final class Partial extends Person {",
        "    @Nullable private final String name;",
        "    @Nullable private final Integer age;",
        "",
        "    Partial(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public String name() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    @Nullable",
        "    public Integer age() {",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial other = (Person_Builder.Partial) obj;",
        "      return Objects.equals(name, other.name) && Objects.equals(age, other.age);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Objects.hash(name, age);",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{\"",
        "          + COMMA_JOINER.join(",
        "              (name != null ? \"name=\" + name : null), "
            + "(age != null ? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "}\n"));
  }

  private static String generateSource(Metadata metadata, Feature<?>... features) {
    SourceBuilder sourceBuilder = SourceStringBuilder.simple(features);
    new CodeGenerator().writeBuilderSource(sourceBuilder, metadata);
    return CompilationUnitBuilder.formatSource(sourceBuilder.toString());
  }

  private static Metadata metadata(boolean bean) {
    ClassTypeImpl integer = newTopLevelClass("java.lang.Integer");
    ClassTypeImpl string = newTopLevelClass("java.lang.String");
    ClassElementImpl nullable = newTopLevelClass("javax.annotation.Nullable").asElement();
    QualifiedName person = QualifiedName.of("com.example", "Person");
    QualifiedName generatedBuilder = QualifiedName.of("com.example", "Person_Builder");
    Property name = new Property.Builder()
        .setAllCapsName("NAME")
        .setBoxedType(string)
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName(bean ? "getName" : "name")
        .setName("name")
        .setType(string)
        .setUsingBeanConvention(bean)
        .build();
    Property age = new Property.Builder()
        .setAllCapsName("AGE")
        .setBoxedType(integer)
        .setCapitalizedName("Age")
        .setFullyCheckedCast(true)
        .setGetterName(bean ? "getAge" : "age")
        .setName("age")
        .setType(integer)
        .setUsingBeanConvention(bean)
        .build();
    Metadata metadata = new Metadata.Builder()
        .setBuilder(person.nestedType("Builder").withParameters())
        .setExtensible(true)
        .setBuilderFactory(BuilderFactory.NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(generatedBuilder.withParameters())
        .setInterfaceType(false)
        .setPartialType(generatedBuilder.nestedType("Partial").withParameters())
        .addProperties(name, age)
        .setPropertyEnum(generatedBuilder.nestedType("Property").withParameters())
        .setType(person.withParameters())
        .setValueType(generatedBuilder.nestedType("Value").withParameters())
        .build();
    return metadata.toBuilder()
        .clearProperties()
        .addProperties(name.toBuilder()
            .setCodeGenerator(new NullableProperty(metadata, name, ImmutableSet.of(nullable)))
            .build())
        .addProperties(age.toBuilder()
            .setCodeGenerator(new NullableProperty(metadata, age, ImmutableSet.of(nullable)))
            .build())
        .build();
  }
}
