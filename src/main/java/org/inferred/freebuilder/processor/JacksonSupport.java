package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.Excerpt;
import org.inferred.freebuilder.processor.util.QualifiedName;
import org.inferred.freebuilder.processor.util.SourceBuilder;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class JacksonSupport {

  private static final String JSON_DESERIALIZE =
      "com.fasterxml.jackson.databind.annotation.JsonDeserialize";
  private static final QualifiedName JSON_PROPERTY =
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonProperty");

  public static Optional<JacksonSupport> create(TypeElement userValueType) {
    if (findAnnotationMirror(userValueType, JSON_DESERIALIZE).isPresent()) {
      return Optional.of(new JacksonSupport());
    }
    return Optional.absent();
  }

  private JacksonSupport() {}

  public void addJacksonAnnotations(
      Property.Builder resultBuilder, ExecutableElement getterMethod) {
    Optional<AnnotationMirror> annotation = findAnnotationMirror(getterMethod, JSON_PROPERTY);
    if (annotation.isPresent()) {
      resultBuilder.addAccessorAnnotations(new AnnotationExcerpt(annotation.get()));
    } else {
      resultBuilder.addAccessorAnnotations(new JsonPropertyExcerpt(resultBuilder.getName()));
    }
  }

  private static class AnnotationExcerpt implements Excerpt {

    private final AnnotationMirror annotation;

    AnnotationExcerpt(AnnotationMirror annotation) {
      this.annotation = annotation;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("%s", annotation);
    }
  }

  private static class JsonPropertyExcerpt implements Excerpt {

    private final String propertyName;

    JsonPropertyExcerpt(String propertyName) {
      this.propertyName = propertyName;
    }

    @Override
    public void addTo(SourceBuilder code) {
      code.addLine("@%s(\"%s\")", JSON_PROPERTY, propertyName);
    }
  }

}
