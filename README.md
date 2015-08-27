`@FreeBuilder`
==============

_Automatic generation of the Builder pattern for Java 1.6+_

> The Builder pattern is a good choice when designing classes whose constructors
> or static factories would have more than a handful of parameters.
> &mdash; <em>Effective Java, Second Edition</em>, page 39

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Background](#background)
- [How to use `@FreeBuilder`](#how-to-use-freebuilder)
  - [Quick start](#quick-start)
  - [What you get](#what-you-get)
  - [Defaults and constraints](#defaults-and-constraints)
  - [Optional values](#optional-values)
    - [Using `@Nullable`](#using-nullable)
    - [Converting from `@Nullable`](#converting-from-nullable)
  - [Collections and Maps](#collections-and-maps)
  - [Nested buildable types](#nested-buildable-types)
  - [Builder construction](#builder-construction)
  - [Partials](#partials)
  - [GWT](#gwt)
- [Build tools and IDEs](#build-tools-and-ides)
  - [javac](#javac)
  - [Maven](#maven)
  - [Gradle](#gradle)
  - [Eclipse](#eclipse)
  - [IntelliJ](#intellij)
- [Troubleshooting](#troubleshooting)
  - [Troubleshooting javac](#troubleshooting-javac)
  - [Troubleshooting Eclipse](#troubleshooting-eclipse)
  - [Online resouces](#online-resouces)
- [Alternatives](#alternatives)
  - [AutoValue vs `@FreeBuilder`](#autovalue-vs-freebuilder)
  - [Proto vs `@FreeBuilder`](#proto-vs-freebuilder)
- [Wait, why "free"?](#wait-why-free)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->


Background
----------

Implementing the [Builder pattern](http://en.wikipedia.org/wiki/Builder_pattern)
in Java is tedious, error-prone and repetitive. Who hasn't seen a ten-argument
constructor, thought cross thoughts about the previous maintainers of the
class, then added "just one more"? Even a simple four-field class requires 39
lines of code for the most basic builder API, or 72 lines if you don't use a
utility like [AutoValue][] to generate the value boilerplate.

`@FreeBuilder` produces all the boilerplate for you, as well as free extras like
JavaDoc, getter methods, [collections support](#collections-and-maps),
[nested builders](#nested-buildable-types), and [partial values](#partials)
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


How to use `@FreeBuilder`
-------------------------


### Quick start

_See [Build tools and IDEs](#build-tools-and-ides) for how to add `@FreeBuilder` 
to your project's build and/or IDE._

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


### What you get

If you write the Person interface shown above, you get:

  * A builder class with:
     * a no-args constructor
     * JavaDoc
     * getters (throwing `IllegalStateException` for unset fields)
     * setters
     * `mergeFrom` methods to copy data from existing values or builders
     * a `build` method that verifies all fields have been set
        * [see below for default values and constraint checking](#defaults-and-constraints)
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


### Defaults and constraints

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


### Optional values

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
Note that `setDescription` will not accept a null; instead, call
`clearDescription()` to reset the field, or `setNullableDescription(Value)`
if you have a potentially-null value.

#### Using `@Nullable`

As Java currently stands, **you should strongly prefer Optional to returning
nulls**. Using null to represent unset properties is the classic example of
[Hoare's billion-dollar mistake][Hoare]: a silent source of errors that users
won't remember to write test cases for, and which won't be spotted in code reviews.
The large "air gap" between the declaration of the getter and the usage is the
cause of this problem. Optional uses the compiler to force the call sites to
perform explicit null handling, giving reviewers a better chance of seeing
mistakes. See also [Using and Avoiding Null][].

Obviously, greenfield code can trivially adopt Optional, but even existing APIs
can be converted to Optional via a simple refactoring sequence; see below.
However, if you have **compelling legacy reasons** that mandate using nulls,
you can disable null-checking by marking the getter method `@Nullable`. (Any
annotation type named "Nullable" will do, but you may wish to use
`javax.annotation.Nullable`, as used in [Google Guava][].)

[Google Guava]: https://github.com/google/guava
[Hoare]: http://www.infoq.com/presentations/Null-References-The-Billion-Dollar-Mistake-Tony-Hoare
[Using and Avoiding Null]: https://code.google.com/p/guava-libraries/wiki/UsingAndAvoidingNullExplained

#### Converting from `@Nullable`

This is the O(1), non-tedious, non&ndash;error-prone way we recomment converting
`@Nullable` to Optional:

 * Load all your code in Eclipse, or another IDE with support for renaming and
    inlining.
 * _[IDE REFACTOR]_ Rename all your `@Nullable` getters to `getNullableX`.
 * Add an Optional-returning getX
 * Implement your getNullableX methods as:  `return getX().orNull()`
 * _[IDE REFACTOR]_ Inline your getNullableX methods

At this point, you have effectively performed an automatic translation of a
`@Nullable` method to an Optional-returning one. Of course, your code is not
optimal yet (e.g.  `if (foo.getX().orNull() != null)`  instead of  `if
(foo.getX().isPresent())` ). Search-and-replace should get most of these issues.

 * _[IDE REFACTOR]_ Rename all your `@Nullable` setters to `setNullableX`.

Your API is now `@FreeBuilder`-compatible :)


### Collections and Maps

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


### Nested buildable types

`@FreeBuilder` has special support for buildable types like [protos][] and other
`@FreeBuilder` types:

  * The <code>set<em>X</em></code> method gains an overload for the property
    type's Builder, as shorthand for <code>set<em>X</em>(x.build())</code>
  * The <code>get<em>X</em></code> method is removed
  * A <code>get<em>X</em>Builder</code> method is added instead, returning a
    mutable Builder for the property
  * The property inherits the defaults of its Builder type

[protos]: https://developers.google.com/protocol-buffers/


### Builder construction

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


### Partials

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


### GWT

To enable [GWT][] serialization of the generated Value subclass, just add
`@GwtCompatible(serializable = true)` to your `@FreeBuilder`-annotated type, and
extend/implement `Serializable`. This will generate a [CustomFieldSerializer][],
and ensure all necessary types are whitelisted.

[GWT]: http://www.gwtproject.org/
[CustomFieldSerializer]: http://www.gwtproject.org/javadoc/latest/com/google/gwt/user/client/rpc/CustomFieldSerializer.html


Build tools and IDEs
--------------------

### javac

Download [freebuilder-1.2-rc1.jar][] and add it to the classpath (or
processorpath, if you supply one) on the command line. You will also need to add
[guava-18.0.jar][] to the classpath, as it is used in the generated code.

[freebuilder-1.2-rc1.jar]: http://repo1.maven.org/maven2/org/inferred/freebuilder/1.2-rc1/freebuilder-1.2-rc1.jar
[guava-18.0.jar]: http://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar

### Maven

Add the `@FreeBuilder` artifact as an optional dependency to your Maven POM:

```xml
<dependencies>
  <dependency>
    <groupId>org.inferred</groupId>
    <artifactId>freebuilder</artifactId>
    <version>1.2-rc1</version>
    <optional>true</optional>
  </dependency>
  <dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>18.0</version>
  </dependency>
</dependencies>
```

You may already have Guava, or want to use a different version; adjust accordingly.

### Gradle

Add the following lines to your dependencies:

```
compile 'org.inferred:freebuilder:1.2-rc1'
compile 'com.google.guava:guava:18.0'
```

You may already have Guava, or want to use a different version; adjust accordingly.

### Eclipse

_Condensed from [Eclipse Indigo's documentation][]._

Download [freebuilder-1.2-rc1.jar][] and [guava-18.0.jar][] and add them to your
project. Select them, right-click and choose **Build path > Add to Build path**.

In your projects properties dialog, go to **Java Compiler > Annotation
Processing** and ensure **Enable annotation processing** is checked.
Next, go to **Java Compiler > Annotation Processing > Factory Path**, select
**Add JARs**, and select `freebuilder-1.2-rc1.jar`.

[Eclipse Indigo's documentation]: http://help.eclipse.org/indigo/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_apt_getting_started.htm

### IntelliJ

_Condensed from the [IntelliJ 14.0.3 documentation][] and [Auto Issue #106][]._

Download [freebuilder-1.2-rc1.jar][] and [guava-18.0.jar][], add them to your
project, right-click them and select **Use as Project Library**.

In your Settings, go to **Build, Execution, Deployment > Compiler > Annotation
Processors** and ensure **Enable annotation processing** is selected, and
**Store generated sources relative to** is set to *Module content root*.
(If you have specified a processor path, ensure you add the new JAR to it.
Similarly, if you choose to specify processors explicitly, add
`org.inferred.freebuilder.processor.Processor` to the list.)

Run **Build > Rebuild Project**, then right-click the new `generated` folder
(this may have a different name if you have changed the **Production sources
directory** setting) and select **Mark Directory As > Generated Sources Root**.

[IntelliJ 14.0.3 documentation]: http://www.jetbrains.com/idea/webhelp/configuring-annotation-processing.html
[Auto Issue #106]: https://github.com/google/auto/issues/106


Troubleshooting
---------------


### Troubleshooting javac

If you make a mistake in your code (e.g. giving your value type a private
constructor), `@FreeBuilder` is designed to output a Builder superclass anyway,
with as much of the interface intact as possible, so the only errors you see
are the ones output by the annotation processor.

Unfortunately, `javac` has a broken design: if _any_ annotation processor
outputs _any error whatsoever_, *all* generated code is discarded, and all
references to that generated code are flagged as broken references. Since
your Builder API is generated, that means every usage of a `@FreeBuilder`
builder becomes an error. This deluge of false errors swamps the actual
error you need to find and fix. (See also [the related issue][issue 3].)

If you find yourself in this situation, search the output of `javac` for the
string "@FreeBuilder type"; nearly all errors include this in their text.

[issue 3]: https://github.com/google/FreeBuilder/issues/3

### Troubleshooting Eclipse

Eclipse manages, somehow, to be worse than `javac` here. It will never output
annotation processor errors unless there is another error of some kind; and,
even then, only after an incremental, not clean, compile. In practice, most
mistakes are made while editing the `@FreeBuilder` type, which means the
incremental compiler will flag the errors. If you find a generated superclass
appears to be missing after a clean compile, however, try touching the
relevant file to trigger the incremental compiler. (Or run javac.)

### Online resouces

  * If you find yourself stuck, needing help, wondering whether a given
    behaviour is a feature or a bug, or just wanting to discuss `@FreeBuilder`,
    please join and/or post to [the `@FreeBuilder` mailing list][mailing list].
  * To see a list of open issues, or add a new one, see [the `@FreeBuilder`
    issue tracker][issue tracker].
  * To submit a bug fix, or land a sweet new feature, [read up on how to
    contribute][contributing].

[mailing list]: https://groups.google.com/forum/#!forum/freebuilder
[issue tracker]: https://github.com/google/freebuilder/issues
[contributing]: https://github.com/google/FreeBuilder/blob/master/CONTRIBUTING.md


Alternatives
------------


### AutoValue vs `@FreeBuilder`

<em><strong>Why is `@FreeBuilder` better than [AutoValue][]?</strong></em>

It’s not! AutoValue provides an implementing class with a package-visible
constructor, so you can easily implement the Factory pattern. If you’re writing
an immutable type that needs a small number of values to create (Effective Java
suggests at most three), and is not likely to require more in future, use the
Factory pattern.

How about if you want a builder? [AutoValue.Builder][] lets you explicitly
specify a minimal Builder interface that will then be implemented by generated
code, while `@FreeBuilder` provides a generated builder API. AutoValue.Builder
is better if you must have a minimal API&mdash;for instance, in an Android
project, where every method is expensive&mdash;or strongly prefer a
visible-in-source API at the expense of many useful methods. Otherwise, consider
using `@FreeBuilder` to implement the Builder pattern.

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
[AutoValue.Builder]: https://github.com/google/auto/tree/master/value#builders


### Proto vs `@FreeBuilder`

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


Wait, why "free"?
-----------------

  * Free as in beer: you don't pay the cost of writing or maintaining the builder
    code.
  * Free as in flexible: you should always be able to customize the builder where
    the defaults don't work for you.
  * Free as in liberty: you can always drop `@FreeBuilder` and walk away with
    the code it generated for you.

License
-------

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


