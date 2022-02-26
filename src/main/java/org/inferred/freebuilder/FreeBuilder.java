/*
 * Copyright 2014 Google Inc. All rights reserved.
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
package org.inferred.freebuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a type that has an auto-generated builder.
 *
 * <h3>Quick start</h3>
 *
 * <p>Create your value type (e.g. {@code Person}) as an interface or abstract class, containing an
 * abstract accessor method for each desired field. This accessor must be non-void, parameterless,
 * and start with 'get' or 'is'. Add the {@code @FreeBuilder} annotation to your class, and it will
 * automatically generate an implementing class and a package-visible builder API ({@code
 * Person_Builder}), which you must subclass. For instance:
 *
 * <blockquote>
 *
 * <pre>&#64;FreeBuilder
 * public interface Person {
 *   /** Returns the person's full (English) name. *&#47;
 *   String getName();
 *   /** Returns the person's age in years, rounded down. *&#47;
 *   int getAge();
 *   /** Builder of {&#64;link Person} instances. *&#47;
 *   class Builder extends Person_Builder { }
 * }</pre>
 *
 * </blockquote>
 *
 * <p>You can now use the {@code Builder} class:
 *
 * <blockquote>
 *
 * <pre>Person person = new Person.Builder()
 *     .setName("Phil")
 *     .setAge(31)
 *     .build();
 * System.out.println(person);  // Person{name=Phil, age=31}</pre>
 *
 * </blockquote>
 *
 * @see <a href="http://freebuilder.inferred.org/">Full documentation at
 *     freebuilder.inferred.org</a>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface FreeBuilder {}
