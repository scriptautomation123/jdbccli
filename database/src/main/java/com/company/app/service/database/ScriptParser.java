package com.company.app.service.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Parses SQL script files into individual executable statements. Handles both standard SQL
 * (semicolon-delimited) and Oracle PL/SQL blocks (slash-delimited).
 *
 * <p><strong>Oracle PL/SQL Support:</strong>
 *
 * <ul>
 *   <li>Anonymous blocks: BEGIN...END; followed by /
 *   <li>Named blocks: CREATE [OR REPLACE] PROCEDURE/FUNCTION/TRIGGER/PACKAGE... followed by /
 *   <li>Regular SQL: terminated by semicolon
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is stateless and thread-safe.
 */
public final class ScriptParser {

  /** Pattern to detect start of PL/SQL block (case-insensitive). */
  private static final Pattern PLSQL_BLOCK_START =
      Pattern.compile(
          "^\\s*(DECLARE|BEGIN|CREATE\\s+(OR\\s+REPLACE\\s+)?(PROCEDURE|FUNCTION|PACKAGE|TRIGGER|TYPE))\\b",
          Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

  /** Pattern for slash delimiter on its own line. */
  private static final Pattern SLASH_DELIMITER = Pattern.compile("^\\s*/\\s*$", Pattern.MULTILINE);

  private ScriptParser() {
    // Utility class
  }

  /**
   * Immutable container for a parsed SQL script.
   *
   * @param sourcePath original file path (for error reporting)
   * @param statements list of individual SQL statements (trimmed, non-empty)
   */
  public record ParsedScript(String sourcePath, List<String> statements) {

    public ParsedScript {
      Objects.requireNonNull(sourcePath, "Source path cannot be null");
      statements = statements != null ? List.copyOf(statements) : List.of();
    }

    /** Returns true if the script contains no executable statements. */
    public boolean isEmpty() {
      return statements.isEmpty();
    }

    /** Returns the number of statements in the script. */
    public int size() {
      return statements.size();
    }
  }

  /**
   * Parses a SQL script file into individual statements.
   *
   * @param scriptPath path to the SQL script file
   * @return parsed script containing individual statements
   * @throws IOException if the file cannot be read
   */
  public static ParsedScript parse(final String scriptPath) throws IOException {
    Objects.requireNonNull(scriptPath, "Script path cannot be null");

    final String content = Files.readString(Path.of(scriptPath), StandardCharsets.UTF_8);
    final List<String> statements = splitStatements(content);

    return new ParsedScript(scriptPath, statements);
  }

  /**
   * Parses script content (already loaded) into individual statements. Useful for testing or when
   * content is already in memory.
   *
   * @param content SQL script content
   * @param sourceName name/path for error reporting
   * @return parsed script containing individual statements
   */
  public static ParsedScript parseContent(final String content, final String sourceName) {
    Objects.requireNonNull(sourceName, "Source name cannot be null");

    if (content == null || content.isBlank()) {
      return new ParsedScript(sourceName, List.of());
    }

    return new ParsedScript(sourceName, splitStatements(content));
  }

  /**
   * Splits script content into individual statements. Detects PL/SQL blocks and uses appropriate
   * delimiters.
   *
   * @param content script content to split
   * @return list of trimmed, non-empty statements
   */
  private static List<String> splitStatements(final String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }

    final List<String> statements = new ArrayList<>();
    final StringBuilder current = new StringBuilder();
    final String[] lines = content.split("\\R");

    final ParserState state = new ParserState();

    for (final String line : lines) {
      final String trimmedLine = line.trim();

      updateBlockCommentState(trimmedLine, state);

      if (!shouldSkipLine(current, trimmedLine)) {
        updatePlsqlBlockState(trimmedLine, state);

        if (!processPlsqlTerminator(line, current, statements, state)) {
          appendLineToStatement(line, current);
          state.inString = updateStringState(line, state.inString);
          processSemicolonTerminator(trimmedLine, current, statements, state);
        }
      }
    }

    addRemainingStatement(current, statements);

    return statements;
  }

  /** Mutable state for parser loop. */
  private static final class ParserState {
    boolean inPlsqlBlock = false;
    boolean inString = false;
    boolean inBlockComment = false;
  }

  /** Updates block comment state based on current line. */
  private static void updateBlockCommentState(final String trimmedLine, final ParserState state) {
    if (state.inString) {
      return;
    }
    if (trimmedLine.contains("/*") && !trimmedLine.contains("*/")) {
      state.inBlockComment = true;
    }
    if (state.inBlockComment && trimmedLine.contains("*/")) {
      state.inBlockComment = false;
    }
  }

  /** Determines if current line should be skipped. */
  private static boolean shouldSkipLine(final StringBuilder current, final String trimmedLine) {
    return current.isEmpty() && isCommentLine(trimmedLine);
  }

  /** Updates PL/SQL block state if block start detected. */
  private static void updatePlsqlBlockState(final String trimmedLine, final ParserState state) {
    if (!state.inPlsqlBlock && !state.inBlockComment && isPLSQLBlockStart(trimmedLine)) {
      state.inPlsqlBlock = true;
    }
  }

  /**
   * Processes PL/SQL slash terminator if present.
   *
   * @return true if line was processed as terminator
   */
  private static boolean processPlsqlTerminator(
      final String line,
      final StringBuilder current,
      final List<String> statements,
      final ParserState state) {

    if (!state.inPlsqlBlock || !SLASH_DELIMITER.matcher(line).matches()) {
      return false;
    }

    addStatementIfValid(current.toString().trim(), statements);
    current.setLength(0);
    state.inPlsqlBlock = false;
    return true;
  }

  /** Appends line to current statement buffer. */
  private static void appendLineToStatement(final String line, final StringBuilder current) {
    if (!current.isEmpty()) {
      current.append("\n");
    }
    current.append(line);
  }

  /** Processes semicolon terminator for non-PL/SQL statements. */
  private static void processSemicolonTerminator(
      final String trimmedLine,
      final StringBuilder current,
      final List<String> statements,
      final ParserState state) {

    if (state.inPlsqlBlock || state.inBlockComment || state.inString) {
      return;
    }
    if (!trimmedLine.endsWith(";")) {
      return;
    }

    final String statement = current.toString().trim();
    final String cleaned = removeTrailingSemicolon(statement);
    addStatementIfValid(cleaned, statements);
    current.setLength(0);
  }

  /** Adds remaining statement if any exists. */
  private static void addRemainingStatement(
      final StringBuilder current, final List<String> statements) {
    final String remaining = current.toString().trim();
    if (remaining.isEmpty()) {
      return;
    }
    final String cleaned = removeTrailingSemicolon(remaining);
    addStatementIfValid(cleaned, statements);
  }

  /** Removes trailing semicolon from statement. */
  private static String removeTrailingSemicolon(final String statement) {
    return statement.endsWith(";")
        ? statement.substring(0, statement.length() - 1).trim()
        : statement;
  }

  /** Adds statement to list if it's valid and executable. */
  private static void addStatementIfValid(final String statement, final List<String> statements) {
    if (!statement.isEmpty() && isExecutableStatement(statement)) {
      statements.add(statement);
    }
  }

  /** Checks if a line starts a PL/SQL block. */
  private static boolean isPLSQLBlockStart(final String line) {
    return PLSQL_BLOCK_START.matcher(line).find();
  }

  /** Checks if a line is purely a comment. */
  private static boolean isCommentLine(final String line) {
    return line.startsWith("--") || line.startsWith("/*") || line.isEmpty();
  }

  /**
   * Updates string literal tracking state based on single quotes in line. Simple heuristic: odd
   * number of unescaped quotes toggles state.
   */
  private static boolean updateStringState(final String line, final boolean currentState) {
    int quoteCount = 0;
    boolean escaped = false;

    for (int i = 0; i < line.length(); i++) {
      final char c = line.charAt(i);
      if (c == '\'' && !escaped) {
        quoteCount++;
      }
      // Handle escaped quotes ('') in SQL
      escaped = (c == '\'');
    }

    // Odd number of quotes toggles the in-string state
    return quoteCount % 2 == 1 != currentState;
  }

  /**
   * Determines if a statement is executable (not just comments or whitespace).
   *
   * @param statement the statement to check
   * @return true if the statement should be executed
   */
  private static boolean isExecutableStatement(final String statement) {
    final String trimmed = statement.stripLeading();
    // Skip pure comment blocks
    if (trimmed.startsWith("--")) {
      return false;
    }
    // Check for content after stripping comments
    final String withoutLineComments = trimmed.replaceAll("--.*$", "").trim();
    return !withoutLineComments.isEmpty();
  }
}
