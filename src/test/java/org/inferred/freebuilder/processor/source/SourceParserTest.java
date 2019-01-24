package org.inferred.freebuilder.processor.source;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SourceParserTest {

  @Mock private SourceParser.EventHandler eventHandler;
  private SourceParser parser;

  @Before
  public void setup() {
    parser = new SourceParser(eventHandler);
  }

  @Test
  public void packageDeclaration() {
    parse("/* This is my cool type! */ package com.example.foo;");
    verify(eventHandler).onPackageStatement("com.example.foo");
  }

  @Test
  public void packageWithUmlaut() {
    parse("package com.example.vögel;");
    verify(eventHandler).onPackageStatement("com.example.vögel");
  }

  @Test
  public void packageWithOddSpacing() {
    parse("package com.example\n    .foo;");
    verify(eventHandler).onPackageStatement("com.example.foo");
  }

  @Test
  public void ifBlockStart() {
    parse("if (something < someOtherThing) {");
    verify(eventHandler).onOtherBlockStart();
  }

  @Test
  public void blockEnd() {
    parse("}");
    verify(eventHandler).onBlockEnd();
  }

  @Test
  public void classDeclaration() {
    parse("public static abstract class FooBar {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of());
  }

  @Test
  public void classWithUmlaut() {
    parse("public class Vögel {");
    verify(eventHandler).onTypeBlockStart("class", "Vögel", ImmutableSet.of());
  }

  @Test
  public void genericClass() {
    parse("public class Foo<A, B> {");
    verify(eventHandler).onTypeBlockStart("class", "Foo", ImmutableSet.of());
  }

  @Test
  public void classWithSuperclass() {
    parse("public static abstract class FooBar extends Baz.Bam<Foo, Bar> {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Baz.Bam"));
  }

  @Test
  public void superclassWithOddSpacing() {
    parse("public static abstract class FooBar extends Baz .Bam<Foo, Bar> {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Baz.Bam"));
  }

  @Test
  public void superclassWithUmlaut() {
    parse("public static abstract class FooBar extends Vögel {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Vögel"));
  }

  @Test
  public void classImplementingInterface() {
    parse("public static abstract class FooBar implements Baz.Bam<Foo, Bar> {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Baz.Bam"));
  }

  @Test
  public void interfaceWithOddSpacing() {
    parse("public static abstract class FooBar implements Baz .Bam<Foo, Bar> {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Baz.Bam"));
  }

  @Test
  public void interfaceWithUmlaut() {
    parse("public static abstract class FooBar implements Vögel {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Vögel"));
  }

  @Test
  public void classImplementingMultipleInterfaces() {
    parse("public static abstract class FooBar implements Foo, Bar, Baz {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Foo", "Bar", "Baz"));
  }

  @Test
  public void classExtendingSuperclassAndImplementingMultipleInterfaces() {
    parse("public static abstract class FooBar extends Foo implements Bar, Baz {");
    verify(eventHandler).onTypeBlockStart("class", "FooBar", ImmutableSet.of("Foo", "Bar", "Baz"));
  }

  @Test
  public void methodWithNoArguments() {
    parse("void foo() {");
    verify(eventHandler).onMethodBlockStart("foo", ImmutableSet.of());
  }

  @Test
  public void methodWithOneArgument() {
    parse("public static int foo(String bar) {");
    verify(eventHandler).onMethodBlockStart("foo", ImmutableSet.of("bar"));
  }

  @Test
  public void methodWithTwoArguments() {
    parse("public static int foo(String bar, Map<K, V> baz) {");
    verify(eventHandler).onMethodBlockStart("foo", ImmutableSet.of("bar", "baz"));
  }

  @Test
  public void methodWithThreeArguments() {
    parse("public static int foo(String bar, Map<K, V> baz, Set<Integer> bam) {");
    verify(eventHandler).onMethodBlockStart("foo", ImmutableSet.of("bar", "baz", "bam"));
  }

  @Test
  public void methodWithQualifiedTypes() {
    parse("public static java.lang.Integer foo(java.lang.String bar) {");
    verify(eventHandler).onMethodBlockStart("foo", ImmutableSet.of("bar"));
  }

  @Test
  public void plainConstructor() {
    parse("FooBar(int bar) {");
    verify(eventHandler).onMethodBlockStart("FooBar", ImmutableSet.of("bar"));
  }

  @Test
  public void methodWithAnnotations() {
    parse("public static void foo(@MyAnnotation(\"name, age\") int bar) {");
    verify(eventHandler).onMethodBlockStart("foo", ImmutableSet.of("bar"));
  }

  @Test
  public void methodWithThrows() {
    parse("public static void foo(int bar) throws FooError, BarError {");
    verify(eventHandler).onMethodBlockStart("foo", ImmutableSet.of("bar"));
  }

  @Test
  public void parameterWithInterfaceKeywordIn() {
    // This triggered a bug due to accidentally marking the space after "interface" as optional.
    parse("public Builder setInterfaceType(boolean interfaceType) {");
    verify(eventHandler).onMethodBlockStart("setInterfaceType", ImmutableSet.of("interfaceType"));
  }

  @Test
  public void ifWithComparisons() {
    parse("if (a < b && c > d) {");
    verify(eventHandler).onOtherBlockStart();
  }

  @After
  public void teardown() {
    verifyNoMoreInteractions(eventHandler);
  }

  private void parse(CharSequence chars) {
    chars.chars().forEach(c -> parser.parse((char) c));
  }
}
