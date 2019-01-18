package org.inferred.freebuilder.processor.util;

import static com.google.common.collect.Iterables.getLast;

import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;

import org.inferred.freebuilder.processor.util.Scope.FileScope;
import org.inferred.freebuilder.processor.util.Scope.MethodScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class BlockHandler implements SourceParser.EventHandler {

  private interface Block {
    TypeShortener typeShortener();
    Scope scope();
    Block packageStatement(String packageName);
    Block type(String simpleName, Set<String> supertypes);
    Block method(String methodName, Set<String> paramNames);
    Block block();
    void close();
    CharSequence source();
  }

  private static class UnpackagedBlock implements Block {

    private final CharSequence source;
    private final ScopeHandler scopeHandler;

    UnpackagedBlock(CharSequence source, ScopeHandler scopeHandler) {
      this.source = source;
      this.scopeHandler = scopeHandler;
    }

    @Override
    public TypeShortener typeShortener() {
      return new TypeShortener.AlwaysShorten();
    }

    @Override
    public Scope scope() {
      throw new IllegalStateException("Missing package declaration");
    }

    @Override
    public Block packageStatement(String packageName) {
      return new PackagedBlock(source, new ImportManager(), scopeHandler, packageName);
    }

    @Override
    public Block type(String simpleName, Set<String> supertypes) {
      throw new IllegalStateException("Missing package declaration");
    }

    @Override
    public Block method(String methodName, Set<String> paramNames) {
      throw new IllegalStateException("Missing package declaration");
    }

    @Override
    public Block block() {
      throw new IllegalStateException("Missing package declaration");
    }

    @Override
    public void close() {
      throw new IllegalStateException("Unexpected '}'");
    }

    @Override
    public CharSequence source() {
      return source;
    }
  }

  private static class ScopedBlock implements Block {

    private final ScopeAwareTypeShortener typeShortener;
    private final Scope scope;
    private final CharSequence source;

    ScopedBlock(ScopeAwareTypeShortener typeShortener, Scope scope, CharSequence source) {
      this.typeShortener = typeShortener;
      this.scope = scope;
      this.source = source;
    }

    @Override
    public TypeShortener typeShortener() {
      return typeShortener;
    }

    @Override
    public Scope scope() {
      return scope;
    }

    @Override
    public Block packageStatement(String packageName) {
      throw new IllegalStateException("Illegal package keyword");
    }

    @Override
    public Block type(String simpleName, Set<String> supertypes) {
      ScopeAwareTypeShortener nestedTypeShortener = typeShortener.inScope(simpleName, supertypes);
      return new ScopedBlock(nestedTypeShortener, scope, source);
    }

    @Override
    public Block method(String methodName, Set<String> paramNames) {
      Scope methodScope = new MethodScope(scope);
      for (String paramName : paramNames) {
        methodScope.putIfAbsent(new IdKey(paramName), this);
      }
      return new ScopedBlock(typeShortener, methodScope, source);
    }

    @Override
    public Block block() {
      return new ScopedBlock(typeShortener, scope, source);
    }

    @Override
    public void close() { }

    @Override
    public CharSequence source() {
      return source;
    }
  }

  private static class PackagedBlock extends ScopedBlock {

    private final ImportManager importManager;
    private final int importsOffset;

    PackagedBlock(
        CharSequence source,
        ImportManager importManager,
        ScopeHandler scopeHandler,
        String packageName) {
      super(
          new ScopeAwareTypeShortener(importManager, scopeHandler, packageName),
          new FileScope(),
          source);
      this.importManager = importManager;
      importsOffset = source.length();
    }

    @Override
    public Block method(String methodName, Set<String> paramNames) {
      throw new IllegalStateException("Unexpected start of method '" + methodName + "'");
    }

    @Override
    public void close() {
      throw new IllegalStateException("Unexpected '}'");
    }

    @Override
    public CharSequence source() {
      StringBuilder s = new StringBuilder();
      s.append(super.source(), 0, importsOffset);
      if (!importManager.getClassImports().isEmpty()) {
        s.append("\n\n");
        for (String classImport : importManager.getClassImports()) {
          s.append("import ").append(classImport).append(";\n");
        }
      }
      s.append(super.source(), importsOffset, super.source().length());
      return s;
    }
  }

  private final List<Block> blocks;

  BlockHandler(CharSequence source, ScopeHandler scopeHandler) {
    blocks = new ArrayList<>();
    blocks.add(new UnpackagedBlock(source, scopeHandler));
  }

  TypeShortener typeShortener() {
    return getLast(blocks).typeShortener();
  }

  Scope scope() {
    return getLast(blocks).scope();
  }

  @Override
  public void onPackageStatement(String packageName) {
    blocks.add(getLast(blocks).packageStatement(packageName));
  }

  @Override
  public void onTypeBlockStart(String keyword, String simpleName, Set<String> supertypes) {
    blocks.add(getLast(blocks).type(simpleName, supertypes));
  }

  @Override
  public void onMethodBlockStart(String methodName, Set<String> paramNames) {
    blocks.add(getLast(blocks).method(methodName, paramNames));
  }

  @Override
  public void onOtherBlockStart() {
    blocks.add(getLast(blocks).block());
  }

  @Override
  public void onBlockEnd() {
    blocks.remove(blocks.size() - 1).close();
  }

  @Override
  public String toString() {
    String source = getLast(blocks).source().toString();
    try {
      return new Formatter().formatSource(source);
    } catch (FormatterException | RuntimeException e) {
      StringBuilder message = new StringBuilder()
          .append("Formatter failed:\n")
          .append(e.getMessage())
          .append("\nGenerated source:");
      int lineNo = 0;
      for (String line : source.split("\n")) {
        message
            .append("\n")
            .append(++lineNo)
            .append(": ")
            .append(line);
      }
      throw new RuntimeException(message.toString());
    }
  }
}
