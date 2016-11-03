`@FreeBuilder`
==============

_Automatic generation of the Builder pattern for Java 1.6+_

[![Maven Central](https://img.shields.io/maven-central/v/org.inferred/freebuilder.svg)](http://mvnrepository.com/artifact/org.inferred/freebuilder)
[![Travis CI](https://travis-ci.org/google/FreeBuilder.svg?branch=master)](https://travis-ci.org/google/FreeBuilder)
[![Gitter](https://img.shields.io/gitter/room/inferred/freebuilder.svg?style=flat-square)](https://gitter.im/inferred-freebuilder/Lobby)

> The Builder pattern is a good choice when designing classes whose constructors
> or static factories would have more than a handful of parameters.
> &mdash; <em>Effective Java, Second Edition</em>, page 39

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Background](#background)
- [How to use `@FreeBuilder`](#how-to-use-freebuilder)
  - [Quick start](#quick-start)
  - [What you get](#what-you-get)
  - [Accessor methods](#accessor-methods)
  - [Defaults and constraints](#defaults-and-constraints)
  - [Optional values](#optional-values)
    - [Using `@Nullable`](#using-nullable)
    - [Converting from `@Nullable`](#converting-from-nullable)
  - [Collections and Maps](#collections-and-maps)
  - [Nested buildable types](#nested-buildable-types)
  - [Builder construction](#builder-construction)
  - [Partials](#partials)
  - [Jackson](#jackson)
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
  - [Immutables vs `@FreeBuilder`](#immutables-vs-freebuilder)
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
JavaDoc, getter methods, mapper methods (Java 8+), [collections support](#collections-and-maps),
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
     * lambda-accepting mapper methods (Java 8+)
     * `from` and `mergeFrom` methods to copy data from existing values or builders
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


### Accessor methods

For each property `foo`, the builder gets:

| Method | Description |
|:------:| ----------- |
| A setter method, `setFoo` | Throws a NullPointerException if provided a null. (See the sections on [Optional](#optional-values) and [Nullable](#using-nullable) for ways to store properties that can be missing.) |
| A getter method, `getFoo` | Throws an IllegalStateException if the property value has not yet been set. |
| A mapper method, `mapFoo` | *Java 8+* Takes a [UnaryOperator]. Replaces the current property value with the result of invoking the unary operator on it. Throws a NullPointerException if the operator, or the value it returns, is null. Throws an IllegalStateException if the property value has not yet been set. |

The mapper methods are very useful when modifying existing values, e.g.

```java
Person olderPerson = Person.Builder.from(person)
    .mapAge(age -> age + 1)
    .build();
```

[UnaryOperator]: https://docs.oracle.com/javase/8/docs/api/java/util/function/UnaryOperator.html


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
    @Override Builder setAge(int age) {
      // Check single-field (argument) constraints in the setter method.
      checkArgument(age >= 0);
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
use [the Java 8 Optional type][] (or [the Guava Optional type][] for
backwards-compatibility).

[the Java 8 Optional type]: https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html
[the Guava Optional type]: http://google.github.io/guava/releases/19.0/api/docs/com/google/common/base/Optional.html


```java
  /** Returns an optional human-readable description of the person. */
  Optional<String> getDescription();
```

This property will now default to Optional.empty(), and the Builder
will gain additional convenience setter methods:

| Method | Description |
|:------:| ----------- |
| `setDescription(String value)` | Sets the property to `Optional.of(value)`. Throws a NullPointerException if value is null; this avoids users accidentally clearing an optional value in a way that peer review is unlikely to catch. |
| `clearDescription()` | Sets the property to `Optional.empty()`. |
| `setDescription(Optional<String> value)` | Sets the property to `value`. |
| `setNullableDescription(String value)` | Sets the property to `Optional.ofNullable(value)`. |
| `mapDescription(UnaryOperator<String> mapper` | *Java 8+* If the property value is not empty, this replaces the value with the result of invoking `mapper` with the existing value, or clears it if `mapper` returns null. Throws a NullPointerException if `mapper` is null. |

Prefer to use explicit defaults where meaningful, as it avoids the need for
edge-case code; but prefer Optional to ad-hoc 'not set' defaults, like -1 or
the empty string, as it forces the user to think about those edge cases.

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
`javax.annotation.Nullable`, as used in [Google Guava][Guava].)

```java
  /** Returns an optional title to use when addressing the person. */
  @Nullable String getTitle();
```

This property will now default to null, and the Builder's setter methods will
change their null-handling behaviour:

| Method | Description |
|:------:| ----------- |
| `setTitle(@Nullable String title)` | Sets the property to `title`. |
| `getTitle()` | Returns the current value of the property. May be null. |
| `mapTitle(UnaryOperator<String> mapper)` | *Java 8+* Takes a [UnaryOperator]. Replaces the current property value, if it is not null, with the result of invoking `mapper` on it. Throws a NullPointerException if `mapper` is null. `mapper` may return a null. |

[Guava]: https://github.com/google/guava
[Hoare]: http://www.infoq.com/presentations/Null-References-The-Billion-Dollar-Mistake-Tony-Hoare
[Using and Avoiding Null]: https://github.com/google/guava/wiki/UsingAndAvoidingNullExplained

#### Converting from `@Nullable`

This is the O(1), non-tedious, non&ndash;error-prone way we recomment converting
`@Nullable` to Optional:

 * Load all your code in Eclipse, or another IDE with support for renaming and
    inlining.
 * _[IDE REFACTOR]_ Rename all your `@Nullable` getters to `getNullableX`.
 * Add an Optional-returning getX
 * Implement your getNullableX methods as:  `return getX().orElse(null)`
   <br>(Guava: `return getX().orNull()`)
 * _[IDE REFACTOR]_ Inline your getNullableX methods

At this point, you have effectively performed an automatic translation of a
`@Nullable` method to an Optional-returning one. Of course, your code is not
optimal yet (e.g.  `if (foo.getX().orElse(null) != null)`  instead of  `if
(foo.getX().isPresent())` ). Search-and-replace should get most of these issues.

 * _[IDE REFACTOR]_ Rename all your `@Nullable` setters to `setNullableX`.

Your API is now `@FreeBuilder`-compatible :)


### Collections and Maps

`@FreeBuilder` has special support for collection and map properties, removing
the `setFoo` accessor method and generating new ones appropriate to the type.
Collection and map properties default to an empty collection/map and cannot hold
nulls.

```java
  /** Returns a list of descendents for this person. **/
  List<String> getDescendants();
```

A <code>[List][]</code>, <code>[Set][]</code> or <code>[Multiset][]</code>
property called 'descendants' would generate:

| Method | Description |
|:------:| ----------- |
| `addDescendants(String element)` | Appends `element` to the collection of descendants. If descendants is a set and the element is already present, it is ignored. Throws a NullPointerException if element is null. |
| `addDescendants(String... elements)` | Appends all `elements` to the collection of descendants. If descendants is a set, any elements already present are ignored. Throws a NullPointerException if elements, or any of the values it holds, is null. |
| `addAllDescendants(​Iterable<String> elements)` | Appends all `elements` to the collection of descendants. If descendants is a set, any elements already present are ignored. Throws a NullPointerException if elements, or any of the values it holds, is null. |
| `mutateDescendants(​Consumer<‌.‌.‌.‌<String>> mutator)` | *Java 8+* Invokes the [Consumer] `mutator` with the collection of descendants. (The mutator takes a list, set or map as appropriate.) Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream] expecting the returned collection to replace the existing collection. |
| `clearDescendants()` | Removes all elements from the collection of descendants, leaving it empty. |
| `getDescendants()` | Returns an unmodifiable view of the collection of descendants. Changes to the collection held by the builder will be reflected in the view. |

```java
  /** Returns a map of favourite albums by year. **/
  Map<Integer, String> getAlbums();
```

A <code>[Map][]</code> property called 'albums' would generate:

| Method | Description |
|:------:| ----------- |
| `putAlbums(int key, String value)` | Associates `key` with `value` in albums.  Throws a NullPointerException if either parameter is null. Replaces any existing entry. |
| `putAllAlbums(Map<? extends Integer, ? extends String> map)` | Associates all of `map`'s keys and values in albums. Throws a NullPointerException if the map is null or contains a null key or value. Throws an IllegalArgumentException if any key is already present. |
| `removeAlbums(int key)` | Removes the mapping for `key` from albums. Throws a NullPointerException if the parameter is null. Does nothing if the key is not present. |
| `mutateAlbums(​Consumer<Map<Integer, String>> mutator)` | *Java 8+* Invokes the [Consumer] `mutator` with the map of albums. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream] expecting the returned map to replace the existing map. |
| `clearAlbums()` | Removes all mappings from albums, leaving it empty. |
| `getAlbums()` | Returns an unmodifiable view of the map of albums. Changes to the map held by the builder will be reflected in this view. |

```java
  /** Returns a multimap of all awards by year. **/
  SetMultimap<Integer, String> getAwards();
```

A <code>[Multimap][]</code> property called 'awards' would generate:

| Method | Description |
|:------:| ----------- |
| `putAwards(int key, String value)` | Associates `key` with `value` in awards. Throws a NullPointerException if either parameter is null. |
| `putAllAwards(int key, Iterable<? extends String> values)` | Associates `key` with every element of `values` in awards. Throws a NullPointerException if either parameter, or any value, is null. |
| `putAllAwards(Map<? extends Integer, ? extends String> map)` | Associates all of `map`'s keys and values in awards. Throws a NullPointerException if the map is null or contains a null key or value. If awards is a map, an IllegalArgumentException will be thrown if any key is already present. |
| `removeAwards(int key, String value)` | Removes the single pair `key`-`value` from awards. If multiple pairs match, which is removed is unspecified. Throws a NullPointerException if either parameter is null. |
| `removeAllAwards(int key)` | Removes all values associated with `key` from awards. Throws a NullPointerException if the key is null. |
| `mutateAwards(​Consumer<Map<Integer, String>> mutator)` | *Java 8+* Invokes the [Consumer] `mutator` with the multimap of awards. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream] expecting the returned multimap to replace the existing multimap. |
| `clearAwards()` | Removes all mappings from awards, leaving it empty. |
| `getAwards()` | Returns an unmodifiable view of the multimap of awards. Changes to the multimap held by the builder will be reflected in this view. |

In all cases, the value type will return immutable objects from its getter.

The mutator methods are useful for invoking methods not directly exposed on the builder, like [subList], or methods that take a mutable collection, like [sort]:

```java
personBuilder
    // Delete the fourth and fifth descendants in the list
    .mutateDescendants(d -> d.subList(3,5).clear())
    // Sort the remaining descendants
    .mutateDescendants(Collections::sort);
```

[List]: http://docs.oracle.com/javase/tutorial/collections/interfaces/list.html
[Set]: http://docs.oracle.com/javase/tutorial/collections/interfaces/set.html
[Multiset]: https://github.com/google/guava/wiki/NewCollectionTypesExplained#multiset
[Map]: http://docs.oracle.com/javase/tutorial/collections/interfaces/map.html
[Multimap]: https://github.com/google/guava/wiki/NewCollectionTypesExplained#multimap
[sort]: http://docs.oracle.com/javase/8/docs/api/java/util/Collections.html#sort-java.util.List-
[stream]: https://docs.oracle.com/javase/8/docs/api/java/util/Collection.html#stream--
[subList]: http://docs.oracle.com/javase/8/docs/api/java/util/List.html#subList-int-int-


### Nested buildable types

```java
  /** Returns the person responsible for this project. */
  Person getOwner();
```

`@FreeBuilder` has special support for buildable types like [protos][] and other
`@FreeBuilder` types. A buildable property called 'owner' would generate:

| Method | Description |
|:------:| ----------- |
| `setOwner(Person owner)` | Sets the owner. Throws a NullPointerException if provided a null. |
| `setOwner(Person.Builder builder)` | Calls `build()` on `builder` and sets the owner to the result. Throws a NullPointerException if builder or the result of calling `build()` is null. |
| `getOwnerBuilder()` | Returns a builder for the owner property. Unlike other getter methods in FreeBuilder-generated API, this object is mutable, and modifying it **will modify the underlying property**. |
| `mutateOwner(Consumer<Person.Builder> mutator)` | *Java 8+* Invokes the [Consumer] `mutator` with the builder for the property. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored. |

The mutate method allows the buildable property to be set up or modified succinctly and readably in Java 8+:

```java
Project project = new Project.Builder()
    .mutateOwner(b -> b
        .setName("Phil")
        .setDepartment("HR"))
    .build();
```

[protos]: https://developers.google.com/protocol-buffers/
[Consumer]: https://docs.oracle.com/javase/8/docs/api/java/util/function/Consumer.html


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


### Jackson

To create types compatible with the [Jackson JSON serialization
library][Jackson], use the builder property of [@JsonDeserialize] to point Jackson
at your Builder class. For instance:

```java
// This type can be freely converted to and from JSON with Jackson
@JsonDeserialize(builder = Address.Builder.class)
interface Address {
    String getCity();
    String getState();

    class Builder extends Address_Builder {}
}
```

`@FreeBuilder` will generate appropriate [@JsonProperty] annotations on the
builder. (If you use Java 8 or Guava types, you may need to include the
relevant Jackson extension modules, [jackson-datatype-jdk8] and
[jackson-datatype-guava].)

[Jackson]: http://wiki.fasterxml.com/JacksonHome
[jackson-datatype-guava]: https://github.com/FasterXML/jackson-datatype-guava
[jackson-datatype-jdk8]: https://github.com/FasterXML/jackson-datatype-jdk8
[@JsonProperty]: http://fasterxml.github.io/jackson-annotations/javadoc/2.6/com/fasterxml/jackson/annotation/JsonProperty.html
[@JsonDeserialize]: http://fasterxml.github.io/jackson-databind/javadoc/2.6/com/fasterxml/jackson/databind/annotation/JsonDeserialize.html


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

Download [the latest FreeBuilder JAR] and add it to the classpath (or
processorpath, if you supply one) on the command line. If [Guava] is
available, FreeBuilder will use it to generate cleaner, more
interoperable implementation code (e.g returning [immutable collections]).

[the latest FreeBuilder JAR]: https://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=org.inferred&a=freebuilder&v=RELEASE
[immutable collections]: https://github.com/google/guava/wiki/ImmutableCollectionsExplained

### Maven

Add the `@FreeBuilder` artifact as an optional dependency to your Maven POM:

```xml
<dependencies>
  <dependency>
    <groupId>org.inferred</groupId>
    <artifactId>freebuilder</artifactId>
    <version>[current version]</version>
    <optional>true</optional>
  </dependency>
</dependencies>
```

If [Guava] is available, FreeBuilder will use it to generate cleaner, more
interoperable implementation code (e.g returning [immutable collections]).

### Gradle

Add the following lines to your dependencies:

```
compile 'org.inferred:freebuilder:<current version>'
```

If [Guava] is available, FreeBuilder will use it to generate cleaner, more
interoperable implementation code (e.g returning [immutable collections]).

If you use Eclipse or IDEA along with Gradle, consider using the
[org.inferred.processors plugin] to correctly configure code generation in
your IDE.

[org.inferred.processors plugin]: https://github.com/palantir/gradle-processors

### Eclipse

_Condensed from [Eclipse Indigo's documentation][]._

Download [the latest FreeBuilder JAR] and add it to your project. Select it,
right-click and choose **Build path > Add to Build path**.

In your projects properties dialog, go to **Java Compiler > Annotation
Processing** and ensure **Enable annotation processing** is checked.
Next, go to **Java Compiler > Annotation Processing > Factory Path**, select
**Add JARs**, and select the FreeBuilder JAR.

[Eclipse Indigo's documentation]: http://help.eclipse.org/indigo/index.jsp?topic=%2Forg.eclipse.jdt.doc.isv%2Fguide%2Fjdt_apt_getting_started.htm

### IntelliJ

_Condensed from the [IntelliJ 14.0.3 documentation][] and [Auto Issue #106][]._

Download [the latest FreeBuilder JAR], add it to your project, right-click it
and select **Use as Project Library**.

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

### Immutables vs `@FreeBuilder`

<em><strong>Where is Immutables better than `@FreeBuilder`?</strong></em>

[Immutables](https://immutables.github.io/) provides many of the same features as `@FreeBuilder`, plus a whole host more. Some are optional ways to potentially enhance performance, like [derived](https://immutables.github.io/immutable.html#derived-attributes) and [lazy](https://immutables.github.io/immutable.html#lazy-attributes) attributes, [singleton](https://immutables.github.io/immutable.html#singleton-instances) and [interned](https://immutables.github.io/immutable.html#instance-interning) instances, and [hash code precomputation](https://immutables.github.io/immutable.html#precomputed-hashcode). Some are API-changing: [strict builders](https://immutables.github.io/immutable.html#strict-builder) provide a compile-time guarantee that all fields are set (but limit the use of builders as a consequence), while [copy methods](https://immutables.github.io/immutable.html#copy-methods) provide a concise way to clone a value with a single field changed (but require you to reference the generated ImmutableFoo type, not Foo). It provides [advanced binary serialization options](https://immutables.github.io/immutable.html#serialization) and [GSON support](https://immutables.github.io/typeadapters.html). It does not require getters follow the JavaBean naming convention (i.e. starting with `get` or `is`), and lets you easily customize the conventional method prefixes. As Immutables is an active project, this list is likely incomplete.

<em><strong>Where is `@FreeBuilder` better than Immutables?</strong></em>

`@FreeBuilder` provides some features that are missing from, or not the default in, Immutables:

 * [Partials](#partials).
 * [Builder getter, mapper](#accessor-methods) and [mutation](#collections-and-maps) methods.
 * [Nested builders](#nested-buildable-types).
 * Jackson serialization is [clean and easily customised](#jackson).
 * [GWT support](#gwt)
 * Proxyable types<sup>1</sup>.
 * [Traditional OO customization](#defaults-and-constraints)<sup>1</sup>.

The first three points are increasingly useful as your interfaces grow in complexity and usage. Partials greatly reduce the fragility of your tests by only setting the values the code being tested actually uses. Your interfaces are liable accumulate more constraints, like new required fields, or cross-field constraints, and while these will be vitally important across the application as a whole, they create a big maintenance burden when they break unit tests for existing code that does not rely on those constraints. Builder getter, mapper and mutation methods and nested builders empower the modify-rebuild pattern, where code changes a small part of an object without affecting the remainder.

The last two points arise because Immutables is *type-generating*: you write your value type as a prototype, but you always use the generated class. This type is final, meaning you can't proxy it, which unfortunately breaks tools like [Mockito](http://mockito.org/) and its wonderful [smart nulls](http://site.mockito.org/mockito/docs/current/org/mockito/Answers.html#RETURNS_SMART_NULLS). The generated builder is hard to customize as you cannot override methods, explaining why Immutables has so many annotations: for instance, [the `@Value.Check` annotation](https://immutables.github.io/immutable.html#precondition-check-method) is unnecessary in `@FreeBuilder`, as you can simply override the setters or build method to perform field or cross-field validation.

1: But note that you *can* write a Builder subclass in your Foo type, enabling FreeBuilder-style override-based customization, though that is not the default approach recommended in the guide, and you will break all your callers if you change between the two.

Immutables is a very active project, frequently adding new features, and future releases may address some or all of these deficiencies.

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
[Range]: http://google.github.io/guava/releases/19.0/api/docs/com/google/common/collect/Range.html


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
