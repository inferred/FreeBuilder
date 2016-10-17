package org.inferred.freebuilder.processor.util;

import static java.util.Arrays.asList;
import static org.inferred.freebuilder.processor.util.feature.FunctionPackage.FUNCTION_PACKAGE;

import com.google.common.collect.ImmutableList;

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
      if (code.feature(FUNCTION_PACKAGE).lambdasAvailable()) {
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

  private Excerpts() {}
}
