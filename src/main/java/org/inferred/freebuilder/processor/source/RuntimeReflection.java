package org.inferred.freebuilder.processor.source;

import org.inferred.freebuilder.processor.source.ScopeHandler.TypeInfo;
import org.inferred.freebuilder.processor.source.ScopeHandler.Visibility;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

class RuntimeReflection implements ScopeHandler.Reflection {

  private class RuntimeTypeInfo implements ScopeHandler.TypeInfo {

    private final Class<?> cls;
    private final QualifiedName name;

    RuntimeTypeInfo(Class<?> cls) {
      this.cls = cls;
      this.name = QualifiedName.of(cls);
    }

    @Override
    public QualifiedName name() {
      return name;
    }

    @Override
    public Visibility visibility() {
      int modifiers = cls.getModifiers();
      if (Modifier.isPublic(modifiers)) {
        return Visibility.PUBLIC;
      } else if (Modifier.isProtected(modifiers)) {
        return Visibility.PROTECTED;
      } else if (Modifier.isPrivate(modifiers)) {
        return Visibility.PRIVATE;
      } else if (cls.getEnclosingClass() != null && cls.getEnclosingClass().isInterface()) {
        return Visibility.PUBLIC;
      } else {
        return Visibility.PROTECTED;
      }
    }

    @Override
    public Stream<TypeInfo> supertypes() {
      return Stream.concat(stream(cls.getSuperclass()), Arrays.stream(cls.getInterfaces()))
          .map(RuntimeTypeInfo::new);
    }

    @Override
    public Stream<TypeInfo> nestedTypes() {
      return Arrays.stream(cls.getDeclaredClasses()).map(RuntimeTypeInfo::new);
    }
  }

  private final ClassLoader classLoader;

  RuntimeReflection(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  @Override
  public Optional<TypeInfo> find(String typename) {
    try {
      return Optional.of(classLoader.loadClass(typename)).map(RuntimeTypeInfo::new);
    } catch (ClassNotFoundException e) {
      return Optional.empty();
    }
  }

  private static <T> Stream<T> stream(T value) {
    return (value != null) ? Stream.of(value) : Stream.of();
  }
}
