package org.inferred.freebuilder.processor.util;

import static java.util.Arrays.asList;
import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.JavaxPackage;

import java.util.List;

import javax.lang.model.type.TypeMirror;

public class Excerpts {

  private static final class AddingExcerpt extends Excerpt {
    private final String fmt;
    private final Object[] args;

    private AddingExcerpt(String fmt, Object[] args) {
      this.args = args;
      this.fmt = fmt;
    }

    @Override
    public void addTo(SourceBuilder source) {
      source.add(fmt, args);
    }

    @Override
    public String toString() {
      StringBuilder result = new StringBuilder()
          .append("Excerpts.add(\"")
          .append(fmt.replaceAll("[\\\"]", "\\\1"))
          .append('"');
      for (Object arg : args) {
        result.append(", ").append(arg);
      }
      result.append(")");
      return result.toString();
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("fmt", fmt);
      fields.add("args", asList(args));
    }
  }

  public static Excerpt add(final String fmt, final Object... args) {
    return new AddingExcerpt(fmt, args);
  }

  private static final class EmptyExcerpt extends Excerpt {
    @Override
    public void addTo(SourceBuilder source) {}

    @Override
    protected void addFields(FieldReceiver fields) {}
  }

  private static final Excerpt EMPTY = new EmptyExcerpt();

  public static Excerpt empty() {
    return EMPTY;
  }

  private static final class ForEachExcerpt extends Excerpt {
    private final TypeMirror elementType;
    private final String iterable;
    private final String method;

    private ForEachExcerpt(TypeMirror elementType, String iterable, String method) {
      this.elementType = elementType;
      this.iterable = iterable;
      this.method = method;
    }

    @Override
    public void addTo(SourceBuilder code) {
      if (code.feature(SOURCE_LEVEL).hasLambdas()) {
        code.addLine("%s.forEach(this::%s);", iterable, method);
      } else {
        code.addLine("for (%s element : %s) {", elementType, iterable)
            .addLine("  %s(element);", method)
            .addLine("}");
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("elementType", elementType);
      fields.add("iterable", iterable);
      fields.add("method", method);
    }
  }

  /**
   * Returns an excerpt calling {@code method} with each {@code elementType} element of
   * {@code iterable}.
   *
   * <p>Will be {@code iterable.forEach(this::method);} on Java 8+.
   */
  public static Excerpt forEach(TypeMirror elementType, String iterable, String method) {
    return new ForEachExcerpt(elementType, iterable, method);
  }

  private static final class GeneratedAnnotationExcerpt extends Excerpt {
    private final Class<?> generator;

    GeneratedAnnotationExcerpt(Class<?> generator) {
      this.generator = generator;
    }

    @Override
    public void addTo(SourceBuilder code) {
      QualifiedName generated = code.feature(JavaxPackage.JAVAX).generated().orNull();
      if (generated != null) {
        code.addLine("@%s(\"%s\")", generated, generator.getName());
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("generator", generator);
    }
  }

  /**
   * Returns an excerpt of the {@link javax.annotation.Generated} annotation, if available,
   * with value set to the full name of the {@code generator} class as recommended.
   */
  public static Excerpt generated(Class<?> generator) {
    return new GeneratedAnnotationExcerpt(generator);
  }

  private static final class JoiningExcerpt extends Excerpt {
    private final String separator;
    private final List<?> excerpts;

    private JoiningExcerpt(String separator, Iterable<?> excerpts) {
      this.separator = separator;
      this.excerpts = ImmutableList.copyOf(excerpts);
    }

    @Override
    public void addTo(SourceBuilder source) {
      String itemPrefix = "";
      for (Object object : excerpts) {
        source.add("%s%s", itemPrefix, object);
        itemPrefix = separator;
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("separator", separator);
      fields.add("excerpts", excerpts);
    }
  }

  public static Object join(final String separator, final Iterable<?> excerpts) {
    return new JoiningExcerpt(separator, excerpts);
  }

  private static final class EqualsExcerpt extends Excerpt {
    private final Object a;
    private final Object b;

    private EqualsExcerpt(Object a, Object b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public void addTo(SourceBuilder source) {
      QualifiedName objects = source.feature(SOURCE_LEVEL).javaUtilObjects().orNull();
      if (objects != null) {
        source.add("%s.equals(%s, %s)", objects, a, b);
      } else {
        source.add("(%1$s == %2$s || (%1$s != null && %1$s.equals(%2$s)))", a, b);
      }
    }

    @Override
    protected void addFields(FieldReceiver fields) {
      fields.add("a", a);
      fields.add("b", b);
    }
  }

  /** Returns an excerpt equivalent to Java 7's {@code Object.equals(a, b)}. */
  public static Object equals(Object a, Object b) {
    return new EqualsExcerpt(a, b);
  }

  private Excerpts() {}
}
