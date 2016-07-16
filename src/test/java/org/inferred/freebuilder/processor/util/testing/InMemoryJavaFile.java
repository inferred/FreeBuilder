package org.inferred.freebuilder.processor.util.testing;

import com.google.common.base.Joiner;

import org.inferred.freebuilder.processor.util.QualifiedName;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

public class InMemoryJavaFile extends InMemoryFile implements JavaFileObject {

  private final QualifiedName qualifiedName;
  private final Kind kind;

  public InMemoryJavaFile(QualifiedName qualifiedName, Kind kind) {
    super(name(qualifiedName));
    this.qualifiedName = qualifiedName;
    this.kind = kind;
  }

  @Override
  public Kind getKind() {
    return kind;
  }

  @Override
  public boolean isNameCompatible(String simpleName, Kind kind) {
    return qualifiedName.getSimpleName().equals(simpleName) && this.kind == kind;
  }

  @Override
  public NestingKind getNestingKind() {
    return qualifiedName.isTopLevel() ? NestingKind.TOP_LEVEL : NestingKind.MEMBER;
  }

  @Override
  public Modifier getAccessLevel() {
    return null;
  }

  private static String name(QualifiedName qualifiedName) {
    StringBuilder name = new StringBuilder();
    if (!qualifiedName.getPackage().isEmpty()) {
      name.append(qualifiedName.getPackage().replace('.', '/')).append('/');
    }
    Joiner.on('$').appendTo(name, qualifiedName.getSimpleNames());
    name.append(".java");
    return name.toString();
  }
}
