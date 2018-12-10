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
import static org.inferred.freebuilder.processor.util.PrimitiveTypeImpl.INT;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

import org.inferred.freebuilder.processor.GenericTypeElementImpl.GenericTypeMirrorImpl;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.OptionalProperty.OptionalType;
import org.inferred.freebuilder.processor.util.ClassTypeImpl;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.GuavaLibrary;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.lang.model.type.TypeMirror;

@RunWith(JUnit4.class)
public class GuavaOptionalSourceTest {

  @Test
  public void testJ8() {
    Metadata metadata = createMetadataWithOptionalProperties(true);

    String source = generateSource(metadata, GuavaLibrary.AVAILABLE);
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
        "  // Store a nullable object instead of an Optional. Escape analysis then",
        "  // allows the JVM to optimize away the Optional objects created by and",
        "  // passed to our API.",
        "  private String name = null;",
        "  // Store a nullable object instead of an Optional. Escape analysis then",
        "  // allows the JVM to optimize away the Optional objects created by and",
        "  // passed to our API.",
        "  private Integer age = null;",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code name} is null",
        "   */",
        "  public Person.Builder setName(String name) {",
        "    this.name = Objects.requireNonNull(name);",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setName(Optional<? extends String> name) {",
        "    if (name.isPresent()) {",
        "      return setName(name.get());",
        "    } else {",
        "      return clearName();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setNullableName(@Nullable String name) {",
        "    if (name != null) {",
        "      return setName(name);",
        "    } else {",
        "      return clearName();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#getName()} is present, "
            + "replaces it by applying",
        "   * {@code mapper} to it and using the result.",
        "   *",
        "   * <p>If the result is null, clears the value.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapName(UnaryOperator<String> mapper) {",
        "    Objects.requireNonNull(mapper);",
        "    Optional<String> oldName = getName();",
        "    if (oldName.isPresent()) {",
        "      setNullableName(mapper.apply(oldName.get()));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()} to "
            + "{@link Optional#absent()",
        "   * Optional.absent()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder clearName() {",
        "    name = null;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#getName()}. */",
        "  public Optional<String> getName() {",
        "    return Optional.fromNullable(name);",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setAge(int age) {",
        "    this.age = age;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setAge(Optional<? extends Integer> age) {",
        "    if (age.isPresent()) {",
        "      return setAge(age.get());",
        "    } else {",
        "      return clearAge();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setNullableAge(@Nullable Integer age) {",
        "    if (age != null) {",
        "      return setAge(age);",
        "    } else {",
        "      return clearAge();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#getAge()} is present, "
            + "replaces it by applying",
        "   * {@code mapper} to it and using the result.",
        "   *",
        "   * <p>If the result is null, clears the value.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapAge(UnaryOperator<Integer> mapper) {",
        "    Objects.requireNonNull(mapper);",
        "    Optional<Integer> oldAge = getAge();",
        "    if (oldAge.isPresent()) {",
        "      setNullableAge(mapper.apply(oldAge.get()));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()} to {@link Optional#absent()",
        "   * Optional.absent()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder clearAge() {",
        "    age = null;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#getAge()}. */",
        "  public Optional<Integer> getAge() {",
        "    return Optional.fromNullable(age);",
        "  }",
        "",
        "  /** Sets all property values using the given {@code Person} as a template. */",
        "  public Person.Builder mergeFrom(Person value) {",
        "    if (value.getName().isPresent()) {",
        "      setName(value.getName().get());",
        "    }",
        "    if (value.getAge().isPresent()) {",
        "      setAge(value.getAge().get());",
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
        "    if (template.getName().isPresent()) {",
        "      setName(template.getName().get());",
        "    }",
        "    if (template.getAge().isPresent()) {",
        "      setAge(template.getAge().get());",
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
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final String name;",
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final Integer age;",
        "",
        "    private Value(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public Optional<String> getName() {",
        "      return Optional.fromNullable(name);",
        "    }",
        "",
        "    @Override",
        "    public Optional<Integer> getAge() {",
        "      return Optional.fromNullable(age);",
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
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final String name;",
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final Integer age;",
        "",
        "    Partial(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public Optional<String> getName() {",
        "      return Optional.fromNullable(name);",
        "    }",
        "",
        "    @Override",
        "    public Optional<Integer> getAge() {",
        "      return Optional.fromNullable(age);",
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
    Metadata metadata = createMetadataWithOptionalProperties(false);

    assertThat(generateSource(metadata, GuavaLibrary.AVAILABLE)).isEqualTo(Joiner.on('\n').join(
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
        "  // Store a nullable object instead of an Optional. Escape analysis then",
        "  // allows the JVM to optimize away the Optional objects created by and",
        "  // passed to our API.",
        "  private String name = null;",
        "  // Store a nullable object instead of an Optional. Escape analysis then",
        "  // allows the JVM to optimize away the Optional objects created by and",
        "  // passed to our API.",
        "  private Integer age = null;",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#name()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code name} is null",
        "   */",
        "  public Person.Builder name(String name) {",
        "    this.name = Objects.requireNonNull(name);",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#name()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder name(Optional<? extends String> name) {",
        "    if (name.isPresent()) {",
        "      return name(name.get());",
        "    } else {",
        "      return clearName();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#name()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder nullableName(@Nullable String name) {",
        "    if (name != null) {",
        "      return name(name);",
        "    } else {",
        "      return clearName();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#name()} is present, replaces it by "
            + "applying {@code",
        "   * mapper} to it and using the result.",
        "   *",
        "   * <p>If the result is null, clears the value.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapName(UnaryOperator<String> mapper) {",
        "    Objects.requireNonNull(mapper);",
        "    Optional<String> oldName = name();",
        "    if (oldName.isPresent()) {",
        "      nullableName(mapper.apply(oldName.get()));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#name()} to {@link Optional#absent()",
        "   * Optional.absent()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder clearName() {",
        "    name = null;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#name()}. */",
        "  public Optional<String> name() {",
        "    return Optional.fromNullable(name);",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#age()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder age(int age) {",
        "    this.age = age;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#age()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder age(Optional<? extends Integer> age) {",
        "    if (age.isPresent()) {",
        "      return age(age.get());",
        "    } else {",
        "      return clearAge();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#age()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder nullableAge(@Nullable Integer age) {",
        "    if (age != null) {",
        "      return age(age);",
        "    } else {",
        "      return clearAge();",
        "    }",
        "  }",
        "",
        "  /**",
        "   * If the value to be returned by {@link Person#age()} is present, replaces it by "
            + "applying {@code",
        "   * mapper} to it and using the result.",
        "   *",
        "   * <p>If the result is null, clears the value.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code mapper} is null",
        "   */",
        "  public Person.Builder mapAge(UnaryOperator<Integer> mapper) {",
        "    Objects.requireNonNull(mapper);",
        "    Optional<Integer> oldAge = age();",
        "    if (oldAge.isPresent()) {",
        "      nullableAge(mapper.apply(oldAge.get()));",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#age()} to {@link Optional#absent()",
        "   * Optional.absent()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder clearAge() {",
        "    age = null;",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /** Returns the value that will be returned by {@link Person#age()}. */",
        "  public Optional<Integer> age() {",
        "    return Optional.fromNullable(age);",
        "  }",
        "",
        "  /** Sets all property values using the given {@code Person} as a template. */",
        "  public Person.Builder mergeFrom(Person value) {",
        "    if (value.name().isPresent()) {",
        "      name(value.name().get());",
        "    }",
        "    if (value.age().isPresent()) {",
        "      age(value.age().get());",
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
        "    if (template.name().isPresent()) {",
        "      name(template.name().get());",
        "    }",
        "    if (template.age().isPresent()) {",
        "      age(template.age().get());",
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
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final String name;",
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final Integer age;",
        "",
        "    private Value(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public Optional<String> name() {",
        "      return Optional.fromNullable(name);",
        "    }",
        "",
        "    @Override",
        "    public Optional<Integer> age() {",
        "      return Optional.fromNullable(age);",
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
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final String name;",
        "    // Store a nullable object instead of an Optional. Escape analysis then",
        "    // allows the JVM to optimize away the Optional objects created by our",
        "    // getter method.",
        "    private final Integer age;",
        "",
        "    Partial(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public Optional<String> name() {",
        "      return Optional.fromNullable(name);",
        "    }",
        "",
        "    @Override",
        "    public Optional<Integer> age() {",
        "      return Optional.fromNullable(age);",
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
    try {
      return new Formatter().formatSource(sourceBuilder.toString());
    } catch (FormatterException e) {
      throw new RuntimeException(e);
    }
  }

  private static Metadata createMetadataWithOptionalProperties(boolean bean) {
    GenericTypeElementImpl optional = newTopLevelGenericType("com.google.common.base.Optional");
    ClassTypeImpl integer = newTopLevelClass("java.lang.Integer");
    GenericTypeMirrorImpl optionalInteger = optional.newMirror(integer);
    ClassTypeImpl string = newTopLevelClass("java.lang.String");
    GenericTypeMirrorImpl optionalString = optional.newMirror(string);
    QualifiedName person = QualifiedName.of("com.example", "Person");
    QualifiedName generatedBuilder = QualifiedName.of("com.example", "Person_Builder");
    Property name = new Property.Builder()
        .setAllCapsName("NAME")
        .setBoxedType(optionalString)
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName(bean ? "getName" : "name")
        .setName("name")
        .setType(optionalString)
        .setUsingBeanConvention(bean)
        .build();
    Property age = new Property.Builder()
        .setAllCapsName("AGE")
        .setBoxedType(optionalInteger)
        .setCapitalizedName("Age")
        .setFullyCheckedCast(true)
        .setGetterName(bean ? "getAge" : "age")
        .setName("age")
        .setType(optionalInteger)
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
            .setCodeGenerator(new OptionalProperty(
                metadata, name, OptionalType.GUAVA, string, Optional.<TypeMirror>absent(), false))
            .build())
        .addProperties(age.toBuilder()
            .setCodeGenerator(new OptionalProperty(
                metadata, age, OptionalType.GUAVA, integer, Optional.<TypeMirror>of(INT), false))
            .build())
        .build();
  }

}
