/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.gitnode.os.actions.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The GitHub Actions expression language, evaluated server-side (job-level {@code if}). Mirrors the
 * runner's Go implementation so a workflow behaves the same wherever it is evaluated.
 *
 * <p>Supported: literals (null, booleans, numbers, 'strings'), context lookups (dotted paths and
 * {@code ['index']}), parentheses, {@code !}, {@code < <= > >=}, {@code == !=}, {@code &&} and
 * {@code ||} (value-returning like GitHub), and the functions contains, startsWith, endsWith,
 * format, join, toJSON, fromJSON plus the status functions success(), failure(), cancelled() and
 * always().
 */
@NullMarked
@SuppressWarnings("checkstyle:CyclomaticComplexity") // a tokenizer/parser is one branch per token
public final class ExpressionEngine {

  /** Largest magnitude below which a whole double prints as an integer. */
  private static final double MAX_PLAIN_INTEGER = 1e15;

  private static final int HEX_RADIX = 16;

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final Pattern NUMBER =
      Pattern.compile("^-?(?:0[xX][0-9a-fA-F]+|(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?)");
  private static final Pattern FORMAT_PLACEHOLDER = Pattern.compile("\\{\\{|}}|\\{(\\d+)}");

  private ExpressionEngine() {}

  /** Raised for expressions that cannot be parsed or evaluated. */
  public static final class ExpressionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExpressionException(final String message) {
      super(message);
    }
  }

  /**
   * Evaluates a bare expression (no {@code ${{ }}} wrapper).
   *
   * @param source the expression
   * @param vars flattened context values keyed by dotted path, e.g. {@code github.ref}
   * @param status the running job status: success, failure, cancelled or skipped
   */
  public static @Nullable Object evaluate(
      final String source, final Map<String, String> vars, final String status) {

    final var parser = new Parser(tokenize(source), vars, status);
    final var value = parser.parseOr();
    if (parser.peek().kind() != Kind.EOF) {
      throw new ExpressionException("unexpected '" + parser.peek().text() + "'");
    }
    return value;
  }

  public static boolean truthy(final @Nullable Object value) {
    return switch (value) {
      case null -> false;
      case Boolean b -> b;
      case Double d -> d != 0 && !d.isNaN();
      case String s -> !s.isEmpty();
      default -> true;
    };
  }

  public static String asString(final @Nullable Object value) {
    return switch (value) {
      case null -> "";
      case Boolean b -> b.toString();
      case Double d -> formatNumber(d);
      default -> value.toString();
    };
  }

  private static String formatNumber(final double d) {
    if (d == Math.rint(d) && Math.abs(d) < MAX_PLAIN_INTEGER) {
      return Long.toString((long) d);
    }
    return Double.toString(d);
  }

  // ── tokenizer ──────────────────────────────────────────────────────────────

  private enum Kind {
    EOF,
    NUM,
    STR,
    IDENT,
    OP,
    LPAREN,
    RPAREN,
    LBRACK,
    RBRACK,
    COMMA,
    DOT
  }

  private record Token(Kind kind, String text) {}

  private static List<Token> tokenize(final String source) {
    final var tokens = new ArrayList<Token>();
    final int length = source.length();
    int i = 0;
    while (i < length) {
      final char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '(') {
        tokens.add(new Token(Kind.LPAREN, "("));
        i++;
      } else if (c == ')') {
        tokens.add(new Token(Kind.RPAREN, ")"));
        i++;
      } else if (c == '[') {
        tokens.add(new Token(Kind.LBRACK, "["));
        i++;
      } else if (c == ']') {
        tokens.add(new Token(Kind.RBRACK, "]"));
        i++;
      } else if (c == ',') {
        tokens.add(new Token(Kind.COMMA, ","));
        i++;
      } else if (c == '.' && !(i + 1 < length && Character.isDigit(source.charAt(i + 1)))) {
        tokens.add(new Token(Kind.DOT, "."));
        i++;
      } else if (c == '\'') {
        i = readString(source, i, tokens);
      } else if (Character.isDigit(c)
          || c == '.'
          || (c == '-' && i + 1 < length && Character.isDigit(source.charAt(i + 1)))) {
        final Matcher m = NUMBER.matcher(source.substring(i));
        if (!m.find()) {
          throw new ExpressionException("bad number near '" + source.substring(i) + "'");
        }
        tokens.add(new Token(Kind.NUM, m.group()));
        i += m.end();
      } else if (Character.isLetter(c) || c == '_' || c == '*') {
        int j = i + 1;
        while (j < length && isIdentPart(source.charAt(j))) {
          j++;
        }
        tokens.add(new Token(Kind.IDENT, source.substring(i, j)));
        i = j;
      } else {
        i = readOperator(source, i, tokens);
      }
    }
    tokens.add(new Token(Kind.EOF, ""));
    return tokens;
  }

  private static boolean isIdentPart(final char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '*';
  }

  private static int readString(final String source, final int start, final List<Token> tokens) {
    final var sb = new StringBuilder();
    int i = start + 1;
    while (i < source.length()) {
      final char c = source.charAt(i);
      if (c == '\'') {
        if (i + 1 < source.length() && source.charAt(i + 1) == '\'') {
          sb.append('\'');
          i += 2;
          continue;
        }
        tokens.add(new Token(Kind.STR, sb.toString()));
        return i + 1;
      }
      sb.append(c);
      i++;
    }
    throw new ExpressionException("unterminated string");
  }

  private static int readOperator(final String source, final int i, final List<Token> tokens) {
    if (i + 1 < source.length()) {
      final String two = source.substring(i, i + 2);
      if (two.equals("==")
          || two.equals("!=")
          || two.equals("<=")
          || two.equals(">=")
          || two.equals("&&")
          || two.equals("||")) {
        tokens.add(new Token(Kind.OP, two));
        return i + 2;
      }
    }
    final char c = source.charAt(i);
    if (c == '!' || c == '<' || c == '>') {
      tokens.add(new Token(Kind.OP, String.valueOf(c)));
      return i + 1;
    }
    throw new ExpressionException("unexpected character '" + c + "'");
  }

  // ── parser / evaluator ─────────────────────────────────────────────────────

  private static final class Parser {

    private final List<Token> tokens;
    private final Map<String, String> vars;
    private final String status;
    private int pos;

    Parser(final List<Token> tokens, final Map<String, String> vars, final String status) {
      this.tokens = tokens;
      this.vars = vars;
      this.status = status;
    }

    Token peek() {
      return this.tokens.get(this.pos);
    }

    private Token next() {
      final var t = this.tokens.get(this.pos);
      if (t.kind() != Kind.EOF) {
        this.pos++;
      }
      return t;
    }

    private boolean isOp(final String text) {
      final var t = this.peek();
      return t.kind() == Kind.OP && t.text().equals(text);
    }

    @Nullable Object parseOr() {
      var left = this.parseAnd();
      while (this.isOp("||")) {
        this.next();
        final var right = this.parseAnd();
        if (!truthy(left)) {
          left = right;
        }
      }
      return left;
    }

    private @Nullable Object parseAnd() {
      var left = this.parseEquality();
      while (this.isOp("&&")) {
        this.next();
        final var right = this.parseEquality();
        if (truthy(left)) {
          left = right;
        }
      }
      return left;
    }

    private @Nullable Object parseEquality() {
      var left = this.parseComparison();
      while (this.isOp("==") || this.isOp("!=")) {
        final String op = this.next().text();
        final var right = this.parseComparison();
        final boolean equal = looseEqual(left, right);
        left = op.equals("!=") ? !equal : equal;
      }
      return left;
    }

    private @Nullable Object parseComparison() {
      var left = this.parseUnary();
      while (this.isOp("<") || this.isOp("<=") || this.isOp(">") || this.isOp(">=")) {
        final String op = this.next().text();
        final var right = this.parseUnary();
        left = compare(op, left, right);
      }
      return left;
    }

    private @Nullable Object parseUnary() {
      if (this.isOp("!")) {
        this.next();
        return !truthy(this.parseUnary());
      }
      return this.parsePrimary();
    }

    private @Nullable Object parsePrimary() {
      final var t = this.next();
      switch (t.kind()) {
        case LPAREN -> {
          final var value = this.parseOr();
          if (this.next().kind() != Kind.RPAREN) {
            throw new ExpressionException("expected )");
          }
          return value;
        }
        case STR -> {
          return t.text();
        }
        case NUM -> {
          return parseNumber(t.text());
        }
        case IDENT -> {
          return this.parseIdentifier(t.text());
        }
        default -> throw new ExpressionException("unexpected '" + t.text() + "'");
      }
    }

    private @Nullable Object parseIdentifier(final String name) {
      switch (name.toLowerCase(Locale.ROOT)) {
        case "true" -> {
          return Boolean.TRUE;
        }
        case "false" -> {
          return Boolean.FALSE;
        }
        case "null" -> {
          return null;
        }
        default -> {
          // fall through to call or context path
        }
      }
      if (this.peek().kind() == Kind.LPAREN) {
        return this.parseCall(name);
      }
      return this.parsePath(name);
    }

    private @Nullable Object parsePath(final String first) {
      final var path = new StringBuilder(first);
      while (true) {
        switch (this.peek().kind()) {
          case DOT -> {
            this.next();
            final var segment = this.next();
            if (segment.kind() != Kind.IDENT && segment.kind() != Kind.NUM) {
              throw new ExpressionException("expected property name after '.'");
            }
            path.append('.').append(segment.text());
          }
          case LBRACK -> {
            this.next();
            final var index = this.parseOr();
            if (this.next().kind() != Kind.RBRACK) {
              throw new ExpressionException("expected ]");
            }
            path.append('.').append(asString(index));
          }
          default -> {
            return this.lookup(path.toString());
          }
        }
      }
    }

    private @Nullable Object lookup(final String path) {
      String raw = this.vars.get(path);
      if (raw == null) {
        for (final var entry : this.vars.entrySet()) {
          if (entry.getKey().equalsIgnoreCase(path)) {
            raw = entry.getValue();
            break;
          }
        }
      }
      if (raw == null) {
        return null; // unknown context values are null, like GitHub
      }
      // Dispatch inputs travel as strings; restore GitHub's boolean typing for them.
      if (path.regionMatches(true, 0, "inputs.", 0, "inputs.".length())) {
        if (raw.equals("true")) {
          return Boolean.TRUE;
        }
        if (raw.equals("false")) {
          return Boolean.FALSE;
        }
      }
      return raw;
    }

    private @Nullable Object parseCall(final String name) {
      this.next(); // (
      final var args = new ArrayList<@Nullable Object>();
      while (this.peek().kind() != Kind.RPAREN) {
        args.add(this.parseOr());
        if (this.peek().kind() == Kind.COMMA) {
          this.next();
        } else if (this.peek().kind() != Kind.RPAREN) {
          throw new ExpressionException("expected , or ) in call to " + name);
        }
      }
      this.next(); // )
      return this.call(name.toLowerCase(Locale.ROOT), args);
    }

    private @Nullable Object call(final String name, final List<@Nullable Object> args) {
      switch (name) {
        case "success" -> {
          return this.status.isEmpty() || this.status.equals("success");
        }
        case "failure" -> {
          return this.status.equals("failure");
        }
        case "cancelled" -> {
          return this.status.equals("cancelled");
        }
        case "always" -> {
          return Boolean.TRUE;
        }
        case "contains" -> {
          require(name, args, 2);
          final var items = jsonArray(asString(args.get(0)));
          if (items != null) { // contains(array, item): membership, as on GitHub
            final var wanted = lower(args.get(1));
            return items.stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).equals(wanted));
          }
          return lower(args.get(0)).contains(lower(args.get(1)));
        }
        case "startswith" -> {
          require(name, args, 2);
          return lower(args.get(0)).startsWith(lower(args.get(1)));
        }
        case "endswith" -> {
          require(name, args, 2);
          return lower(args.get(0)).endsWith(lower(args.get(1)));
        }
        case "format" -> {
          require(name, args, 1);
          return format(asString(args.get(0)), args.subList(1, args.size()));
        }
        case "join" -> {
          require(name, args, 1);
          return join(asString(args.get(0)), args.size() > 1 ? asString(args.get(1)) : ",");
        }
        case "tojson" -> {
          require(name, args, 1);
          return toJson(args.get(0));
        }
        case "fromjson" -> {
          require(name, args, 1);
          return fromJson(asString(args.get(0)));
        }
        default -> throw new ExpressionException("unknown function " + name + "()");
      }
    }
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static void require(
      final String name, final List<@Nullable Object> args, final int count) {
    if (args.size() < count) {
      throw new ExpressionException(name + "() needs " + count + " argument(s)");
    }
  }

  private static String lower(final @Nullable Object value) {
    return asString(value).toLowerCase(Locale.ROOT);
  }

  /** The elements of text that holds a JSON array (how fromJSON hands one back), else null. */
  private static @Nullable List<String> jsonArray(final String text) {
    final var trimmed = text.strip();
    if (!trimmed.startsWith("[")) {
      return null;
    }
    try {
      final var node = JSON.readTree(trimmed);
      if (!node.isArray()) {
        return null;
      }
      final var items = new ArrayList<String>();
      node.forEach(
          element -> items.add(element.isValueNode() ? element.asText() : element.toString()));
      return items;
    } catch (final com.fasterxml.jackson.core.JsonProcessingException ex) {
      return null;
    }
  }

  private static String join(final String list, final String separator) {
    final var array = jsonArray(list);
    if (array != null) {
      return String.join(separator, array);
    }
    final var parts = new ArrayList<String>();
    for (final String part : list.split("[,\\n]")) {
      parts.add(part.strip());
    }
    return String.join(separator, parts);
  }

  private static String format(final String template, final List<@Nullable Object> args) {
    final Matcher m = FORMAT_PLACEHOLDER.matcher(template);
    final var sb = new StringBuilder();
    while (m.find()) {
      final String token = m.group();
      final String replacement;
      if (token.equals("{{")) {
        replacement = "{";
      } else if (token.equals("}}")) {
        replacement = "}";
      } else {
        final int index = Integer.parseInt(m.group(1));
        replacement = index < args.size() ? asString(args.get(index)) : "";
      }
      m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static String toJson(final @Nullable Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (final com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new ExpressionException("toJSON: " + ex.getOriginalMessage());
    }
  }

  private static @Nullable Object fromJson(final String text) {
    try {
      final Object parsed = JSON.readValue(text, Object.class);
      return switch (parsed) {
        case null -> null;
        case Boolean b -> b;
        case Number n -> n.doubleValue();
        case String s -> s;
        default -> text; // objects and arrays stay as their JSON text
      };
    } catch (final com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new ExpressionException("fromJSON: " + ex.getOriginalMessage());
    }
  }

  private static double parseNumber(final String text) {
    try {
      if (text.startsWith("0x") || text.startsWith("0X")) {
        return Long.parseLong(text.substring(2), HEX_RADIX);
      }
      return Double.parseDouble(text);
    } catch (final NumberFormatException ex) {
      throw new ExpressionException("bad number '" + text + "'");
    }
  }

  private static double toNumber(final @Nullable Object value) {
    return switch (value) {
      case null -> 0;
      case Boolean b -> b ? 1 : 0;
      case Double d -> d;
      case String s -> {
        final String trimmed = s.strip();
        if (trimmed.isEmpty()) {
          yield 0;
        }
        try {
          yield parseNumber(trimmed);
        } catch (final ExpressionException ex) {
          yield Double.NaN;
        }
      }
      default -> Double.NaN;
    };
  }

  private static @Nullable Boolean boolString(final String s) {
    if (s.equalsIgnoreCase("true")) {
      return Boolean.TRUE;
    }
    if (s.equalsIgnoreCase("false")) {
      return Boolean.FALSE;
    }
    return null;
  }

  private static boolean looseEqual(final @Nullable Object left, final @Nullable Object right) {
    if (left instanceof String l && right instanceof String r) {
      return l.equalsIgnoreCase(r);
    }
    // Deliberate deviation from GitHub: a boolean compared with "true"/"false" compares as
    // booleans, so GitNode-style `inputs.flag != 'true'` keeps working next to `!= true`.
    if (left instanceof Boolean lb && right instanceof String rs && boolString(rs) != null) {
      return lb.equals(boolString(rs));
    }
    if (right instanceof Boolean rb && left instanceof String ls && boolString(ls) != null) {
      return rb.equals(boolString(ls));
    }
    if (left == null && right == null) {
      return true;
    }
    final double ln = toNumber(left);
    final double rn = toNumber(right);
    if (Double.isNaN(ln) || Double.isNaN(rn)) {
      return false;
    }
    return ln == rn;
  }

  private static boolean compare(
      final String op, final @Nullable Object left, final @Nullable Object right) {
    if (left instanceof String l && right instanceof String r) {
      final int c = l.toLowerCase(Locale.ROOT).compareTo(r.toLowerCase(Locale.ROOT));
      return switch (op) {
        case "<" -> c < 0;
        case "<=" -> c <= 0;
        case ">" -> c > 0;
        default -> c >= 0;
      };
    }
    final double a = toNumber(left);
    final double b = toNumber(right);
    if (Double.isNaN(a) || Double.isNaN(b)) {
      return false;
    }
    return switch (op) {
      case "<" -> a < b;
      case "<=" -> a <= b;
      case ">" -> a > b;
      default -> a >= b;
    };
  }
}
