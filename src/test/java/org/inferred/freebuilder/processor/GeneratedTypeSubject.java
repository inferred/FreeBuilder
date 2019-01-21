package org.inferred.freebuilder.processor;

import static com.google.common.truth.Truth.THROW_ASSERTION_ERROR;

import static java.util.stream.Collectors.joining;

import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;

import org.inferred.freebuilder.processor.util.SourceBuilder;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.junit.ComparisonFailure;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    String source = SourceBuilder
        .forTesting(environmentFeatures.toArray(new Feature<?>[0]))
        .add(getSubject())
        .toString();
    if (!source.equals(expected)) {
      throw new ComparisonFailure("Generated code incorrect", expected, source);
    }
  }
}
