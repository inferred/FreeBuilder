package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkState;

import static javax.lang.model.SourceVersion.isIdentifier;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.SourceVersion;

/**
 * API for parsing Java source with callbacks.
 */
class SourceParser {

  /**
   * Receive notifications of gross Java structure events.
   */
  interface EventHandler {
    void onTypeBlockStart(String keyword, String simpleName, Set<String> supertypes);
    void onMethodBlockStart(String methodName, Set<String> parameterNames);
    void onOtherBlockStart();
    void onBlockEnd();
  }

  private static final Pattern TYPE = Pattern.compile(
      "\\b(class|interface|enum|@interface)\\s+([^\\s<>]+)");
  private static final Pattern METHOD = Pattern.compile(
      "\\b([^\\s()<>,.]+)\\s*\\(([^()]*)\\)\\s*(throws\\b|$)");
  private static final Pattern ARGUMENTS = Pattern.compile(
      ",?[^,]+\\s([^\\s.,]+)\\s*");
  private static final Pattern IDENTIFIER = Pattern.compile("[^\\s.,]+(\\s*\\.\\s*[^\\s.,]+)*");

  private enum State {
    CODE,
    SLASH,
    STRING_LITERAL,
    STRING_LITERAL_ESCAPE,
    CHAR_LITERAL,
    CHAR_LITERAL_ESCAPE,
    LINE_COMMENT,
    BLOCK_COMMENT,
    BLOCK_COMMENT_STAR
  }

  private final EventHandler eventHandler;
  private final StringBuilder statement;
  private State state;

  SourceParser(EventHandler eventHandler) {
    this.eventHandler = eventHandler;
    statement = new StringBuilder();
    state = State.CODE;
  }

  public void parse(char c) {
    switch (state) {
      case CODE:
      case SLASH:
        statement.append(c);
        switch (c) {
          case '{':
            statement.deleteCharAt(statement.length() - 1);
            onBlockStart(statement.toString().trim());
            statement.setLength(0);
            break;
          case '}':
            eventHandler.onBlockEnd();
            statement.setLength(0);
            break;
          case ';':
            statement.setLength(0);
            break;
          case '"':
            state = State.STRING_LITERAL;
            break;
          case '/':
            if (state == State.CODE) {
              state = State.SLASH;
            } else {
              state = State.LINE_COMMENT;
              statement.delete(statement.length() - 2, statement.length());
            }
            break;
          case '*':
            if (state == State.CODE) {
              state = State.CODE;
            } else {
              state = State.BLOCK_COMMENT;
              statement.delete(statement.length() - 2, statement.length());
            }
            break;
        }
        break;

      case STRING_LITERAL:
        switch (c) {
          case '\\':
            state = State.STRING_LITERAL_ESCAPE;
            break;

          case '"':
            statement.append(c);
            state = State.CODE;
            break;
        }
        break;

      case STRING_LITERAL_ESCAPE:
        statement.append(c);
        state = State.STRING_LITERAL;
        break;

      case CHAR_LITERAL:
        switch (c) {
          case '\\':
            state = State.CHAR_LITERAL_ESCAPE;
            break;

          case '\'':
            statement.append(c);
            state = State.CODE;
            break;
        }
        break;

      case CHAR_LITERAL_ESCAPE:
        state = State.CHAR_LITERAL;
        break;

      case LINE_COMMENT:
        if (c == '\n') {
          state = State.CODE;
        }
        break;

      case BLOCK_COMMENT:
      case BLOCK_COMMENT_STAR:
        switch (c) {
          case '*':
            state = State.BLOCK_COMMENT_STAR;
            break;

          case '/':
            if (state == State.BLOCK_COMMENT_STAR) {
              state = State.CODE;
            }
            break;

          default:
            state = State.BLOCK_COMMENT;
            break;
        }
        break;
    }
  }

  private void onBlockStart(CharSequence raw) {
    CharSequence chars = withoutTypeParams(withoutAnnotations(raw));
    if (chars == null) {
      eventHandler.onOtherBlockStart();
      return;
    }
    Matcher typeMatcher = TYPE.matcher(chars);
    if (typeMatcher.find()) {
      Set<String> supertypes = supertypes(chars.subSequence(typeMatcher.end(), chars.length()));
      eventHandler.onTypeBlockStart(typeMatcher.group(1), typeMatcher.group(2), supertypes);
      return;
    }
    Matcher methodMatcher = METHOD.matcher(chars);
    if (methodMatcher.find()) {
      String methodName = methodMatcher.group(1);
      if (!SourceVersion.isKeyword(methodName)) {
        Set<String> args = new LinkedHashSet<>();
        Matcher argMatcher = ARGUMENTS.matcher(methodMatcher.group(2));
        int index = 0;
        while (argMatcher.find() && argMatcher.start() == index) {
          args.add(argMatcher.group(1));
          index = argMatcher.end();
        }
        if (index == methodMatcher.group(2).length()) {
          eventHandler.onMethodBlockStart(methodName, args);
          return;
        }
      }
    }
    eventHandler.onOtherBlockStart();
  }

  private static Set<String> supertypes(CharSequence chars) {
    Set<String> types = new HashSet<String>();
    Matcher rawTypeMatcher = IDENTIFIER.matcher(chars);
    while (rawTypeMatcher.find()) {
      String[] identifierParts = rawTypeMatcher.group().split("\\.");
      StringBuilder identifier = new StringBuilder();
      String separator = "";
      for (String part : identifierParts) {
        checkState(isIdentifier(part.trim()), "Invalid identifier %s", rawTypeMatcher.group());
        identifier.append(separator).append(part.trim());
        separator = ".";
      }
      types.add(identifier.toString());
    }
    types.remove("extends");
    types.remove("implements");
    return types;
  }

  enum AnnotationState {
    CODE, SYMBOL, NAME, SPACE, PERIOD, BODY
  }

  private static CharSequence withoutAnnotations(CharSequence chars) {
    StringBuilder result = new StringBuilder();
    AnnotationState state = AnnotationState.CODE;
    int depth = 0;
    for (int i = 0; i < chars.length(); i++) {
      char c = chars.charAt(i);
      switch (state) {
        case CODE:
          if (c == '@') {
            state = AnnotationState.SYMBOL;
          }
          break;

        case SYMBOL:
          if (Character.isJavaIdentifierStart(c)) {
            state = AnnotationState.NAME;
          } else if (!Character.isWhitespace(c)) {
            throw new IllegalStateException("Unexpected character " + c + " after @");
          }
          break;

        case NAME:
          if (Character.isWhitespace(c)) {
            state = AnnotationState.SPACE;
          } else if (c == '(') {
            state = AnnotationState.BODY;
            depth = 1;
          } else if (!Character.isJavaIdentifierPart(c)) {
            state = AnnotationState.CODE;
          }
          break;

        case SPACE:
          if (c == '(') {
            state = AnnotationState.BODY;
            depth = 1;
          } else if (c == '.') {
            state = AnnotationState.PERIOD;
          } else if (!Character.isWhitespace(c)) {
            state = AnnotationState.CODE;
          }
          break;

        case PERIOD:
          if (c == '(') {
            throw new IllegalStateException("Unexpected ( after .");
          } else if (Character.isJavaIdentifierStart(c)) {
            state = AnnotationState.NAME;
          } else if (!Character.isWhitespace(c)) {
            state = AnnotationState.CODE;
          }
          break;

        case BODY:
          if (c == '(') {
            depth++;
          } else if (c == ')') {
            depth--;
          }
          break;
      }
      if (state == AnnotationState.CODE) {
        result.append(c);
      } else if (state == AnnotationState.BODY && depth == 0) {
        state = AnnotationState.CODE;
      }
    }
    return result.toString();
  }

  private static CharSequence withoutTypeParams(CharSequence chars) {
    StringBuilder result = new StringBuilder();
    int depth = 0;
    for (int i = 0; i < chars.length(); i++) {
      char c = chars.charAt(i);
      switch (c) {
        case '<':
          depth++;
          break;

        case '>':
          depth--;
          if (depth < 0) {
            return null;
          }
          break;

        default:
          if (depth == 0) {
            result.append(c);
          }
          break;
      }
    }
    return result;
  }
}