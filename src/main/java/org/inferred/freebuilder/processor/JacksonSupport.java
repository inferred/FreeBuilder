package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.util.ModelUtils.findAnnotationMirror;

import com.google.common.collect.ImmutableSet;

import org.inferred.freebuilder.processor.util.Excerpts;
import org.inferred.freebuilder.processor.util.QualifiedName;

import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

class JacksonSupport {

  private static final String JSON_DESERIALIZE =
      "com.fasterxml.jackson.databind.annotation.JsonDeserialize";
  private static final QualifiedName JSON_PROPERTY =
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonProperty");
  private static final String JACKSON_XML_ANNOTATION_PACKAGE =
      "com.fasterxml.jackson.dataformat.xml.annotation";
  /** Annotations which disable automatic generation of JsonProperty annotations. */
  private static final Set<QualifiedName> DISABLE_PROPERTY_ANNOTATIONS = ImmutableSet.of(
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonAnyGetter"),
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonIgnore"),
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonUnwrapped"),
      QualifiedName.of("com.fasterxml.jackson.annotation", "JsonValue"));

  public static Optional<JacksonSupport> create(TypeElement userValueType, Elements elements) {
    return findAnnotationMirror(userValueType, JSON_DESERIALIZE)
        .map($ -> new JacksonSupport(elements));
  }

  private final Elements elements;

  private JacksonSupport(Elements elements) {
    this.elements = elements;
  }

  public void addJacksonAnnotations(
      Property.Builder resultBuilder,
      ExecutableElement getterMethod) {
    Optional<AnnotationMirror> jsonPropertyAnnotation = findAnnotationMirror(getterMethod,
            JSON_PROPERTY);
    if (jsonPropertyAnnotation.isPresent()) {
      resultBuilder.addAccessorAnnotations(Excerpts.add("%s%n", jsonPropertyAnnotation.get()));
    } else if (generateDefaultAnnotations(getterMethod)) {
      resultBuilder.addAccessorAnnotations(Excerpts.add(
          "@%s(\"%s\")%n", JSON_PROPERTY, resultBuilder.getName()));
    }

    getterMethod
        .getAnnotationMirrors()
        .stream()
        .filter(this::isXmlAnnotation)
        .forEach(annotation -> {
          resultBuilder.addAccessorAnnotations(code -> code.addLine("%s", annotation));
        });
  }

  private boolean isXmlAnnotation(AnnotationMirror mirror) {
    Name pkg = elements.getPackageOf(mirror.getAnnotationType().asElement()).getQualifiedName();
    return pkg.contentEquals(JACKSON_XML_ANNOTATION_PACKAGE);
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
