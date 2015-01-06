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
 * Annotates a type that has an auto-generated builder. See also
 * <a href="http://freebuilder.inferred.org/">freebuilder.inferred.org</a>.
 *
 * <h3>Basics</h3>
 *
 * <p>Given an abstract class X and a set of abstract getter methods, the {@literal @}FreeBuilder
 * annotation processor will generate a builder class X_Builder, for use as a supertype of a
 * boilerplate X.Builder class, e.g.:
 *
 * <pre>
 * {@literal @}FreeBuilder
 * public abstract class DataType {
 *   public abstract int getPropertyA();
 *   public abstract boolean isPropertyB();
 *
 *   public static class Builder extends DataType_Builder {}
 *   public static Builder builder() {
 *     return new Builder();
 *   }
 * }</pre>
 *
 * An implementation of the abstract class X will be constructed and returned by the builder's
 * <code>build()</code> method.
 *
 * <pre>
 * DataType value = DataType.builder()
 *     .setPropertyA(11)
 *     .setPropertyB(true)
 *     .build();</pre>
 *
 * <h3>Defaults and Validation</h3>
 *
 * <p>To set defaults and perform validation, override the relevant methods on the builder, e.g.:
 *
 * <pre>
 *   public static class Builder extends DataType_Builder {
 *     // Set defaults in the constructor.
 *     private Builder() {
 *       setPropertyB(false);
 *     }
 *
 *     // Perform single-property (argument) validation in the setter methods.
 *     {@literal @}Override public Builder setPropertyA(int propertyA) {
 *       Preconditions.checkArgument(propertyA >= 0);
 *       return super.setPropertyA(propertyA);
 *     }
 *
 *     // Perform cross-property (state) validation in the build method.
 *     {@literal @}Override public DataType build() {
 *       DataType result = super.build(); // Ensures all properties are set
 *       Preconditions.checkState(result.getPropertyA() &lt;= 10 || result.isPropertyB(),
 *           "Property A can only exceed 10 if property B is set");
 *       return result;
 *     }
 *   }</pre>
 *
 * <h3>Effective Java</h3>
 *
 * <p><em>Effective Java</em> (ISBN 978-0321356680) recommends the Builder pattern <cite>when
 * designing classes whose constructors or static factories would have more than a handful of
 * parameters...say, four or more. But keep in mind that you may want to add parameters in
 * the future....it's often better to start with a builder in the first place.</cite>
 *
 * <p>We follow most of the recommendations of <em>Effective Java</em>, except that we do not
 * reqire all required parameters be passed to the Builder's constructor. This allows greater
 * flexibility in use, plus increased readability from all parameters being named, at the
 * cost of losing compile-time validation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface FreeBuilder {
}
