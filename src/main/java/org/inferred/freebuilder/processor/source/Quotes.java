package org.inferred.freebuilder.processor.source;

public class Quotes {

  /** Escapes each character in a string that has an escape sequence. */
  public static CharSequence escapeJava(CharSequence s) {
    return quote(s, 0, s.length());
  }

  public static CharSequence quote(CharSequence s, int start, int end) {
    StringBuilder buf = new StringBuilder();
    for (int i = start; i < end; i++) {
      appendQuoted(buf, s.charAt(i));
    }
    return buf;
  }

  private static void appendQuoted(StringBuilder buf, char ch) {
    switch (ch) {
      case '\b':
        buf.append("\\b");
        return;
      case '\f':
        buf.append("\\f");
        return;
      case '\n':
        buf.append("\\n");
        return;
      case '\r':
        buf.append("\\r");
        return;
      case '\t':
        buf.append("\\t");
        return;
      case '\"':
        buf.append("\\\"");
        return;
      case '\\':
        buf.append("\\\\");
        return;
      default:
        buf.append(ch);
        return;
    }
  }

  private Quotes() {}
}
