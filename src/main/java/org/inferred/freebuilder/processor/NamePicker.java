package org.inferred.freebuilder.processor;

import static org.inferred.freebuilder.processor.model.MethodFinder.methodsOn;
import static org.inferred.freebuilder.processor.model.ModelUtils.asElement;
import static org.inferred.freebuilder.processor.model.ModelUtils.getReturnType;

import static java.util.stream.Collectors.toMap;

import org.inferred.freebuilder.processor.Datatype.Visibility;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collector;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class NamePicker {

  /** Find an available name and visibility for a method. */
  public static NameAndVisibility pickName(
      DeclaredType targetType,
      Elements elements,
      Types types,
      TypeMirror returnType,
      String preferredName) {
    Map<String, ExecutableElement> methodsByName =
        methodsOn(asElement(targetType), elements, errorType -> { })
            .stream()
            .filter(noParameters())
            .collect(byName());

    String name = preferredName;
    Visibility visibility = Visibility.PUBLIC;

    for (int attempt = 1; true; attempt++) {
      ExecutableElement method = methodsByName.get(name);
      if (method == null) {
        // No (parameterless) method exists with this name, so we can create one
        return NameAndVisibility.of(name, visibility);
      }
      boolean sufficientVisibility = !method.getModifiers().contains(Modifier.PRIVATE);
      TypeMirror actualReturnType = getReturnType(targetType, method, types);
      boolean correctReturnType = types.isSameType(actualReturnType, returnType);
      if (sufficientVisibility && correctReturnType) {
        // A method exists with this name, but it is not incompatible
        if (!method.getModifiers().contains(Modifier.PUBLIC)) {
          visibility = Visibility.PACKAGE;
        }
        return NameAndVisibility.of(name, visibility);
      }

      // This method name is already taken by an incompatible method; try a different name.
      name = "_" + preferredName + "Impl";
      visibility = Visibility.PACKAGE;
      if (attempt > 1) {
        name += attempt;
      }
    }
  }

  private static Predicate<? super ExecutableElement> noParameters() {
    return method -> method.getParameters().isEmpty();
  }

  private static Collector<ExecutableElement, ?, Map<String, ExecutableElement>> byName() {
    return toMap(method -> method.getSimpleName().toString(), method -> method);
  }
}
