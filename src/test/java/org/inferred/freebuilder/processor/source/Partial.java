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
package org.inferred.freebuilder.processor.source;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isPrivate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import javassist.util.proxy.MethodFilter;
import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.ProxyFactory;

/**
 * Utility methods for creating partial type implementations for tests.
 *
 * <p>Concrete partial types can be created by generating {@link
 * UnsupportedOperationException}-throwing implementations for all methods that should not be called
 * in the test. This has some issues:
 *
 * <ol>
 *   <li>All the boilerplate obscures the intended behaviour of the class;
 *   <li>Tests are harder to write and maintain; and
 *   <li>The resulting type cannot be used if any implemented superclass or interface changes.
 * </ol>
 *
 * <p>This last issue is especially problematic for testing annotation processors, as the {@code
 * javax.lang.model} interfaces change between Java versions. Instead, we create an abstract class
 * containing only the methods with real behaviour, and create a concrete partial subclass of this
 * dynamically at runtime. This requires javassist, as {@link java.lang.reflect.Proxy} can only
 * subclass interfaces.
 */
public class Partial {

  /**
   * Constructs a partial instance of abstract type {@code cls}, passing {@code args} into its
   * constructor.
   *
   * <p>The returned object will throw an {@link UnsupportedOperationException} from any
   * unimplemented methods.
   */
  public static <T> T of(Class<T> cls, Object... args) {
    checkIsValidPartial(cls);
    try {
      Constructor<?> constructor = cls.getDeclaredConstructors()[0];
      ProxyFactory factory = new ProxyFactory();
      factory.setSuperclass(cls);
      factory.setFilter(
          new MethodFilter() {
            @Override
            public boolean isHandled(Method m) {
              return Modifier.isAbstract(m.getModifiers());
            }
          });
      @SuppressWarnings("unchecked")
      T partial =
          (T) factory.create(constructor.getParameterTypes(), args, new ThrowingMethodHandler());
      return partial;
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate " + cls, e);
    }
  }

  private static <T> void checkIsValidPartial(Class<T> cls) {
    checkArgument(isAbstract(cls.getModifiers()), "Partial class must be abstract");
    checkArgument(
        cls.getDeclaredConstructors().length == 1,
        "Partial class %s must have exactly one constructor (found %s)",
        cls,
        cls.getDeclaredConstructors().length);
    Constructor<?> constructor = cls.getDeclaredConstructors()[0];
    checkArgument(
        !isPrivate(constructor.getModifiers()),
        "Partial class %s must have a package-visible constructor",
        cls);
  }

  private static final class ThrowingMethodHandler implements MethodHandler {
    @Override
    public Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) {
      throw new UnsupportedOperationException();
    }
  }

  private Partial() {}
}
