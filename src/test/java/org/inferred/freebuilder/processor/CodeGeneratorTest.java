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
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newNestedClass;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.inferred.freebuilder.processor.util.PrimitiveTypeImpl.INT;

import com.google.common.base.Joiner;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.ImpliedClass;
import org.inferred.freebuilder.processor.util.SourceStringBuilder;
import org.inferred.freebuilder.processor.util.NameImpl;
import org.inferred.freebuilder.processor.util.PackageElementImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.cglib.proxy.CallbackHelper;
import org.mockito.cglib.proxy.Enhancer;
import org.mockito.cglib.proxy.InvocationHandler;
import org.mockito.cglib.proxy.NoOp;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

@RunWith(JUnit4.class)
public class CodeGeneratorTest {

  private static final PackageElement PACKAGE = new PackageElementImpl("com.example");

  @Test
  public void testSimpleDataType() {
    TypeElement person = newTopLevelClass("com.example.Person").asElement();
    TypeMirror string = newTopLevelClass("java.lang.String");
    ImpliedClass generatedBuilder =
        new ImpliedClass(PACKAGE, "Person_Builder", person, elements());
    Property.Builder name = new Property.Builder()
        .setAllCapsName("NAME")
        .setBoxedType(string)
        .setCapitalizedName("Name")
        .setFullyCheckedCast(true)
        .setGetterName("getName")
        .setName("name")
        .setType(string);
    Property.Builder age = new Property.Builder()
        .setAllCapsName("AGE")
        .setBoxedType(newTopLevelClass("java.lang.Integer"))
        .setCapitalizedName("Age")
        .setFullyCheckedCast(true)
        .setGetterName("getAge")
        .setName("age")
        .setType(INT);
    Metadata metadata = new Metadata.Builder(elements())
        .setBuilder(newNestedClass(person, "Builder").asElement())
        .setBuilderFactory(BuilderFactory.NO_ARGS_CONSTRUCTOR)
        .setBuilderSerializable(false)
        .setGeneratedBuilder(generatedBuilder)
        .setGwtCompatible(false)
        .setGwtSerializable(false)
        .setPartialType(generatedBuilder.createNestedClass("Partial"))
        .addProperty(name
            .setCodeGenerator(
                new DefaultPropertyFactory.CodeGenerator(name.build(), "setName", false))
            .build())
        .addProperty(age
            .setCodeGenerator(
                new DefaultPropertyFactory.CodeGenerator(age.build(), "setAge", false))
            .build())
        .setPropertyEnum(generatedBuilder.createNestedClass("Property"))
        .setType(person)
        .setValueType(generatedBuilder.createNestedClass("Value"))
        .build();

    SourceStringBuilder sourceBuilder = SourceStringBuilder.simple();
    new CodeGenerator().writeBuilderSource(sourceBuilder, metadata);

    assertThat(sourceBuilder.toString()).isEqualTo(Joiner.on('\n').join(
        "/**",
        " * Auto-generated superclass of {@link Person.Builder},",
        " * derived from the API of {@link Person}.",
        " */",
        "@Generated(\"org.inferred.freebuilder.processor.CodeGenerator\")",
        "abstract class Person_Builder {",
        "",
        "  private static final Joiner COMMA_JOINER = Joiner.on(\", \").skipNulls();",
        "",
        "  private enum Property {",
        "    NAME(\"name\"),",
        "    AGE(\"age\"),",
        "    ;",
        "",
        "    private final String name;",
        "",
        "    private Property(String name) {",
        "      this.name = name;",
        "    }",
        "",
        "    @Override public String toString() {",
        "      return name;",
        "    }",
        "  }",
        "",
        "  private String name;",
        "  private int age;",
        "  private final EnumSet<Person_Builder.Property> _unsetProperties =",
        "      EnumSet.allOf(Person_Builder.Property.class);",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getName()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   * @throws NullPointerException if {@code name} is null",
        "   */",
        "  public Person.Builder setName(String name) {",
        "    this.name = Preconditions.checkNotNull(name);",
        "    _unsetProperties.remove(Person_Builder.Property.NAME);",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getName()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public String getName() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.NAME),",
        "        \"name not set\");",
        "    return name;",
        "  }",
        "",
        "  /**",
        "   * Sets the value to be returned by {@link Person#getAge()}.",
        "   *",
        "   * @return this {@code Builder} object",
        "   */",
        "  public Person.Builder setAge(int age) {",
        "    this.age = age;",
        "    _unsetProperties.remove(Person_Builder.Property.AGE);",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Returns the value that will be returned by {@link Person#getAge()}.",
        "   *",
        "   * @throws IllegalStateException if the field has not been set",
        "   */",
        "  public int getAge() {",
        "    Preconditions.checkState(",
        "        !_unsetProperties.contains(Person_Builder.Property.AGE),",
        "        \"age not set\");",
        "    return age;",
        "  }",
        "",
        "  private static final class Value extends Person {",
        "    private final String name;",
        "    private final int age;",
        "",
        "    private Value(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "    }",
        "",
        "    @Override",
        "    public String getName() {",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public int getAge() {",
        "      return age;",
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
        "      if (age != other.age) {",
        "        return false;",
        "      }",
        "      return true;",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      return Arrays.hashCode(new Object[] { name, age });",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"Person{\"",
        "          + \"name=\" + name + \", \"",
        "          + \"age=\" + age + \"}\";",
        "    }",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created {@link Person} based on the contents of the {@code Builder}.",
        "   *",
        "   * @throws IllegalStateException if any field has not been set",
        "   */",
        "  public Person build() {",
        "    Preconditions.checkState(_unsetProperties.isEmpty(),"
            + " \"Not set: %s\", _unsetProperties);",
        "    return new Person_Builder.Value(this);",
        "  }",
        "",
        "  /**",
        "   * Sets all property values using the given {@code Person} as a template.",
        "   */",
        "  public Person.Builder mergeFrom(Person value) {",
        "    setName(value.getName());",
        "    setAge(value.getAge());",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Copies values from the given {@code Person.Builder}.", // TODO @link
        "   * Does not affect any properties not set on the input.",
        "   */",
        "  public Person.Builder mergeFrom(Person.Builder template) {",
        "    // Upcast to access the private _unsetProperties field.",
        "    // Otherwise, oddly, we get an access violation.",
        "    EnumSet<Person_Builder.Property> _templateUnset = ((Person_Builder) template)"
            + "._unsetProperties;",
        "    if (!_templateUnset.contains(Person_Builder.Property.NAME)) {",
        "    setName(template.getName());", // TODO correct indent
        "    }",
        "    if (!_templateUnset.contains(Person_Builder.Property.AGE)) {",
        "    setAge(template.getAge());",
        "    }",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  /**",
        "   * Resets the state of this builder.",
        "   */",
        "  public Person.Builder clear() {",
        "    Person_Builder template = new Person.Builder();",
        "    name = template.name;",
        "    age = template.age;",
        "    _unsetProperties.clear();",
        "    _unsetProperties.addAll(template._unsetProperties);",
        "    return (Person.Builder) this;",
        "  }",
        "",
        "  private static final class Partial extends Person {",
        "    private final String name;",
        "    private final int age;",
        "    private final EnumSet<Person_Builder.Property> _unsetProperties;",
        "",
        "    Partial(Person_Builder builder) {",
        "      this.name = builder.name;",
        "      this.age = builder.age;",
        "      this._unsetProperties = builder._unsetProperties.clone();",
        "    }",
        "",
        "    @Override",
        "    public String getName() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.NAME)) {",
        "        throw new UnsupportedOperationException(\"name not set\");",
        "      }",
        "      return name;",
        "    }",
        "",
        "    @Override",
        "    public int getAge() {",
        "      if (_unsetProperties.contains(Person_Builder.Property.AGE)) {",
        "        throw new UnsupportedOperationException(\"age not set\");",
        "      }",
        "      return age;",
        "    }",
        "",
        "    @Override",
        "    public boolean equals(Object obj) {",
        "      if (!(obj instanceof Person_Builder.Partial)) {",
        "        return false;",
        "      }",
        "      Person_Builder.Partial other = (Person_Builder.Partial) obj;",
        "      if (name != other.name",
        "          && (name == null || !name.equals(other.name))) {",
        "        return false;",
        "      }",
        "      if (age != other.age) {",
        "        return false;",
        "      }",
        "      return _unsetProperties.equals(other._unsetProperties);",
        "    }",
        "",
        "    @Override",
        "    public int hashCode() {",
        "      int result = 1;",
        "      result *= 31;",
        "      result += ((name == null) ? 0 : name.hashCode());",
        "      result *= 31;",
        "      result += ((Integer) age).hashCode();",
        "      result *= 31;",
        "      result += _unsetProperties.hashCode();",
        "      return result;",
        "    }",
        "",
        "    @Override",
        "    public String toString() {",
        "      return \"partial Person{\"",
        "          + COMMA_JOINER.join(",
        "              (!_unsetProperties.contains(Person_Builder.Property.NAME)",
        "                  ? \"name=\" + name : null),",
        "              (!_unsetProperties.contains(Person_Builder.Property.AGE)",
        "                  ? \"age=\" + age : null))",
        "          + \"}\";",
        "    }",
        "  }",
        "",
        "  /**",
        "   * Returns a newly-created partial {@link Person}",
        "   * based on the contents of the {@code Builder}.",
        "   * State checking will not be performed.",
        "   * Unset properties will throw an {@link UnsupportedOperationException}",
        "   * when accessed via the partial object.",
        "   *",
        "   * <p>Partials should only ever be used in tests.",
        "   */",
        "  @VisibleForTesting()",
        "  public Person buildPartial() {",
        "    return new Person_Builder.Partial(this);",
        "  }",
        "}\n"));
  }

  private static Elements elements() {
    Enhancer e = new Enhancer();
    e.setClassLoader(ElementsImpl.class.getClassLoader());
    e.setSuperclass(ElementsImpl.class);
    CallbackHelper helper = new CallbackHelper(ElementsImpl.class, new Class<?>[] {}) {
      @Override
      protected Object getCallback(Method method) {
        if (Modifier.isAbstract(method.getModifiers())) {
          return new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) {
              throw new UnsupportedOperationException(
                  "Not yet implemented by " + ElementsImpl.class.getCanonicalName());
            }
          };
        } else {
          return NoOp.INSTANCE;
        }
      }
    };
    e.setCallbacks(helper.getCallbacks());
    e.setCallbackFilter(helper);
    return (Elements) e.create();
  }

  abstract static class ElementsImpl implements Elements {
    @Override
    public Name getName(CharSequence cs) {
      return new NameImpl(cs.toString());
    }
  }
}

