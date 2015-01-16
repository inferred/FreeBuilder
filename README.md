`@FreeBuilder`
=========================

_Automatic generation of the Builder pattern for Java 1.6+_

> The Builder pattern is a good choice when designing classes whose constructors
> or static factories would have more than a handful of parameters.
> &mdash; <em>Effective Java, Second Edition</em>, page 39


<h2 id=background>Background</h2>

Implementing the [Builder pattern](http://en.wikipedia.org/wiki/Builder_pattern)
in Java is tedious, error-prone and repetitive. Who hasn't seen a ten-argument
constructor, thought cross thoughts about the previous maintainers of the
class, then added "just one more"? Even a simple four-field class requires 39
lines of code for the most basic builder API, or 72 lines if you don't use a
utility like [AutoValue][] to generate the value boilerplate.

`@FreeBuilder` produces all the boilerplate for you, as well as free extras like
JavaDoc, getter methods, [collections support](#collections_and_maps),
[nested builders](#nested_buildable_types), and [partial values](#partials)
(used in testing), which are highly useful, but would very rarely justify
their creation and maintenance burden in hand-crafted code. (We also reserve
the right to add more awesome methods in future!)


> [The Builder pattern] is more verbose&#8230;so should only be used if there are
> enough parameters, say, four or more. But keep in mind that you may want to add
> parameters in the future. If you start out with constructors or static
> factories, and add a builder when the class evolves to the point where the
> number of parameters starts to get out of hand, the obsolete constructors or
> static factories will stick out like a sore thumb. Therefore, it's often better
> to start with a builder in the first place.
> &mdash; <em>Effective Java, Second Edition</em>, page 39


<h2 id=how_to_use_freebuilder>How to use <code>@FreeBuilder</code></h2>


<h3 id=quick_start>Quick start</h3>

Add the `@FreeBuilder` artifact as an optional dependency to your Maven POM:

```xml
<dependencies>
  <dependency>
    <groupId>org.inferred</groupId>
    <artifactId>freebuilder</artifactId>
    <version>1.0-rc2</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

Create your value type (e.g. `Person`) as an interface or abstract class,
containing an abstract accessor method for each desired field. This accessor
must be non-void, parameterless, and start with 'get' or 'is'. Add the
`@FreeBuilder` annotation to your class, and it will automatically generate an
implementing class and a package-visible builder API (`Person_Builder`), which
you must subclass. For instance:


```java
import org.inferred.freebuilder.FreeBuilder;

@FreeBuilder
public interface Person {
  /** Returns the person's full (English) name. */
  String getName();
  /** Returns the person's age in years, rounded down. */
  int getAge();
  /** Builder of {@link Person} instances. */
  class Builder extends Person_Builder { }
}
```

If you are writing an abstract class, or using Java 8, you may wish to hide the
builder's constructor and manually provide instead a static `builder()` method
on the value type (though <em>Effective Java</em> does not do this).


<h3 id=what_you_get>What you get</h3>

If you write the Person interface shown above, you get:

  * A builder class with:
     * a no-args constructor
     * JavaDoc
     * getters (throwing `IllegalStateException` for unset fields)
     * setters
     * `mergeFrom` methods to copy data from existing values or builders
     * a `build` method that verifies all fields have been set
        * [see below for default values and constraint checking](#defaults_and_constraints)
  * An implementation of `Person` with:
     * `toString`
     * `equals` and `hashCode`
  * A [partial](#partials) implementation of `Person` for unit tests with:
     * `UnsupportedOperationException`-throwing getters for unset fields
     * `toString`
     * `equals` and `hashCode`


```java
Person person = new Person.Builder()
    .setName("Phil")
    .setAge(31)
    .build();
System.out.println(person);  // Person{name=Phil, age=31}
```


<h3 id=defaults_and_constraints>Defaults and constraints</h3>

We use method overrides to add customization like default values and constraint
checks. For instance:


```java
@FreeBuilder
public interface Person {
  /** Returns the person's full (English) name. */
  String getName();
  /** Returns the person's age in years, rounded down. */
  int getAge();
  /** Returns a human-readable description of the person. */
  String getDescription();
  /** Builder class for {@link Person}. */
  class Builder extends Person_Builder {
    public Builder() {
      // Set defaults in the builder constructor.
      setDescription("Indescribable");
    }
    @Override public Builder setAge(int age) {
      // Check single-field (argument) constraints in the setter methods.
      checkArgument(age >= 18);
      return super.setAge(age);
    }
    @Override public Person build() {
      // Check cross-field (state) constraints in the build method.
      Person person = super.build();
      checkState(!person.getDescription().contains(person.getName()));
      return person;
    }
  }
}
```


<h3 id=optional_values>Optional values</h3>

If a property is optional&mdash;that is, has no reasonable default&mdash;then
use [the Optional type][]. It will default to Optional.absent(), and the Builder
will gain additional convenience setter methods.

[the Optional type]: http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/base/Optional.html


```java
  /** Returns an optional human-readable description of the person. */
  Optional<String> getDescription();
```

Prefer to use explicit defaults where meaningful, as it avoids the need for
edge-case code; but prefer Optional to ad-hoc 'not set' defaults, like -1 or
the empty string, as it forces the user to think about those edge cases.
@FreeBuilder does <strong>not</strong> support nulls and will throw a
NullPointerException if one is passed to a setter.


<h3 id=collections_and_maps>Collections and Maps</h3>

`@FreeBuilder` has special support for <code>[List][]</code>,
<code>[Set][]</code>, <code>[Multiset][]</code>, <code>[Map][]</code> and
<code>[Multimap][]</code> properties:

  * The Builder's <code>set<em>X</em></code> method is removed
  * Mutation methods are added instead: <code>add<em>X</em></code> (collections),
    <code>put<em>X</em></code> (maps) and <code>clear<em>X</em></code>
  * The Builder's <code>get<em>X</em></code> method returns an unmodifiable view
    of the current values: when the Builder is changed, the view also changes
  * The property defaults to an empty collection
  * The value type returns immutable collections

[List]: http://docs.oracle.com/javase/tutorial/collections/interfaces/list.html
[Set]: http://docs.oracle.com/javase/tutorial/collections/interfaces/set.html
[Multiset]: https://code.google.com/p/guava-libraries/wiki/NewCollectionTypesExplained#Multiset
[Map]: http://docs.oracle.com/javase/tutorial/collections/interfaces/map.html
[Multimap]: https://code.google.com/p/guava-libraries/wiki/NewCollectionTypesExplained#Multimap


<h3 id=nested_buildable_types>Nested buildable types</h3>

`@FreeBuilder` has special support for buildable types like [protos][] and other
`@FreeBuilder` types:

  * The <code>set<em>X</em></code> method gains an overload for the property
    type's Builder, as shorthand for <code>set<em>X</em>(x.build())</code>
  * The <code>get<em>X</em></code> method is removed
  * A <code>get<em>X</em>Builder</code> method is added instead, returning a
    mutable Builder for the property
  * The property inherits the defaults of its Builder type

[protos]: https://developers.google.com/protocol-buffers/


<h3 id=builder_construction>Builder construction</h3>

<em>Effective Java</em> recommends passing required parameters in to the Builder
constructor. While we follow most of the recommendations therein, we explicitly
do not follow this one: while you gain compile-time verification that all
parameters are set, you lose flexibility in client code, as well as opening
yourself back up to the exact same subtle usage bugs as traditional constructors
and factory methods. For the default `@FreeBuilder` case, where all parameters
are required, this does not scale.

If you want to follow <em>Effective Java</em> more faithfully in your own types,
however, just create the appropriate constructor in your builder subclass:


```java
    public Builder(String name, int age) {
      // Set all initial values in the builder constructor
      setName(name);
      setAge(age);
    }
```

Implementation note: in javac, we spot these fields being set in the
constructor, and do not check again at runtime. 


<h3 id=partials>Partials</h3>

A <em>partial value</em> is an implementation of the value type which does not
conform to the type's state constraints. It may be missing required fields, or
it may violate a cross-field constraint.


```java
Person person = new Person.Builder()
    .setName("Phil")
    .buildPartial();  // build() would throw an IllegalStateException here
System.out.println(person);  // prints: partial Person{name="Phil"}
person.getAge();  // throws UnsupportedOperationException
```

As partials violate the (legitimate) expectations of your program, they must
<strong>not</strong> be created in production code. (They may also affect the
performance of your program, as the JVM cannot make as many optimizations.)
However, when testing a component which does not rely on the full state
restrictions of the value type, partials can reduce the fragility of your test
suite, allowing you to add new required fields or other constraints to an
existing value type without breaking swathes of test code.


<h3 id=ides>IDEs</h3>

Follow your IDE's annotation processing instructions [[Eclipse instructions][];
[IntelliJ instructions][]].

[Eclipse instructions]: http://help.eclipse.org/indigo/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_apt_getting_started.htm
[IntelliJ instructions]: http://www.jetbrains.com/idea/webhelp/configuring-annotation-processing.html


<h3 id=gwt>GWT</h3>

To enable [GWT][] serialization of the generated Value subclass, just add
`@GwtCompatible(serializable = true)` to your `@FreeBuilder`-annotated type, and
extend/implement `Serializable`. This will generate a [CustomFieldSerializer][],
and ensure all necessary types are whitelisted.

[GWT]: http://www.gwtproject.org/
[CustomFieldSerializer]: http://www.gwtproject.org/javadoc/latest/com/google/gwt/user/client/rpc/CustomFieldSerializer.html


<h2 id=alternatives>Alternatives</h2>


<h3 id=autovalue_vs_freebuilder>AutoValue vs <code>@FreeBuilder</code></h3>

<em><strong>Why is `@FreeBuilder` better than [AutoValue][]?</strong></em>

It's not! AutoValue provides an implementing class with a package-visible
constructor, so you can easily implement the Factory pattern. `@FreeBuilder`
provides an implementing class with a builder API, so you can easily implement
and customize the Builder pattern. So, like chalk and cheese, which you use
depends on what you want!

If you're writing an immutable type that needs a small number of values to
create (<em>Effective Java</em> suggests at most three), and is not likely to
require more in future, use the Factory pattern. Otherwise, consider using
`@FreeBuilder` to implement the Builder pattern.

<em><strong>I used [AutoValue][], but now have more than three properties! How
do I migrate to `@FreeBuilder`?</strong></em>

  1. Ensure your getter methods start with 'get' or 'is'.
  2. Change your annotation to `@FreeBuilder`.
  3. Rewrite your factory method(s) to use the builder API.
  4. Inline your factory method(s) with a refactoring tool (e.g. Eclipse).

You can always skip step 4 and have both factory and builder methods, if that
seems cleaner!


<em><strong>Can I use both [AutoValue][] and `@FreeBuilder`?</strong></em>

Not really. You can certainly use both annotations, but you will end up with
two different implementing classes that never compare equal, even if they have
the same values.

[AutoValue]: https://github.com/google/auto/tree/master/value


<h3 id=proto_vs_freebuilder>Proto vs <code>@FreeBuilder</code></h3>

<em><strong>[Protocol buffers][] have provided builders for ages. Why should I
use `@FreeBuilder`?</strong></em>

Protocol buffers are cross-platform, backwards- and forwards-compatible, and
have a very efficient wire format. Unfortunately, they do not support custom
validation logic; nor can you use appropriate Java domain types, such as
[Instant][] or [Range][]. Generally, it will be clear which one is appropriate
for your use-case.

[Protocol buffers]: https://developers.google.com/protocol-buffers/
[Instant]: http://docs.oracle.com/javase/8/docs/api/java/time/Instant.html
[Range]: http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/collect/Range.html


<h2 id=why_free>Wait, why "free"?</h2>

  * Free as in beer: you don't pay the cost of writing or maintaining the builder
    code.
  * Free as in flexible: you should always be able to customize the builder where
    the defaults don't work for you.
  * Free as in liberty: you can always drop `@FreeBuilder` and walk away with
    the code it generated for you.

<h2 id=license>License</h2>

    Copyright 2014 Google Inc. All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


