package org.inferred.freebuilder.processor;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;

import static org.inferred.freebuilder.processor.util.ClassTypeImpl.newTopLevelClass;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

import org.inferred.freebuilder.processor.util.CompilationUnitBuilder;
import org.inferred.freebuilder.processor.util.NoTypes;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;
import org.junit.ComparisonFailure;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeVisitor;

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
            getSubject().getVisibleNestedTypes(),
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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static ProcessingEnvironment mockEnvironment(
      QualifiedName generatedType,
      Set<QualifiedName> visibleTypes) {
    ProcessingEnvironment env = Mockito.mock(ProcessingEnvironment.class, new ReturnsDeepStubs());
    when(env.getElementUtils().getTypeElement(any()).getSuperclass().accept(any(), any()))
        .thenAnswer(invocation -> {
          TypeVisitor visitor = invocation.getArgumentAt(0, TypeVisitor.class);
          Object param = invocation.getArgumentAt(1, Object.class);
          return visitor.visitNoType(NoTypes.NONE, param);
        });
    when(env.getElementUtils().getPackageElement(any())).thenAnswer(invocation -> {
      CharSequence pkg = invocation.getArgumentAt(0, CharSequence.class);
      return mockPackageElement(pkg, generatedType, visibleTypes);
    });
    return env;
  }

  private static PackageElement mockPackageElement(
      CharSequence name,
      QualifiedName generatedType,
      Set<QualifiedName> visibleTypes) {
    List<TypeElement> topLevelTypes = visibleTypes.stream()
        .filter(type -> type.getPackage().contentEquals(name))
        .filter(QualifiedName::isTopLevel)
        .filter(Predicate.isEqual(generatedType).negate())
        .map(type -> newTopLevelClass(type.toString()).asElement())
        .collect(toList());
    PackageElement pkgElement = Mockito.mock(PackageElement.class, new ReturnsDeepStubs());
    doReturn(topLevelTypes).when(pkgElement).getEnclosedElements();
    return pkgElement;
  }
}
