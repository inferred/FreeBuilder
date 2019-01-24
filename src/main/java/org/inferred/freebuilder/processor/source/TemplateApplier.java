package org.inferred.freebuilder.processor.source;

import static java.lang.Integer.parseInt;

import java.util.MissingFormatArgumentException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateApplier {

  public interface TextAppender {
    void append(CharSequence chars, int start, int end);
  }

  public interface ParamAppender {
    void append(Object param);
  }

  public static TemplateApplier withParams(Object[] params) {
    return new TemplateApplier(params);
  }

  private static final Pattern PARAM = Pattern.compile("%([%ns]|([1-9]\\d*)\\$s)");
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private final Object[] params;
  private TextAppender textAppender;
  private ParamAppender paramAppender;
  private int nextParam = 0;

  private TemplateApplier(Object[] params) {
    this.params = params;
  }

  public TemplateApplier onText(TextAppender textAppender) {
    this.textAppender = textAppender;
    return this;
  }

  public TemplateApplier onParam(ParamAppender paramAppender) {
    this.paramAppender = paramAppender;
    return this;
  }

  public TemplateApplier parse(CharSequence template) {
    int offset = 0;
    Matcher matcher = PARAM.matcher(template);
    while (matcher.find()) {
      if (offset != matcher.start()) {
        textAppender.append(template, offset, matcher.start());
      }
      if (matcher.group(1).contentEquals("%")) {
        textAppender.append("%", 0, 1);
      } else if (matcher.group(1).contentEquals("n")) {
        textAppender.append(LINE_SEPARATOR, 0, LINE_SEPARATOR.length());
      } else if (matcher.group(1).contentEquals("s")) {
        if (nextParam >= params.length) {
          throw new MissingFormatArgumentException(matcher.group());
        }
        paramAppender.append(params[nextParam++]);
      } else {
        int index = parseInt(matcher.group(2)) - 1;
        if (index >= params.length) {
          throw new MissingFormatArgumentException(matcher.group());
        }
        paramAppender.append(params[index]);
      }
      offset = matcher.end();
    }
    if (offset != template.length()) {
      textAppender.append(template, offset, template.length());
    }
    return this;
  }
}
