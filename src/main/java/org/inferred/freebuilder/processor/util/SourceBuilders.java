package org.inferred.freebuilder.processor.util;

import com.google.common.base.Strings;

/**
 * Provides static methods operating on or generating a {@link SourceBuilder}.
 */
public class SourceBuilders {

  /**
   * Returns a new {@link SourceBuilder} that indents all input by {@code indent} characters before
   * calling {@code delegate}.
   *
   * <p>This method assumes it is called immediately after a newline is written, and always
   * prepends the indent characters to the first text written out.
   */
  public static SourceBuilder withIndent(SourceBuilder delegate, int indent) {
    String indentChars = Strings.repeat(" ", indent);
    if (delegate instanceof IndentingSourceBuilder) {
      // Optimization: don't chain IndentingSourceBuilders.
      IndentingSourceBuilder previousIndenter = (IndentingSourceBuilder) delegate;
      return new IndentingSourceBuilder(
          previousIndenter.delegate, previousIndenter.indentChars + indentChars);
    } else {
      return new IndentingSourceBuilder(delegate, indentChars);
    }
  }

  private static class IndentingSourceBuilder implements SourceBuilder {
    private final SourceBuilder delegate;
    private final String indentChars;
    private boolean atNewline = true;

    private IndentingSourceBuilder(SourceBuilder delegate, String indentChars) {
      this.delegate = delegate;
      this.indentChars = indentChars;
    }

    @Override
    public SourceBuilder addLine(String fmt, Object... args) {
      StringBuilder indentedFormatString = new StringBuilder();
      if (atNewline && fmt.length() > 0 && fmt.charAt(0) != '\n') {
        indentedFormatString.append(indentChars);
      }
      indentedFormatString.append(fmt.replaceAll("(\n+)", "$1" + indentChars));
      indentedFormatString.append("\n");
      atNewline = true;
      delegate.add(indentedFormatString.toString(), args);
      return this;
    }

    @Override
    public SourceBuilder add(String fmt, Object... args) {
      if (fmt.endsWith("\n")) {
        addLine(fmt.substring(0, fmt.length() - 1), args);
      } else {
        StringBuilder indentedFormatString = new StringBuilder();
        if (atNewline) {
          indentedFormatString.append(indentChars);
        }
        indentedFormatString.append(fmt.replace("\n", "\n" + indentChars));
        atNewline = false;
        delegate.add(indentedFormatString.toString(), args);
      }
      return this;
    }

    @Override
    public SourceLevel getSourceLevel() {
      return delegate.getSourceLevel();
    }
  }

  private SourceBuilders() {}
}

