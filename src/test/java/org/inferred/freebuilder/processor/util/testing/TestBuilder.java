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
package org.inferred.freebuilder.processor.util.testing;

import com.google.common.base.Strings;
import com.google.common.collect.Multiset;
import com.google.common.truth.Truth;

import org.junit.Assert;
import org.junit.Test;

import java.util.Objects;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

/**
 * Simple builder API for a test method, suitable for use in {@link BehaviorTester}. See the
 * JavaDoc on that class for an example.
 *
 * <p>Automatically imports {@link Assert}.* and {@link Truth#assertThat}.
 *
 * <p>Does some ugly things to get meaningful file names and line numbers in the generated source.
 * If you invoke build() directly from your test method, the generated class name and method name
 * should line up with your test. Additionally, if you invoke addLine() directly from your test
 * method, without loops, the line numbers should line up with your test in Eclipse.
 * (Specifically, if you invoke addLine with line numbers that increase monotonically, and
 * do not include extra newlines in your code strings, then the test builder will be able to
 * generate a source file with those lines at the same line numbers.) Sadly, javac does not
 * appear to produce such specific line numbers when faced with a fluent API.
 *
 * <p>Note that there is no <b>incorrect</b> way to use the class; you will just find compiler
 * error messages and assertion stack traces are more useful when your test code is simple.
 */
public class TestBuilder {

  private final StringBuilder imports = new StringBuilder();
  private final StringBuilder code = new StringBuilder();
  private int lineNumber = 2;  // Our preamble takes up the first line of the source

  public TestBuilder addStaticImport(Class<?> cls, String method) {
    return addStaticImport(cls.getCanonicalName(), method);
  }

  public TestBuilder addStaticImport(String cls, String method) {
    imports
        .append("import static ")
        .append(cls)
        .append(".")
        .append(method)
        .append("; ");
    return this;
  }

  public TestBuilder addImport(Class<?> cls) {
    return addImport(cls.getCanonicalName());
  }

  public TestBuilder addImport(String cls) {
    imports
        .append("import ")
        .append(cls)
        .append("; ");
    return this;
  }

  public TestBuilder addPackageImport(String pkg) {
    imports
        .append("import ")
        .append(pkg)
        .append(".*; ");
    return this;
  }

  /**
   * Appends a formatted line of code to the source. Formatting is done by {@link String#format},
   * except that {@link Class} instances use their entity's name unadorned, rather than the usual
   * toString implementation.
   */
  public TestBuilder addLine(String fmt, Object... args) {
    StackTraceElement caller = new Exception().getStackTrace()[1];
    if (caller.getLineNumber() > lineNumber) {
      code.append(Strings.repeat("\n", caller.getLineNumber() - lineNumber));
      lineNumber = caller.getLineNumber();
    }

    Object[] substituteArgs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      substituteArgs[i] = SourceBuilder.substitute(args[i]);
    }
    String text = String.format(fmt, substituteArgs);

    lineNumber += text.length() - text.replace("\n", "").length() + 1;
    code.append(text).append("\n");
    return this;
  }

  /**
   * Returns a {@link JavaFileObject} for the test source added to the builder.
   */
  public TestSource build() {
    StackTraceElement caller = new Exception().getStackTrace()[1];
    return new TestSource(
        rootTestClassName(caller.getClassName()),
        caller.getMethodName(),
        imports.toString(),
        code.toString());
  }

  /**
   * Creates a unique test class name. Once no longer referenced, it can subsequently be reused,
   * to keep compiler errors and stack traces cleaner.
   */
  private static String rootTestClassName(String originalClassName) {
    int periodIndex = originalClassName.lastIndexOf('.');
    if (periodIndex != -1) {
      return originalClassName.substring(0, periodIndex)
        + ".generatedcode" + originalClassName.substring(periodIndex);
    } else {
      return "com.example.test.generatedcode.Test";
    }
  }

  public static class TestSource {

    private final String rootClassName;
    private final String methodName;
    private final String imports;
    private final String testCode;

    private TestSource(String rootClassName, String methodName, String imports, String testCode) {
      this.rootClassName = rootClassName;
      this.methodName = methodName;
      this.imports = imports;
      this.testCode = testCode;
    }

    TestFile selectName(Multiset<String> seenNames) {
      long id = seenNames.add(rootClassName, 1) + 1;
      String name = rootClassName + (id == 1 ? "" : "__" + id);
      return new TestFile(name, methodName, imports, testCode);
    }

    @Override
    public String toString() {
      return rootClassName + "." + methodName;
    }

    @Override
    public int hashCode() {
      return Objects.hash(rootClassName, methodName, imports, testCode);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof TestSource)) {
        return false;
      }
      TestSource other = (TestSource) obj;
      return Objects.equals(rootClassName, other.rootClassName)
          && Objects.equals(methodName, other.methodName)
          && Objects.equals(imports, other.imports)
          && Objects.equals(testCode, other.testCode);
    }
  }

  /**
   * In-memory implementation of {@link javax.tools.JavaFileObject JavaFileObject} for test code.
   */
  static class TestFile extends SimpleJavaFileObject {

    private final String className;
    private final String methodName;
    private final String source;

    private TestFile(String className, String methodName, String imports, String testCode) {
      super(SourceBuilder.uriForClass(className), Kind.SOURCE);
      this.className = className;
      this.methodName = methodName;
      int period = className.lastIndexOf('.');
      this.source = "package " + className.substring(0, period) + "; "
          + "import static " + Assert.class.getName() + ".*; "
          + "import static " + Truth.class.getName() + ".assertThat; "
          + imports
          + "public class " + className.substring(period + 1) + " {"
          + "  @" + Test.class.getName()
          + "  public static void " + methodName + "() throws Exception {\n"
          + testCode
          + "\n  }\n}";
    }

    /**
     * Gets the character content of this file object.
     */
    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }

    public String getClassName() {
      return className;
    }

    public String getMethodName() {
      return methodName;
    }
  }
}
