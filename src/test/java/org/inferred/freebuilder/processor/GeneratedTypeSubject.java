package org.inferred.freebuilder.processor;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;

import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newNestedClass;
import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import static java.util.stream.Collectors.joining;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;
import org.junit.ComparisonFailure;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

class GeneratedTypeSubject extends Subject<GeneratedTypeSubject, GeneratedType> {

  public static GeneratedTypeSubject assertThat(GeneratedType subject) {
    return new GeneratedTypeSubject(THROW_ASSERTION_ERROR, subject);
  }

  private final Set<Feature<?>> environmentFeatures = new HashSet<>();

  private GeneratedTypeSubject(FailureStrategy failureStrategy, GeneratedType subject) {
    super(failureStrategy, subject);
  }

  public GeneratedTypeSubject given(Feature<?>... features) {
    environmentFeatures.addAll(Arrays.asList(features));
    return this;
  }

  public void generates(String... code) {
    String expected = Arrays.stream(code).collect(joining("\n", "", "\n"));
    CompilationUnitBuilder compilationUnitBuilder = new CompilationUnitBuilder(
            mockEnvironment(getSubject().getName(), getSubject().getVisibleNestedTypes()),
            getSubject().getName(),
            new StaticFeatureSet(environmentFeatures.toArray(new Feature<?>[0])));
    String rawSource = compilationUnitBuilder
        .add(getSubject())
        .toString();
    try {
      String formattedSource = new Formatter().formatSource(rawSource);
      if (!formattedSource.equals(expected)) {
        throw new ComparisonFailure("Generated code incorrect", expected, formattedSource);
      }
    } catch (FormatterException e) {
      int no = 0;
      for (String line : rawSource.split("\n")) {
        System.err.println((++no) + ": " + line);
      }
      failWithRawMessage("%s", e.getMessage());
    }
  }

  private static ProcessingEnvironment mockEnvironment(
      QualifiedName generatedType,
      Set<QualifiedName> visibleTypes) {
    ProcessingEnvironment env = Mockito.mock(ProcessingEnvironment.class, new ReturnsDeepStubs());
    when(env.getElementUtils().getTypeElement(any())).thenAnswer(invocation -> {
      CharSequence name = invocation.getArgumentAt(0, CharSequence.class);
      return mockTypeElement(name, generatedType, visibleTypes);
    });
    return env;
  }

  private static TypeElement mockTypeElement(
      CharSequence name,
      QualifiedName generatedType,
      Set<QualifiedName> visibleTypes) {
    try {
      Class<?> cls = ClassLoader.getSystemClassLoader().loadClass(name.toString());
      return classType(QualifiedName.of(cls));
    } catch (ClassNotFoundException e) {
      for (QualifiedName visibleType : visibleTypes) {
        if (visibleType.toString().contentEquals(name)) {
          if (visibleType.equals(generatedType) || visibleType.isNestedIn(generatedType)) {
            return null;
          } else {
            return classType(visibleType);
          }
        }
      }
      return null;
    }
  }

  private static TypeElement classType(QualifiedName visibleType) {
    if (visibleType.isTopLevel()) {
      return newTopLevelClass(visibleType.toString()).asElement();
    } else {
      TypeElement parent = classType(visibleType.enclosingType());
      return newNestedClass(parent, visibleType.getSimpleName()).asElement();
    }
  }
}
