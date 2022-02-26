package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.BuildableType.maybeBuilder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import org.inferred.freebuilder.FreeBuilder;
import org.inferred.freebuilder.processor.source.testing.ModelRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildableTypeTest {

  @Rule public final ModelRule model = new ModelRule();

  @Test
  public void simpleFreebuilderAnnotatedType() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder { }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertTrue(result.isPresent());
  }

  @Test
  public void simpleFreebuilderAnnotatedTypeWithExplicitBuildMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    public DataType build();",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertTrue(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithHiddenBuildMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    protected DataType build();",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertFalse(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithExplicitBuildPartialMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    public DataType buildPartial();",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertTrue(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithHiddenBuildPartialMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    protected DataType buildPartial();",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertFalse(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithExplicitClearMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    public Builder clear();",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertTrue(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithHiddenClearMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    protected Builder clear();",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertFalse(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithExplicitMergeFromBuilderMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    public Builder mergeFrom(Builder builder);",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertTrue(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithHiddenMergeFromBuilderMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    protected Builder mergeFrom(Builder builder);",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertFalse(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithExplicitMergeFromValueMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    public Builder mergeFrom(DataType value);",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertTrue(result.isPresent());
  }

  @Test
  public void freebuilderAnnotatedTypeWithHiddenMergeFromValueMethod() {
    TypeElement type =
        model.newType(
            "package com.example;",
            "@" + FreeBuilder.class.getName(),
            "public interface DataType {",
            "  int value();",
            "  class Builder extends DataType_Builder {",
            "    protected Builder mergeFrom(DataType value);",
            "  }",
            "}");
    Optional<DeclaredType> result =
        maybeBuilder((DeclaredType) type.asType(), model.elementUtils(), model.typeUtils());
    assertFalse(result.isPresent());
  }
}
