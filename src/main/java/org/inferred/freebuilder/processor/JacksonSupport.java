package org.inferred.freebuilder.processor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import org.inferred.freebuilder.processor.Metadata.Property;
import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.QualifiedName;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.Set;

import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;

class JacksonSupport {

  private static final String JSON_DESERIALIZE =
      "com.fasterxml.jackson.databind.annotation.JsonDeserialize";
  private static final QualifiedName JSON_PROPERTY =
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonProperty");
  private static final Set<QualifiedName> JACKSON_XML_ANNOTATIONS = ImmutableSet.of(
          QualifiedName.of("com.fasterxml.jackson.dataformat.xml.annotation","JacksonXmlProperty"),
          QualifiedName.of("com.fasterxml.jackson.dataformat.xml.annotation","JacksonXmlText"),
          QualifiedName.of("com.fasterxml.jackson.dataformat.xml.annotation",
                  "JacksonXmlElementWrapper"));
  /** Annotations which disable automatic generation of JsonProperty annotations. */
  private static final Set<QualifiedName> DISABLE_PROPERTY_ANNOTATIONS = ImmutableSet.of(
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonAnyGetter"),
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonIgnore"),
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonUnwrapped"),
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonValue"));

  public static Optional<JacksonSupport> create(TypeElement userValueType) {
    if (findAnnotationMirror(userValueType, JSON_DESERIALIZE).isPresent()) {
      return Optional.of(new JacksonSupport());
    }
    return Optional.absent();
  }

  private JacksonSupport() {}

  public void addJacksonAnnotations(
      Property.Builder resultBuilder, ExecutableElement getterMethod) {
    Optional<AnnotationMirror> jsonPropertyAnnotation = findAnnotationMirror(getterMethod,
            JSON_PROPERTY);
    if (jsonPropertyAnnotation.isPresent()) {
      resultBuilder.addAccessorAnnotations(Excerpts.add("%s%n", jsonPropertyAnnotation.get()));
    } else if (generateDefaultAnnotations(getterMethod)) {
      resultBuilder.addAccessorAnnotations(Excerpts.add(
          "@%s(\"%s\")%n", JSON_PROPERTY, resultBuilder.getName()));
    }

    for (QualifiedName jacksonXmlAnnotation : JACKSON_XML_ANNOTATIONS) {
      Optional<AnnotationMirror> jacksonXmlPropertyAnnotation = findAnnotationMirror(getterMethod,
              jacksonXmlAnnotation);
      if (jacksonXmlPropertyAnnotation.isPresent()) {
        resultBuilder.addAccessorAnnotations(Excerpts.add("%s%n",
                jacksonXmlPropertyAnnotation.get()));
      }
    }
  }

  private static boolean generateDefaultAnnotations(ExecutableElement getterMethod) {
    for (AnnotationMirror annotationMirror : getterMethod.getAnnotationMirrors()) {
      TypeElement annotationTypeElement =
          (TypeElement) (annotationMirror.getAnnotationType().asElement());
      QualifiedName annotationType = QualifiedName.of(annotationTypeElement);
      if (DISABLE_PROPERTY_ANNOTATIONS.contains(annotationType)) {
        return false;
      }
    }
    return true;
  }

}
