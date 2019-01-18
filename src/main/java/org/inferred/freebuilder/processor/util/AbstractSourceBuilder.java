package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkArgument;

import static org.inferred.freebuilder.processor.util.AnnotationSource.addSource;

import org.inferred.freebuilder.processor.util.TypeMirrorShortener.QualifiedNameAppendable;
import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.FeatureType;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;

public abstract class AbstractSourceBuilder<B extends AbstractSourceBuilder<B>>
    implements SourceBuilder, Appendable, QualifiedNameAppendable<B> {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  protected final FeatureSet features;

  protected AbstractSourceBuilder(FeatureSet features) {
    this.features = features;
  }

  protected abstract B getThis();

  static boolean isLegalType(TypeMirror mirror) {
    return !(new IsInvalidTypeVisitor().visit(mirror));
  }

  @Override
  public B add(Excerpt excerpt) {
    excerpt.addTo(this);
    return getThis();
  }

  @Override
  public B add(String template, Object... params) {
    TemplateApplier.withParams(params).onText(this::append).onParam(this::add).parse(template);
    return getThis();
  }

  @Override
  public B addLine(String fmt, Object... args) {
    add(fmt, args);
    append(LINE_SEPARATOR);
    return getThis();
  }

  @Override
  public <T extends Feature<T>> T feature(FeatureType<T> feature) {
    return features.get(feature);
  }

  @Override
  public B append(CharSequence csq) {
    return append(csq, 0, csq.length());
  }

  @Override
  public B append(CharSequence csq, int start, int end) {
    for (int i = start; i < end; i++) {
      append(csq.charAt(i));
    }
    return getThis();
  }

  private void add(Object arg) {
    if (arg instanceof Excerpt) {
      ((Excerpt) arg).addTo(this);
    } else if (arg instanceof Package) {
      append(((Package) arg).getName());
    } else if (arg instanceof Element) {
      ADD_ELEMENT.visit((Element) arg, this);
    } else if (arg instanceof Class<?>) {
      append(QualifiedName.of((Class<?>) arg));
    } else if (arg instanceof TypeMirror) {
      TypeMirror mirror = (TypeMirror) arg;
      checkArgument(isLegalType(mirror), "Cannot write unknown type %s", mirror);
      TypeMirrorShortener.appendShortened(mirror, this);
    } else if (arg instanceof QualifiedName) {
      append((QualifiedName) arg);
    } else if (arg instanceof AnnotationMirror) {
      addSource(this, (AnnotationMirror) arg);
    } else if (arg instanceof CharSequence) {
      append((CharSequence) arg);
    } else {
      append(arg.toString());
    }
  }

  private static final ElementVisitor<Void, AbstractSourceBuilder<?>> ADD_ELEMENT =
      new SimpleElementVisitor6<Void, AbstractSourceBuilder<?>>() {

        @Override
        public Void visitPackage(PackageElement pkg, AbstractSourceBuilder<?> p) {
          p.append(pkg.getQualifiedName());
          return null;
        }

        @Override
        public Void visitType(TypeElement type, AbstractSourceBuilder<?> p) {
          p.append(QualifiedName.of(type));
          return null;
        }

        @Override
        protected Void defaultAction(Element e, AbstractSourceBuilder<?> p) {
          p.append(e.toString());
          return null;
        }
      };

}