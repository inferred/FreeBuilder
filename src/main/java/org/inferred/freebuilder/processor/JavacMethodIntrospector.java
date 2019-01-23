/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor;

import com.google.common.collect.ImmutableSet;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;

/** Implementation of {@link MethodIntrospector} for javac. */
class JavacMethodIntrospector extends MethodIntrospector {

  /**
   * Returns a {@link MethodIntrospector} implementation for the given javac environment.
   *
   * @throws IllegalArgumentException if the environment is not from javac
   */
  public static MethodIntrospector instance(ProcessingEnvironment env) {
    return new JavacMethodIntrospector(Trees.instance(env));
  }

  private final Trees trees;

  private JavacMethodIntrospector(Trees trees) {
    this.trees = trees;
  }

  @Override
  public Set<Name> getOwnMethodInvocations(ExecutableElement method) {
    try {
      return ImmutableSet.copyOf(trees
          .getTree(method)
          .accept(OWN_METHOD_INVOCATIONS_FETCHER, null)
          .names);
    } catch (RuntimeException e) {
      // Fail gracefully
      return ImmutableSet.of();
    }
  }

  @Override
  public void visitAllOwnMethodInvocations(
      ExecutableElement method,
      OwnMethodInvocationVisitor visitor) {
    trees.getTree(method).accept(OWN_METHOD_INVOCATIONS_VISITOR, (tree, methodName) -> {
      visitor.visitInvocation(methodName, (kind, msg) -> {
        CompilationUnitTree compilationUnit = trees.getPath(method).getCompilationUnit();
        trees.printMessage(kind, msg, tree, compilationUnit);
      });
    });
  }

  /** Data object retuned by {@link #OWN_METHOD_INVOCATIONS_FETCHER}. */
  private static class TreeAnalysis {
    private final Set<Name> names = new HashSet<>();
    private boolean explicitReturn = false;
  }

  /** Tree visitor to find all method invocations that are guaranteed to be hit. */
  private static final SimpleTreeVisitor<TreeAnalysis, ?> OWN_METHOD_INVOCATIONS_FETCHER =
      new SimpleTreeVisitor<TreeAnalysis, Void>() {

        @Override
        public TreeAnalysis visitMethod(MethodTree node, Void p) {
          // A method is guaranteed to call all of its statements in order
          // UNLESS one of them has an explicit return statement.
          TreeAnalysis result = new TreeAnalysis();
          for (StatementTree statement : node.getBody().getStatements()) {
            TreeAnalysis statementAnalysis = statement.accept(this, p);
            result.names.addAll(statementAnalysis.names);
            if (statementAnalysis.explicitReturn) {
              result.explicitReturn = true;
              return result;
            }
          }
          return result;
        }

        @Override
        public TreeAnalysis visitExpressionStatement(ExpressionStatementTree node, Void p) {
          return node.getExpression().accept(this, p);
        }

        @Override
        public TreeAnalysis visitMethodInvocation(MethodInvocationTree node, Void p) {
          TreeAnalysis result = new TreeAnalysis();
          Name identifier = node.getMethodSelect().accept(OWNED_IDENTIFIER, null);
          if (identifier != null) {
            result.names.add(identifier);
          }
          return result;
        }

        @Override
        protected TreeAnalysis defaultAction(Tree node, Void p) {
          // In general, we have no knowledge about whether a language construct
          // will invoke a method or not, just based on analysing its children.
          // For instance, an "if" makes no guarantees, an "if...else"
          // guarantees the intersection of its paths, and a try...finally
          // guarantees its finally block.
          TreeAnalysis result = new TreeAnalysis();
          // However, any explicit return call _may_ be hit.
          result.explicitReturn = (RETURN_TREE_FINDER.scan(node, null) != null);
          return result;
        }
      };

  /** Returns the name of an identifier or a this member selection. */
  private static final SimpleTreeVisitor<Name, ?> OWNED_IDENTIFIER =
      new SimpleTreeVisitor<Name, Void>() {
        @Override
        public Name visitIdentifier(IdentifierTree node, Void p) {
          // An identifier is an own method invocation under the condition that we
          // only hit this case from visitMethodInvocation.
          return node.getName();
        }

        @Override
        public Name visitMemberSelect(MemberSelectTree node, Void p) {
          // A member select is an "own method invocation" if the expression is "this",
          // under the condition that we only hit this case from visitMethodInvocation.
          ExpressionTree lhs = node.getExpression();
          if (lhs.getKind() != Kind.IDENTIFIER) {
            return null;
          }
          if (!((IdentifierTree) lhs).getName().contentEquals("this")) {
            return null;
          }
          return node.getIdentifier();
        }
      };

  /** Tree scanner to return any ReturnTree, or null if none is present. */
  private static final TreeScanner<ReturnTree, ?> RETURN_TREE_FINDER =
      new TreeScanner<ReturnTree, Void>() {
        @Override
        public ReturnTree visitReturn(ReturnTree node, Void p) {
          return node;
        }

        @Override
        public ReturnTree reduce(ReturnTree r1, ReturnTree r2) {
          return (r1 != null) ? r1 : r2;
        }
      };

  private static final TreeScanner<?, BiConsumer<MethodInvocationTree, Name>>
      OWN_METHOD_INVOCATIONS_VISITOR =
          new TreeScanner<Void, BiConsumer<MethodInvocationTree, Name>>() {
            @Override
            public Void visitMethodInvocation(
                MethodInvocationTree node,
                BiConsumer<MethodInvocationTree, Name> biConsumer) {
              Name identifier = OWNED_IDENTIFIER.visit(node.getMethodSelect(), null);
              if (identifier != null) {
                biConsumer.accept(node, identifier);
              }
              return super.visitMethodInvocation(node, biConsumer);
            }
          };
}
