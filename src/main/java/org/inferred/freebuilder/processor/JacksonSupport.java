package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;

import com.google.common.base.Optional;

import org.inferred.freebuilder.processor.Metadata.Property.Builder;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

class JacksonSupport {

  private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";

  public static void addJacksonAnnotations(Builder resultBuilder, ExecutableElement getterMethod) {
    Optional<AnnotationMirror> annotation =
        findAnnotationMirror(getterMethod, JSON_PROPERTY);
    if (annotation.isPresent()) {
      resultBuilder.addAccessorAnnotations(annotation.get());
    }
  }

}
