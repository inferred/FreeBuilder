FreeBuilder
===========

_Automatic generation of the Builder pattern for Java 1.8+_

[![Maven Central](https://img.shields.io/maven-central/v/org.inferred/freebuilder.svg)](https://search.maven.org/artifact/org.inferred/freebuilder)
[![CI (GitHub Workflow)](https://github.com/inferred/FreeBuilder/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/inferred/FreeBuilder/actions/workflows/ci.yml?query=branch%3Amain)

> The Builder pattern is a good choice when designing classes whose constructors
> or static factories would have more than a handful of parameters.
> &mdash; <em>Effective Java, Second Edition</em>, page 39

Project Archival
----------------

FreeBuilder was released back in 2015! It was a pet project of my own that unfortunately generated a weird amount of controversy at the company I worked at at the time, and thus ended up being open-sourced after the Immutables project had already been on the scene for most of a year. While I hoped the different design that allowed using partials for robust testing would still be a winner, a community hasn't picked up, and as I haven't used Java at my work for over 5 years now, and it's clearly not keeping up with modern Java, I think it's time to archive it.

If someone feels strongly about picking up where I've left off, please do reach out and let me know! I'd ask that you first fork the project and modernize the dev and CI setup, which really needs doing.

Otherwise, it's been a fun ride, and thanks to everyone who's been building free with me along the way 😁

&mdash; Alice

Table of Contents
-----------------

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->

- [Project Archival](#project-archival)
- [Background](#background)
- [How to use FreeBuilder](#how-to-use-freebuilder)
  - [Quick start](#quick-start)
  - [What you get](#what-you-get)
  - [Accessor methods](#accessor-methods)
  - [Defaults and constraints](#defaults-and-constraints)
  - [Optional values](#optional-values)
    - [Using `@Nullable`](#using-nullable)
    - [Converting from `@Nullable`](#converting-from-nullable)
  - [Collections and Maps](#collections-and-maps)
  - [Nested buildable types](#nested-buildable-types)
  - [Lists of buildable types](#lists-of-buildable-types)
    - [Disabling buildable lists](#disabling-buildable-lists)
  - [Custom toString method](#custom-tostring-method)
  - [Custom conventional method names](#custom-conventional-method-names)
  - [Custom functional interfaces](#custom-functional-interfaces)
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
- [Release notes](#release-notes)
  - [2.3—From method testability](#23from-method-testability)
  - [2.2—Primitive optional types](#22primitive-optional-types)
  - [2.1—Lists of buildable types](#21lists-of-buildable-types)
  - [Upgrading from v1](#upgrading-from-v1)
- [Troubleshooting](#troubleshooting)
  - [Troubleshooting javac](#troubleshooting-javac)
  - [Troubleshooting Eclipse](#troubleshooting-eclipse)
  - [Online resouces](#online-resouces)
- [Alternatives](#alternatives)
  - [Immutables vs FreeBuilder](#immutables-vs-freebuilder)
  - [AutoValue vs FreeBuilder](#autovalue-vs-freebuilder)
  - [Proto vs FreeBuilder](#proto-vs-freebuilder)
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

FreeBuilder produces all the boilerplate for you, as well as free extras like
JavaDoc, getter methods, mapper methods, [collections support](#collections-and-maps),
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


How to use FreeBuilder
----------------------


### Quick start

_See [Build tools and IDEs](#build-tools-and-ides) for how to add FreeBuilder 
to your project's build and/or IDE._

Create your value type (e.g. `Person`) as an interface or abstract class,
containing an abstract accessor method for each desired field. Add the
`@FreeBuilder` annotation to your class, and it will automatically generate an
implementing class and a package-visible builder API (`Person_Builder`), which
you must subclass. For instance:


```java
import org.inferred.freebuilder.FreeBuilder;

@FreeBuilder
public interface Person {
  /** Returns this person's full (English) name. */
  String name();
  /** Returns this person's age in years, rounded down. */
  int age();
  /** Returns a new {@link Builder} with the same property values as this person. */
  Builder toBuilder();
  /** Builder of {@link Person} instances. */
  class Builder extends Person_Builder { }
}
```

The `toBuilder()` method here is optional but highly recommended.
You may also wish to make the builder's constructor package-protected and manually
provide instead a static `builder()` method on the value type (though
<em>Effective Java</em> does not do this).


### What you get

If you write the Person interface shown above, you get:

  * A builder class with:
     * a no-args constructor
     * JavaDoc
     * getters (throwing `IllegalStateException` for unset fields)
     * setters
     * lambda-accepting mapper methods
     * `mergeFrom` and static `from` methods to copy data from existing values or builders
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
    .name("Phil")
    .age(31)
    .build();
System.out.println(person);  // Person{name=Phil, age=31}
```


### JavaBean convention

If you prefer your value types to follow the JavaBean naming convention, just prefix your accessor methods with 'get' (or, optionally, 'is' for boolean accessors). FreeBuilder will follow suit, and additionally add 'set' prefixes on setter methods, as well as dropping the prefix from its toString output.

```java
@FreeBuilder
public interface Person {
  /** Returns the person's full (English) name. */
  String getName();
  /** Returns the person's age in years, rounded down. */
  int getAge();
  /** Builder of {@link Person} instances. */
  class Builder extends Person_Builder { }
}

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
| A setter method, `foo` | Throws a NullPointerException if provided a null. (See the sections on [Optional](#optional-values) and [Nullable](#using-nullable) for ways to store properties that can be missing.) |
| A getter method, `foo` | Throws an IllegalStateException if the property value has not yet been set. |
| A mapper method, `mapFoo` | Takes a [UnaryOperator]. Replaces the current property value with the result of invoking the unary operator on it. Throws a NullPointerException if the operator, or the value it returns, is null. Throws an IllegalStateException if the property value has not yet been set. |

The mapper methods are very useful when modifying existing values, e.g.

```java
Person olderPerson = person.toBuilder().mapAge(age -> age + 1).build();
```

[UnaryOperator]: https://docs.oracle.com/javase/8/docs/api/java/util/function/UnaryOperator.html


### Defaults and constraints

We use method overrides to add customization like default values and constraint
checks. For instance:


```java
@FreeBuilder
public interface Person {
  /** Returns the person's full (English) name. */
  String name();
  /** Returns the person's age in years, rounded down. */
  int age();
  /** Returns a human-readable description of the person. */
  String description();
  /** Builder class for {@link Person}. */
  class Builder extends Person_Builder {
    public Builder() {
      // Set defaults in the builder constructor.
      description("Indescribable");
    }
    @Override Builder age(int age) {
      // Check single-field (argument) constraints in the setter method.
      checkArgument(age >= 0);
      return super.age(age);
    }
    @Override public Person build() {
      // Check cross-field (state) constraints in the build method.
      Person person = super.build();
      checkState(!person.description().contains(person.name()));
      return person;
    }
  }
}
```


### Optional values

If a property is optional&mdash;that is, has no reasonable default&mdash;then
use [the Java Optional type][] (or [the Guava Optional type][] for
backwards-compatibility).

[the Java Optional type]: https://docs.oracle.com/javase/8/docs/api/java/util/Optional.html
[the Guava Optional type]: http://google.github.io/guava/releases/19.0/api/docs/com/google/common/base/Optional.html


```java
  /** Returns an optional human-readable description of the person. */
  Optional<String> description();
```

This property will now default to Optional.empty(), and the Builder
will gain additional convenience setter methods:

| Method | Description |
|:------:| ----------- |
| `description(String value)` | Sets the property to `Optional.of(value)`. Throws a NullPointerException if value is null; this avoids users accidentally clearing an optional value in a way that peer review is unlikely to catch. |
| `clearDescription()` | Sets the property to `Optional.empty()`. |
| `description(Optional<String> value)` | Sets the property to `value`. |
| `nullableDescription(String value)` | Sets the property to `Optional.ofNullable(value)`. |
| `mapDescription(UnaryOperator<String> mapper` | If the property value is not empty, this replaces the value with the result of invoking `mapper` with the existing value, or clears it if `mapper` returns null. Throws a NullPointerException if `mapper` is null. |

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
  @Nullable String title();
```

This property will now default to null, and the Builder's setter methods will
change their null-handling behaviour:

| Method | Description |
|:------:| ----------- |
| `title(@Nullable String title)` | Sets the property to `title`. |
| `title()` | Returns the current value of the property. May be null. |
| `mapTitle(UnaryOperator<String> mapper)` | Takes a [UnaryOperator]. Replaces the current property value, if it is not null, with the result of invoking `mapper` on it. Throws a NullPointerException if `mapper` is null. `mapper` may return a null. |

[Guava]: https://github.com/google/guava
[Hoare]: http://www.infoq.com/presentations/Null-References-The-Billion-Dollar-Mistake-Tony-Hoare
[Using and Avoiding Null]: https://github.com/google/guava/wiki/UsingAndAvoidingNullExplained

#### Converting from `@Nullable`

This is the O(1), non-tedious, non&ndash;error-prone way we recomment converting
`@Nullable` to Optional:

 * Load all your code in Eclipse, or another IDE with support for renaming and
    inlining.
 * _[IDE REFACTOR]_ Rename all your `@Nullable` getters to `nullableX()` (or `getNullableX()` if you use JavaBean naming conventions).
 * Add an Optional-returning `x()` (or `getX()`)
 * Implement your nullableX methods as:  `return x().orElse(null)`
   <br>(Guava: `return x().orNull()`)
 * _[IDE REFACTOR]_ Inline your `nullableX()` methods

At this point, you have effectively performed an automatic translation of a
`@Nullable` method to an Optional-returning one. Of course, your code is not
optimal yet (e.g.  `if (foo.x().orElse(null) != null)`  instead of  `if
(foo.x().isPresent())` ). Search-and-replace should get most of these issues.

 * _[IDE REFACTOR]_ Rename all your `@Nullable` setters to `nullableX` (or `setNullableX`).

Your API is now FreeBuilder-compatible :)


### Collections and Maps

FreeBuilder has special support for collection and map properties, removing
the `foo` accessor method and generating new ones appropriate to the type.
Collection and map properties default to an empty collection/map and cannot hold
nulls.

```java
  /** Returns a list of descendents for this person. **/
  List<String> descendants();
```

A <code>[List][]</code>, <code>[Set][]</code>, <code>[SortedSet][]</code> or <code>[Multiset][]</code>
property called 'descendants' would generate:

| Method | Description |
|:------:| ----------- |
| `addDescendants(String element)` | Appends `element` to the collection of descendants. If descendants is a set and the element is already present, it is ignored. Throws a NullPointerException if element is null. |
| `addDescendants(String... elements)` | Appends all `elements` to the collection of descendants. If descendants is a set, any elements already present are ignored. Throws a NullPointerException if elements, or any of the values it holds, is null. |
| `addAllDescendants(​Iterable<String> elements)`<br>`addAllDescendants(​Stream<String> elements)`<br>`addAllDescendants(​Spliterator<String> elements)` | Appends all `elements` to the collection of descendants. If descendants is a set, any elements already present are ignored. Throws a NullPointerException if elements, or any of the values it holds, is null. |
| `mutateDescendants(​Consumer<‌.‌.‌.‌<String>> mutator)` | Invokes the [Consumer] `mutator` with the collection of descendants. (The mutator takes a list, set or map as appropriate.) Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream()] expecting the returned collection to replace the existing collection. |
| `clearDescendants()` | Removes all elements from the collection of descendants, leaving it empty. |
| `descendants()` | Returns an unmodifiable view of the collection of descendants. Changes to the collection held by the builder will be reflected in the view. |
| `setComparatorForDescendants(​Comparator<? super String> comparator)` | *SortedSet only* A protected method that sets the [comparator] to keep the set elements ordered by. Must be called before any other accessor method for this property. Defaults to the [natural ordering] of the set's elements. |

```java
  /** Returns a map of favourite albums by year. **/
  Map<Integer, String> albums();
```

A <code>[Map][]</code> property called 'albums' would generate:

| Method | Description |
|:------:| ----------- |
| `putAlbums(int key, String value)` | Associates `key` with `value` in albums.  Throws a NullPointerException if either parameter is null. Replaces any existing entry. |
| `putAllAlbums(Map<? extends Integer, ? extends String> map)` | Associates all of `map`'s keys and values in albums. Throws a NullPointerException if the map is null or contains a null key or value. Replaces any existing mapping for all keys in `map`. |
| `removeAlbums(int key)` | Removes the mapping for `key` from albums. Throws a NullPointerException if the parameter is null. Does nothing if the key is not present. |
| `mutateAlbums(​Consumer<Map<Integer, String>> mutator)` | Invokes the [Consumer] `mutator` with the map of albums. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream()] expecting the returned map to replace the existing map. |
| `clearAlbums()` | Removes all mappings from albums, leaving it empty. |
| `albums()` | Returns an unmodifiable view of the map of albums. Changes to the map held by the builder will be reflected in this view. |

```java
  /** Returns a bimap of favourite albums by year. **/
  BiMap<Integer, String> albums();
```

A <code>[BiMap][]</code> property called 'albums' would generate:

| Method | Description |
|:------:| ----------- |
| `putAlbums(int key, String value)` | Associates `key` with `value` in albums.  Throws a NullPointerException if either parameter is null, or an IllegalArgumentException if `value` is already bound to a different key. Replaces any existing entry for `key`. |
| `forcePutAlbums(int key, String value)` | Associates `key` with `value` in albums.  Throws a NullPointerException if either parameter is null. Replaces any existing entry for both `key` _and_ `value`. _Override this method to implement [constraint checks](#defaults-and-constraints)._ |
| `putAllAlbums(Map<? extends Integer, ? extends String> map)` | Associates all of `map`'s keys and values in albums. Throws a NullPointerException if the map is null or contains a null key or value. Replaces any existing mapping for all keys in `map`. Throws an IllegalArgumentException if an attempt to put any entry fails. |
| `removeKeyFromAlbums(int key)` | Removes the mapping for `key` from albums. Throws a NullPointerException if the parameter is null. Does nothing if the key is not present. |
| `removeValueFromAlbums(String value)` | Removes the mapping for `value` from albums. Throws a NullPointerException if the parameter is null. Does nothing if the value is not present. |
| `mutateAlbums(​Consumer<BiMap<Integer, String>> mutator)` | Invokes the [Consumer] `mutator` with the bimap of albums. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream()] expecting the returned map to replace the existing map. |
| `clearAlbums()` | Removes all mappings from albums, leaving it empty. |
| `albums()` | Returns an unmodifiable view of the bimap of albums. Changes to the bimap held by the builder will be reflected in this view. |

```java
  /** Returns a multimap of all awards by year. **/
  SetMultimap<Integer, String> awards();
```

A <code>[Multimap][]</code> property called 'awards' would generate:

| Method | Description |
|:------:| ----------- |
| `putAwards(int key, String value)` | Associates `key` with `value` in awards. Throws a NullPointerException if either parameter is null. |
| `putAllAwards(int key, Iterable<? extends String> values)` | Associates `key` with every element of `values` in awards. Throws a NullPointerException if either parameter, or any value, is null. |
| `putAllAwards(Map<? extends Integer, ? extends String> map)` | Associates all of `map`'s keys and values in awards. Throws a NullPointerException if the map is null or contains a null key or value. If awards is a map, an IllegalArgumentException will be thrown if any key is already present. |
| `removeAwards(int key, String value)` | Removes the single pair `key`-`value` from awards. If multiple pairs match, which is removed is unspecified. Throws a NullPointerException if either parameter is null. |
| `removeAllAwards(int key)` | Removes all values associated with `key` from awards. Throws a NullPointerException if the key is null. |
| `mutateAwards(​Consumer<Map<Integer, String>> mutator)` | Invokes the [Consumer] `mutator` with the multimap of awards. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream()] expecting the returned multimap to replace the existing multimap. |
| `clearAwards()` | Removes all mappings from awards, leaving it empty. |
| `awards()` | Returns an unmodifiable view of the multimap of awards. Changes to the multimap held by the builder will be reflected in this view. |

In all cases, the value type will return immutable objects from its getter.

The mutator methods are useful for invoking methods not directly exposed on the builder, like [subList], or methods that take a mutable collection, like [sort]:

```java
personBuilder
    // Delete the fourth and fifth descendants in the list
    .mutateDescendants(d -> d.subList(3,5).clear())
    // Sort the remaining descendants
    .mutateDescendants(Collections::sort);
```

[Comparator]: https://docs.oracle.com/javase/8/docs/api/java/util/Comparator.html
[List]: http://docs.oracle.com/javase/tutorial/collections/interfaces/list.html
[Set]: http://docs.oracle.com/javase/tutorial/collections/interfaces/set.html
[SortedSet]: http://docs.oracle.com/javase/8/docs/api/java/util/SortedSet.html
[Spliterator]: https://docs.oracle.com/javase/8/docs/api/java/util/Spliterator.html
[Stream]: https://docs.oracle.com/javase/8/docs/api/java/util/stream/Stream.html
[Multiset]: https://github.com/google/guava/wiki/NewCollectionTypesExplained#multiset
[Map]: http://docs.oracle.com/javase/tutorial/collections/interfaces/map.html
[BiMap]: https://github.com/google/guava/wiki/NewCollectionTypesExplained#bimap
[Multimap]: https://github.com/google/guava/wiki/NewCollectionTypesExplained#multimap
[natural ordering]: https://docs.oracle.com/javase/8/docs/api/java/lang/Comparable.html
[sort]: http://docs.oracle.com/javase/8/docs/api/java/util/Collections.html#sort-java.util.List-
[stream()]: https://docs.oracle.com/javase/8/docs/api/java/util/Collection.html#stream--
[subList]: http://docs.oracle.com/javase/8/docs/api/java/util/List.html#subList-int-int-


### Nested buildable types

```java
  /** Returns the person responsible for this project. */
  Person owner();
```

FreeBuilder has special support for buildable types like [protos][] and other
FreeBuilder types. A buildable property called 'owner' would generate:

| Method | Description |
|:------:| ----------- |
| `owner(Person owner)` | Sets the owner. Throws a NullPointerException if provided a null. |
| `owner(Person.Builder builder)` | Calls `build()` on `builder` and sets the owner to the result. Throws a NullPointerException if builder or the result of calling `build()` is null. |
| `ownerBuilder()` | Returns a builder for the owner property. Unlike other getter methods in FreeBuilder-generated API, this object is mutable, and modifying it **will modify the underlying property**. |
| `mutateOwner(Consumer<Person.Builder> mutator)` | Invokes the [Consumer] `mutator` with the builder for the property. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored. |

The mutate method allows the buildable property to be set up or modified succinctly and readably:

```java
Project project = new Project.Builder()
    .mutateOwner($ -> $
        .name("Phil")
        .department("HR"))
    .build();
```

[protos]: https://developers.google.com/protocol-buffers/
[Consumer]: https://docs.oracle.com/javase/8/docs/api/java/util/function/Consumer.html


### Lists of buildable types

FreeBuilder has special support for lists of buildable types, too. It maintains
a list of builders, to allow elements of the list to be built incrementally. (For
better performance, if given a built instance for the list, it will lazily convert
it to a Builder on demand. This may cause problems if your buildable types
continue to be mutable after construction; to avoid unpredictable aliasing,
we recommend disabling buildable list support, as described below.)

A list of buildable properties called 'owners' would generate:

| Method | Description |
|:------:| ----------- |
| `addOwners(Person element)` | Appends `element` to the collection of owners. Throws a NullPointerException if element is null. The element may be lazily converted to/from a Builder. |
| `addOwners(Person.Builder builder)` | Appends the value built by `builder` to the collection of owners. Throws a NullPointerException if builder is null. Only a copy of the builder will be stored; changes made to it after this method returns will have no effect on the list. The copied builder's `build()` method will not be called immediately, so if this builder's state is not legal, you will not get failures until you build the final immutable object. |
| `addOwners(Person... elements)` | Appends all `elements` to the collection of owners.  Throws a NullPointerException if elements, or any of the values it holds, is null. Each element may be lazily converted to/from a Builder. |
| `addOwners(Person.Builder... builders)` | Appends the values built by `builders` to the collection of owners.  Throws a NullPointerException if builders, or any of the values it holds, is null. Only copies of the builders will be stored, and `build()` methods will not be called immediately. |
| `addAllOwners(​Iterable<Person> elements)`<br>`addAllOwners(​Stream<Person> elements)`<br>`addAllOwners(​Spliterator<Person> elements)` | Appends all `elements` to the collection of owners.  Throws a NullPointerException if elements, or any of the values it holds, is null. Each element may be lazily converted to/from a Builder. |
| `addAllBuildersOfOwners(​Iterable<Person.Builder> builders)`<br>`addAllBuildersOfOwners(​Stream<Person.Builder> builders)`<br>`addAllBuildersOfOwners(​Spliterator<Person.Builder> builders)` | Appends the values built by `builders` to the collection of owners.  Throws a NullPointerException if builders, or any of the values it holds, is null. Only copies of the builders will be stored, and `build()` methods will not be called immediately. |
| `mutateOwners(​Consumer<? super List<Person.Builder>> mutator)` | Invokes the [Consumer] `mutator` with the list of owner builders. Throws a NullPointerException if `mutator` is null. As `mutator` is a void consumer, any value returned from a lambda will be ignored, so be careful not to call pure functions like [stream()] expecting the returned collection to replace the existing collection. |
| `clearOwners()` | Removes all elements from the collection of owners, leaving it empty. |
| `buildersOfOwners()` | Returns an unmodifiable view of the list of owner builders. Changes to the list held by the builder will be reflected in the view, and changes made to any of the returned builders will be reflected in the final list of owners. |

Note that `mutateOwners` and `buildersOfOwners` are the only methods which can cause lazy convertion of an inserted value to a Builder, and then only upon accessing the element, so avoid these actions if possible to avoid unexpected performance hits.

#### Disabling buildable lists

You can force FreeBuilder to use vanilla list support, rather than converting elements
to/from Builders under the hood, by declaring a vanilla getter in the Builder. For
instance, to force `owners` to drop Builder support:

```java
  class Builder extends Foo_Builder {
    @Override
    public List<Person> owners() {
      // Disable FreeBuilder's lazy conversion to/from Person.Builder by declaring
      // a non-Builder-compatible getter.
      return super.owners();
    }
  }
```

FreeBuilder will now generate the methods described in [Collections and Maps](#collections-and-maps).


### Custom toString method

FreeBuilder will only generate toString, hashCode and equals methods if they are left abstract, so to customise them, just implement them.
(Due to language constraints, you will need to first convert your type to an abstract class if it was previously an interface.)

```java
abstract class Person {
  public abstract String name();
  public abstract int age();

  @Override public String toString() {
    return name() + " (" + age() + " years old)";
  }

  public static class Builder extends Person_Builder {}
}
```

Note that it is a compile error to leave hashCode abstract if equals is implemented, and vice versa, as FreeBuilder has no reasonable way to ensure the consistency of any implementation it might generate.

If you have a small set of properties you wish to exclude from equals or toString without losing the generated code entirely, you can annotate them `@IgnoredByEquals` and/or `@NotInToString`.

**Warning:**
It is rarely a good idea to redefine equality on a value type, as it makes testing very hard.
For instance, `assertEquals` in JUnit relies on equality; it will not know to check individual fields, and as a result, tests may be failing to catch bugs that, on the face of it, they looks like they should be.
If you are only testing a subset of your fields for equality, consider separating your class in two, as you may have accidentally combined the key and the value of a map into a single object, and you may find your code becomes healthier after the separation.
Alternatively, creating a custom [Comparator] will make it explicit that you are not using the natural definition of equality.

### Custom conventional method names

If for any reason your types cannot use the conventional method names (`build`, `buildPartial`, `clear` and `mergeFrom`), you can force FreeBuilder to generate package protected implementations, and even select alternative fallback names if necessary, by declaring an alternative visibility and/or incompatible signature. If the default name is not available, FreeBuilder will prepend an underscore and append "Impl" (and, if necessary, a number), e.g. `build` becomes `_buildImpl`.

```java
public interface MyType {
  class Builder extends MyType_Builder {
    public OtherDataType build() {
      // This signature is not compatible with the default build method.
      // FreeBuilder will instead declare a package-scoped _buildImpl.
      ...
    }
    public DataType buildMyType() {
      return _buildImpl();
    }
  }
}
```

Note that this will, unfortunately, disable FreeBuilder's [enhanced support for nested builders](#nested-buildable-types) for this type, as it needs to be able to call these methods.

### Custom functional interfaces

FreeBuilder's generated map and mutate methods take [UnaryOperator] or [Consumer] functional interfaces. If you need to use a different functional interface, you can override the generated methods in your Builder and change the parameter type. FreeBuilder will spot the incompatible override and change the code it generates to match:

```java
public interface MyType {
  String property();

  class Builder extends MyType_Builder {
    @Override public Builder mapProperty(
        com.google.common.base.Function<Integer, Integer> mapper) {
      return super.mapProperty(mapper);
    }
  }
}
```

### Builder construction

<em>Effective Java</em> recommends passing required parameters in to the Builder
constructor. While we follow most of the recommendations therein, we explicitly
do not follow this one: while you gain compile-time verification that all
parameters are set, you lose flexibility in client code, as well as opening
yourself back up to the exact same subtle usage bugs as traditional constructors
and factory methods. For the default FreeBuilder case, where all parameters
are required, this does not scale.

If you want to follow <em>Effective Java</em> more faithfully in your own types,
however, just create the appropriate constructor in your builder subclass:


```java
    public Builder(String name, int age) {
      // Set all initial values in the builder constructor
      name(name);
      age(age);
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
    .name("Phil")
    .buildPartial();  // build() would throw an IllegalStateException here
System.out.println(person);  // prints: partial Person{name=Phil}
person.age();  // throws UnsupportedOperationException
```

As partials violate the (legitimate) expectations of your program, they must
<strong>not</strong> be created in production code. (They may also affect the
performance of your program, as the JVM cannot make as many optimizations.)
However, when testing a component which does not rely on the full state
restrictions of the value type, partials can reduce the fragility of your test
suite, allowing you to add new required fields or other constraints to an
existing value type without breaking swathes of test code.

To allow robust tests of modify-rebuild code, Builders created from partials
(either via the static `Builder.from` method or the optional `toBuilder()`
method) will override `build()` to instead call `buildPartial()`.

```java
Person anotherPerson = person.toBuilder().name("Bob").build();
System.out.println(anotherPerson);  // prints: partial Person{name=Bob}
```

This "infectious" behavior of partials is another reason to confine them to
test code.

(Note the `mergeFrom` method does not behave this way; instead, it will throw an
UnsupportedOperationException if given a partial.)


### Jackson

To create types compatible with the [Jackson JSON serialization
library][Jackson], use the builder property of [@JsonDeserialize] to point Jackson
at your Builder class. For instance:

```java
// This type can be freely converted to and from JSON with Jackson
@JsonDeserialize(builder = Address.Builder.class)
interface Address {
    String city();
    String state();

    class Builder extends Address_Builder {}
}
```

FreeBuilder will generate appropriate [@JsonProperty] annotations on the
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

Add the FreeBuilder artifact as an optional dependency to your Maven POM:

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

#### 4.6+

Add the following lines to your project's build.gradle file:

```
dependencies {
  annotationProcessor 'org.inferred:freebuilder:<current version>'
  compileOnly 'org.inferred:freebuilder:<current version>'
}
```

If [Guava] is available, FreeBuilder will use it to generate cleaner, more
interoperable implementation code (e.g returning [immutable collections]).
You may also wish to use the [org.inferred.processors plugin] to correctly configure code
generation in your IDE.

#### Pre-4.6

Add the following lines to your project's build.gradle file:

```
plugins {
  id 'org.inferred.processors' version '1.2.10'
}

dependencies {
  processor 'org.inferred:freebuilder:<current version>'
}
```

This uses the [org.inferred.processors plugin] to correctly configure code generation in
your IDE. Alternatively, you can drop the plugin and replace `processor` with
[`compileOnly`], or `compile` if you are on Gradle 2.11 or earlier—you will lose IDE
integration—or use your own favourite Gradle annotation processor plugin.

If [Guava] is available, FreeBuilder will use it to generate cleaner, more
interoperable implementation code (e.g returning [immutable collections]).

[org.inferred.processors plugin]: https://github.com/palantir/gradle-processors
[`compileOnly`]: https://blog.gradle.org/introducing-compile-only-dependencies

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


If you are using Maven to build your project within IntelliJ, you can alternatively
configure the POM in a way that enables IntelliJ to automatically detect annotation
processors when the POM is reloaded:

```
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessors>
            <annotationProcessor>org.inferred.freebuilder.processor.Processor</annotationProcessor>
        </annotationProcessors>
        <annotationProcessorPaths>
            <path>
                <groupId>org.inferred</groupId>
                <artifactId>freebuilder</artifactId>
                <version>2.6.2</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Click **Reload All Maven Projects** in the Maven tool window to force a reload if you have not configured IntelliJ
for automatic reloading. The Annotation processor should appear in **Build, Execution, Deployment > Compiler > Annotation
Processors** automatically, with all needed settings applied.

In case you need to use more than one annotation processor, you'll find an example at https://github.com/inferred/FreeBuilder/issues/435 .

[IntelliJ 14.0.3 documentation]: http://www.jetbrains.com/idea/webhelp/configuring-annotation-processing.html
[Auto Issue #106]: https://github.com/google/auto/issues/106


Release notes
-------------

### 2.3—From method testability

FreeBuilder 2.3 now allows partials to be passed to the static `Builder.from`
method. Previously this would have thrown an UnsupportedOperationException
if any field was unset; now, as with the optional `toBuilder` method, a Builder
subclass will be returned that redirects `build` to `buildPartial`. This allows
unit tests to be written that won't break if new constraints or required fields
are later added to the datatype. You can restore the old behaviour by overriding
the `from` method to delegate to `mergeFrom`.

Note that use of partials outside of tests is considered undefined behaviour
by FreeBuilder, as documented here and on the `buildPartial` method. Incomplete
values should always be represented by Builder instances, not partials.

### 2.2—Primitive optional types

FreeBuilder 2.2 extends its [optional value API customization](#optional-values)
to [OptionalInt], [OptionalLong] and [OptionalDouble]. This is, for mutate
methods, a non-binary-backwards-compatible change. If you have existing
properties that you do not want this to affect, you can force FreeBuilder to
adhere to your existing API with [custom functional interfaces](#custom-functional-interfaces).
To do this, use your IDE to override all mapper methods taking an `OptionalInt`,
`OptionalLong` or `OptionalDouble` (the implementations can just delegate to
super). One of these will have been generated for each primitive optional
property. Once all such methods are overridden, upgrading to 2.2 should now leave your
APIs unaltered.

This change also alters the default behavior of the Optional-accepting setter method
to delegate to a new primitive-accepting setter method, to allow constraints to be
added through overriding. If you have previously added such a primitive-accepting
setter method that delegates to the Optional-accepting setter method, this will of
course now result in a stack overflow at runtime. FreeBuilder will attempt to flag
this as a compiler error, but please double-check your builders when upgrading.

[OptionalInt]: https://docs.oracle.com/javase/8/docs/api/java/util/OptionalInt.html
[OptionalLong]: https://docs.oracle.com/javase/8/docs/api/java/util/OptionalLong.html
[OptionalDouble]: https://docs.oracle.com/javase/8/docs/api/java/util/OptionalDouble.html

### 2.1—Lists of buildable types

FreeBuilder 2.1 adds more extensive API customization for [lists of buildable types](#lists-of-buildable-types), storing Builder instances internally until build is called, cascading buildPartial automatically, and adding overloads accepting Builder instances.

This is a behavioural and, for the get and mutate methods, a non-binary-backwards-compatible change. If you have existing properties that you do not want this to affect, see [disabling buildable lists](#disabling-buildable-lists) for instructions on restoring the 2.0 behaviour on a case-by-case basis.

### Upgrading from v1

There are three API-breaking changes between v1 and v2 of FreeBuilder:

 * Mapper methods use primitive, not boxed, functional interfaces for `int`,
   `long` and `double` properties.

   **This will likely break binary backwards compatibility** for any library
   using FreeBuilder to generate its builders. We apologise profusely for the
   hassle this causes. If you simply cannot break your clients, but want to
   upgrade to v2, you can force FreeBuilder to adhere to your existing API with
   [custom functional interfaces](#custom-functional-interfaces). To do this,
   use your IDE to override all mapper methods taking a `UnaryOperator<Integer>`,
   `UnaryOperator<Long>` or `UnaryOperator<Double>` (the implementations can
   just delegate to super). One of these will have been generated for each
   `int`, `long` and `double` property. Once all such methods are overridden,
   upgrading to v2 should now leave your APIs unaltered.

 * No more support for Java 6/7

   If you are still on Java 6/7, please continue to use the v1 releases of FreeBuilder.

 * No longer ships with `javax.annotation.Nullable`

   FreeBuilder treats _any_ annotation named Nullable the same way, so you can either
   explicitly compile against the old annotation by including
   [the JSR-305 jar](https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305),
   or use a more modern alternative, like
   [org.jetbrains.annotations](https://mvnrepository.com/artifact/org.jetbrains/annotations).
   (Or use optionals! See [Converting from `@Nullable`](#converting-from-nullable) for
   advice.)

Troubleshooting
---------------


### Troubleshooting javac

If you make a mistake in your code (e.g. giving your value type a private
constructor), FreeBuilder is designed to output a Builder superclass anyway,
with as much of the interface intact as possible, so the only errors you see
are the ones output by the annotation processor.

Unfortunately, `javac` has a broken design: if _any_ annotation processor
outputs _any error whatsoever_, *all* generated code is discarded, and all
references to that generated code are flagged as broken references. Since
your Builder API is generated, that means every usage of a FreeBuilder
builder becomes an error. This deluge of false errors swamps the actual
error you need to find and fix. (See also [the related issue][issue 3].)

If you find yourself in this situation, search the output of `javac` for the
string "@FreeBuilder type"; nearly all errors include this in their text.

[issue 3]: https://github.com/inferred/FreeBuilder/issues/3

### Troubleshooting Eclipse

Eclipse manages, somehow, to be worse than `javac` here. It will never output
annotation processor errors unless there is another error of some kind; and,
even then, only after an incremental, not clean, compile. In practice, most
mistakes are made while editing the FreeBuilder type, which means the
incremental compiler will flag the errors. If you find a generated superclass
appears to be missing after a clean compile, however, try touching the
relevant file to trigger the incremental compiler. (Or run javac.)

### Online resouces

  * If you find yourself stuck, needing help, wondering whether a given
    behaviour is a feature or a bug, or just wanting to discuss FreeBuilder,
    please join and/or post to [the FreeBuilder mailing list][mailing list].
  * To see a list of open issues, or add a new one, see [the FreeBuilder
    issue tracker][issue tracker].
  * To submit a bug fix, or land a sweet new feature, [read up on how to
    contribute][contributing].

[mailing list]: https://groups.google.com/forum/#!forum/freebuilder
[issue tracker]: https://github.com/inferred/freebuilder/issues
[contributing]: https://github.com/inferred/FreeBuilder/blob/main/CONTRIBUTING.md


Alternatives
------------

### Immutables vs FreeBuilder

<em><strong>Where is Immutables better than FreeBuilder?</strong></em>

[Immutables](https://immutables.github.io/) provides many of the same features as FreeBuilder, plus a whole host more. Some are optional ways to potentially enhance performance, like [derived](https://immutables.github.io/immutable.html#derived-attributes) and [lazy](https://immutables.github.io/immutable.html#lazy-attributes) attributes, [singleton](https://immutables.github.io/immutable.html#singleton-instances) and [interned](https://immutables.github.io/immutable.html#instance-interning) instances, and [hash code precomputation](https://immutables.github.io/immutable.html#precomputed-hashcode). Some are API-changing: [strict builders](https://immutables.github.io/immutable.html#strict-builder) provide a compile-time guarantee that all fields are set (but limit the use of builders as a consequence), while [copy methods](https://immutables.github.io/immutable.html#copy-methods) provide a concise way to clone a value with a single field changed (but require you to reference the generated ImmutableFoo type, not Foo). It provides [advanced binary serialization options](https://immutables.github.io/immutable.html#serialization) and [GSON support](https://immutables.github.io/typeadapters.html). It lets you easily customize the conventional method prefixes. As Immutables is an active project, this list is likely incomplete.

<em><strong>Where is FreeBuilder better than Immutables?</strong></em>

FreeBuilder provides some features that are missing from, or not the default in, Immutables:

 * [Partials](#partials).
 * [Builder getter, mapper](#accessor-methods) and [mutation](#collections-and-maps) methods.
 * [Nested builders](#nested-buildable-types).
 * Jackson serialization is [clean and easily customised](#jackson).
 * [GWT support](#gwt)
 * Proxyable types<sup>1</sup>.
 * [Traditional OO customization](#defaults-and-constraints)<sup>1</sup>.

The first three points are increasingly useful as your interfaces grow in complexity and usage. Partials greatly reduce the fragility of your tests by only setting the values the code being tested actually uses. Your interfaces are liable accumulate more constraints, like new required fields, or cross-field constraints, and while these will be vitally important across the application as a whole, they create a big maintenance burden when they break unit tests for existing code that does not rely on those constraints. Builder getter, mapper and mutation methods and nested builders empower the modify-rebuild pattern, where code changes a small part of an object without affecting the remainder.

The last two points arise because Immutables is *type-generating*: you write your value type as a prototype, but you always use the generated class. This type is final, meaning you can't proxy it, which unfortunately breaks tools like [Mockito](http://mockito.org/) and its wonderful [smart nulls](http://site.mockito.org/mockito/docs/current/org/mockito/Answers.html#RETURNS_SMART_NULLS). The generated builder is hard to customize as you cannot override methods, explaining why Immutables has so many annotations: for instance, [the `@Value.Check` annotation](https://immutables.github.io/immutable.html#precondition-check-method) is unnecessary in FreeBuilder, as you can simply override the setters or build method to perform field or cross-field validation.

1: But note that you *can* write a Builder subclass in your Foo type, enabling FreeBuilder-style override-based customization, though that is not the default approach recommended in the guide, and you will break all your callers if you change between the two.

Immutables is a very active project, frequently adding new features, and future releases may address some or all of these deficiencies.

### AutoValue vs FreeBuilder

<em><strong>Why is FreeBuilder better than [AutoValue][]?</strong></em>

It’s not! AutoValue provides an implementing class with a package-visible
constructor, so you can easily implement the Factory pattern. If you’re writing
an immutable type that needs a small number of values to create (Effective Java
suggests at most three), and is not likely to require more in future, use the
Factory pattern.

How about if you want a builder? [AutoValue.Builder][] lets you explicitly
specify a minimal Builder interface that will then be implemented by generated
code, while FreeBuilder provides a generated builder API. AutoValue.Builder
is better if you must have a minimal API&mdash;for instance, in an Android
project, where every method is expensive&mdash;or strongly prefer a
visible-in-source API at the expense of many useful methods. Otherwise, consider
using FreeBuilder to implement the Builder pattern.

<em><strong>I used [AutoValue][], but now have more than three properties! How
do I migrate to FreeBuilder?</strong></em>

  1. Change your annotation to `@FreeBuilder`.
  2. Rewrite your factory method(s) to use the builder API.
  3. Inline your factory method(s) with a refactoring tool (e.g. Eclipse).

You can always skip step 3 and have both factory and builder methods, if that
seems cleaner!


<em><strong>Can I use both [AutoValue][] and FreeBuilder?</strong></em>

Not really. You can certainly use both annotations, but you will end up with
two different implementing classes that never compare equal, even if they have
the same values.

[AutoValue]: https://github.com/google/auto/tree/master/value
[AutoValue.Builder]: https://github.com/google/auto/tree/master/value#builders


### Proto vs FreeBuilder

<em><strong>[Protocol buffers][] have provided builders for ages. Why should I
use FreeBuilder?</strong></em>

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
  * Free as in liberty: you can always drop FreeBuilder and walk away with
    the code it generated for you.

License
-------

    Copyright 2014-7 Google Inc., 2018 Inferred.Org. All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
