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
package org.inferred.freebuilder.processor.util;

import static org.junit.Assert.assertEquals;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;

import org.inferred.freebuilder.processor.util.testing.ModelRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.testing.EqualsTester;

/** Tests for {@link ImpliedClass}. */
@RunWith(JUnit4.class)
public class ImpliedClassTest {
  @Rule public final ModelRule model = new ModelRule();

  @Test
  public void testGetters() {
    Elements elements = model.elementUtils();
    PackageElement pkg = elements.getPackageElement("java.lang");

    ImpliedClass cls = new ImpliedClass(pkg, "Make_Me", elements);

    assertEquals(pkg, cls.getEnclosingElement());
    assertEquals(ElementKind.CLASS, cls.getKind());
    assertEquals(NestingKind.TOP_LEVEL, cls.getNestingKind());
    assertEquals("java.lang.Make_Me", cls.getQualifiedName().toString());
    assertEquals("Make_Me", cls.getSimpleName().toString());
  }

  @Test
  public void testToString() {
    Elements elements = model.elementUtils();
    PackageElement pkg = elements.getPackageElement("java.lang");

    ImpliedClass cls = new ImpliedClass(pkg, "Make_Me", elements);

    assertEquals("class java.lang.Make_Me", cls.toString());
  }

  @Test
  public void testEquality() {
    Elements elements = model.elementUtils();
    PackageElement pkg1 = elements.getPackageElement("java.lang");
    PackageElement pkg2 = elements.getPackageElement("java.util");

    ImpliedClass cls = new ImpliedClass(pkg1, "Make_Me", elements);
    ImpliedClass clsCopy = new ImpliedClass(pkg1, "Make_Me", elements);
    ImpliedClass clsDifferentName = new ImpliedClass(pkg1, "Bring_It_On", elements);
    ImpliedClass clsDifferentPackage = new ImpliedClass(pkg2, "Make_Me", elements);

    new EqualsTester()
        .addEqualityGroup(cls, clsCopy)
        .addEqualityGroup(clsDifferentName)
        .addEqualityGroup(clsDifferentPackage)
        .testEquals();
  }
}
