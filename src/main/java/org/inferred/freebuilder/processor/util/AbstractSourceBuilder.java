package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkArgument;

import static org.inferred.freebuilder.processor.util.AnnotationSource.addSource;

import org.inferred.freebuilder.processor.util.feature.Feature;
import org.inferred.freebuilder.processor.util.feature.FeatureSet;
import org.inferred.freebuilder.processor.util.feature.FeatureType;

import java.io.IOException;
import java.util.MissingFormatArgumentException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;

public abstract class AbstractSourceBuilder<B extends AbstractSourceBuilder<B>>
    implements SourceBuilder, Appendable {

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
  private static final Pattern TEMPLATE_PARAM = Pattern.compile("%([%ns]|([1-9]\\d*)\\$s)");

  protected final FeatureSet features;
  protected final Scope scope;

  protected AbstractSourceBuilder(FeatureSet features, Scope scope) {
    this.features = features;
    this.scope = scope;
  }

  protected abstract TypeShortener getShortener();

  protected abstract B getThis();

  @Override
  public abstract Appendable append(char c);

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
    int offset = 0;
    int nextParam = 0;
    Matcher matcher = TEMPLATE_PARAM.matcher(template);
    while (matcher.find()) {
      append(template.subSequence(offset, matcher.start()));
      if (matcher.group(1).contentEquals("%")) {
        append("%");
      } else if (matcher.group(1).contentEquals("n")) {
        append(LINE_SEPARATOR);
      } else if (matcher.group(1).contentEquals("s")) {
        if (nextParam >= params.length) {
          throw new MissingFormatArgumentException(matcher.group(0));
        }
        add(params[nextParam++]);
      } else {
        int index = Integer.parseInt(matcher.group(2)) - 1;
        if (index >= params.length) {
          throw new MissingFormatArgumentException(matcher.group(0));
        }
        add(params[index]);
      }
      offset = matcher.end();
    }
    append(template.subSequence(offset, template.length()));

    return getThis();
  }

  @Override
  public B addLine(String fmt, Object... args) {
    add(fmt, args);
    append(LINE_SEPARATOR);
    return getThis();
  }

  @Override
  public SourceStringBuilder subBuilder() {
    return new SourceStringBuilder(getShortener(), features, scope);
  }

  @Override
  public SourceStringBuilder nestedType(QualifiedName type, Set<QualifiedName> supertypes) {
    return new SourceStringBuilder(getShortener().inScope(type, supertypes), features, scope);
  }

  @Override
  public SourceStringBuilder subScope(Scope newScope) {
    return new SourceStringBuilder(getShortener(), features, newScope);
  }

  @Override
  public <T extends Feature<T>> T feature(FeatureType<T> feature) {
    return features.get(feature);
  }

  @Override
  public Scope scope() {
    return scope;
  }

  @Override
  public Appendable append(CharSequence csq) {
    return append(csq, 0, csq.length());
  }

  @Override
  public Appendable append(CharSequence csq, int start, int end) {
    for (int i = start; i < end; i++) {
      append(csq.charAt(i));
    }
    return getThis();
  }

  private void add(Object arg) {
    try {
      if (arg instanceof Excerpt) {
        ((Excerpt) arg).addTo(this);
      } else if (arg instanceof Package) {
        append(((Package) arg).getName());
      } else if (arg instanceof Element) {
        ADD_ELEMENT.visit((Element) arg, this);
      } else if (arg instanceof Class<?>) {
        getShortener().appendShortened(this, QualifiedName.of((Class<?>) arg));
      } else if (arg instanceof TypeMirror) {
        TypeMirror mirror = (TypeMirror) arg;
        checkArgument(isLegalType(mirror), "Cannot write unknown type %s", mirror);
        getShortener().appendShortened(this, mirror);
      } else if (arg instanceof QualifiedName) {
        getShortener().appendShortened(this, (QualifiedName) arg);
      } else if (arg instanceof AnnotationMirror) {
        addSource(this, (AnnotationMirror) arg);
      } else if (arg instanceof CharSequence) {
        append((CharSequence) arg);
      } else {
        append(arg.toString());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
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
          p.add(QualifiedName.of(type));
          return null;
        }

        @Override
        protected Void defaultAction(Element e, AbstractSourceBuilder<?> p) {
          p.append(e.toString());
          return null;
        }
      };

}