package com.company.app.service.database;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;
import com.company.app.service.util.StringUtils;

/**
 * Executes database stored procedures with parameter parsing and type
 * conversion.
 * Supports both input and output parameters with automatic JDBC type mapping.
 *
 * <p><strong>SECURITY:</strong> Procedure names are validated against an allowlist
 * pattern to prevent SQL injection. Only alphanumeric characters, underscores,
 * and dots (for schema qualification) are allowed.</p>
 */
public class ProcedureExecutor {

  /** Error status for failed operations */
  private static final String FAILED = "FAILED";

  /** Expected parameter format parts count */
  private static final int EXPECTED_PARAM_PARTS = 3;

  /** Context for parameter parsing operations */
  private static final String PARAMETER_PARSING = "parameter_parsing";

  /**
   * Allowlist pattern for valid procedure names to prevent SQL injection.
   * Allows: alphanumeric, underscore, and dot (for schema.procedure notation).
   * Examples: "my_proc", "schema.my_proc", "PROC123"
   */
  private static final Pattern VALID_PROCEDURE_NAME =
      Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");

  /**
   * Immutable parameter object representing a stored procedure parameter.
   * Contains name, type, and value information for both input and output
   * parameters.
   *
   * @param name  parameter name
   * @param type  parameter data type
   * @param value parameter value (null for output parameters)
   */
  public record ProcedureParam(String name, String type, Object value) {

    /**
     * Creates a ProcedureParam from a string in format "name:type:value".
     * 
     * @param input parameter string to parse
     * @return new ProcedureParam instance
     * @throws IllegalArgumentException if format is invalid
     */
    public static ProcedureParam fromString(final String input) {
      final String[] parts = input.split(":");
      if (parts.length != EXPECTED_PARAM_PARTS) {
        LoggingUtils.logStructuredError(
            PARAMETER_PARSING,
            "from_string",
            "INVALID_FORMAT",
            "Invalid parameter format. Expected 'name:type:value', got: " + input,
            null);
        throw new IllegalArgumentException(
            "Invalid parameter format. Expected 'name:type:value', got: " + input);
      }
      return new ProcedureParam(parts[0], parts[1], parts[2]);
    }

    /**
     * Converts the parameter value to the appropriate Java type based on the
     * parameter type.
     *
     * @return typed parameter value
     */
    public Object getTypedValue() {
      final String typeUpper = type.toUpperCase(Locale.ROOT);

      return switch (typeUpper) {
        case "NUMBER", "INTEGER", "INT" -> Integer.parseInt(value.toString());
        case "DOUBLE" -> Double.parseDouble(value.toString());
        case "BOOLEAN" -> Boolean.parseBoolean(value.toString());
        default -> value;
      };
    }
  }

  /**
   * Validates that a procedure name matches the allowlist pattern to prevent SQL injection.
   *
   * <p><strong>SECURITY:</strong> This method prevents SQL injection by ensuring only
   * safe characters are present in the procedure name. PreparedStatement does NOT protect
   * against injection in SQL identifiers (table/procedure names), only in parameter values.</p>
   *
   * @param procedureName the procedure name to validate
   * @throws IllegalArgumentException if the procedure name contains invalid characters
   */
  private void validateProcedureName(final String procedureName) {
    if (StringUtils.isNullOrBlank(procedureName)) {
      LoggingUtils.logStructuredError(
          "procedure_security",
          "validate_name",
          "INVALID_NAME",
          "Procedure name cannot be null or empty",
          null);
      throw new IllegalArgumentException("Procedure name cannot be null or empty");
    }

    if (!VALID_PROCEDURE_NAME.matcher(procedureName).matches()) {
      LoggingUtils.logStructuredError(
          "procedure_security",
          "validate_name",
          "INVALID_CHARACTERS",
          "Procedure name contains invalid characters: " + procedureName
              + ". Only alphanumeric, underscore, and dots allowed.",
          null);
      throw new IllegalArgumentException(
          "Invalid procedure name: " + procedureName
              + ". Only alphanumeric characters, underscores, and dots (for schema.procedure) are allowed.");
    }
  }

  /**
   * Builds a callable statement string for the given procedure and parameter
   * counts.
   *
   * <p><strong>SECURITY:</strong> Validates procedure name to prevent SQL injection.</p>
   *
   * @param procedureName name of the procedure to call (validated against allowlist)
   * @param inputCount    number of input parameters
   * @param outputCount   number of output parameters
   * @return formatted call string
   * @throws IllegalArgumentException if procedure name is invalid
   */
  private String buildCallString(final String procedureName, final int inputCount, final int outputCount) {
    validateProcedureName(procedureName);

    final int totalParams = inputCount + outputCount;
    final StringJoiner placeholders = new StringJoiner(",", "(", ")");
    for (int i = 0; i < totalParams; i++) {
      placeholders.add("?");
    }

    // Safe to concatenate: procedureName has been validated against allowlist pattern
    // Only alphanumeric, underscore, and dots are allowed - no SQL injection possible
    //noinspection SqlSourceToSinkFlow
    return "{call " + procedureName + placeholders + "}";
  }

  /**
   * Executes a stored procedure using string-based parameter parsing.
   *
   * <p><strong>SECURITY:</strong> The procedure name is validated against an allowlist
   * pattern before execution to prevent SQL injection attacks. Invalid characters will
   * cause an IllegalArgumentException to be thrown.</p>
   *
   * @param conn         database connection
   * @param procFullName full procedure name (validated - only alphanumeric, underscore, dots allowed)
   * @param inputParams  comma-separated input parameters in format
   *                     "name:type:value"
   * @param outputParams comma-separated output parameters in format "name:type"
   * @return map of output parameter names to values
   * @throws IllegalArgumentException if procedure name contains invalid characters
   * @throws IllegalArgumentException if procedure name contains invalid characters
   */
  public Map<String, Object> executeProcedureWithStrings(
      final Connection conn,
      final String procFullName,
      final String inputParams,
      final String outputParams){
    LoggingUtils.logProcedureExecution(procFullName, inputParams, outputParams);
    try {
      final List<ProcedureParam> inputs = parseStringInputParams(inputParams);
      final List<ProcedureParam> outputs = parseStringOutputParams(outputParams);
      final String callSql = buildCallString(procFullName, inputs.size(), outputs.size());

      try (CallableStatement call = conn.prepareCall(callSql)) {
        return executeCallableStatement(call, inputs, outputs);
      }
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          "procedure_execution",
          "execute",
          FAILED,
          "Failed to execute procedure with string parameters: " + procFullName,
          e);
      throw ExceptionUtils.wrap(
          e, "Failed to execute procedure with string parameters: " + procFullName);
    }
  }

  private Map<String, Object> executeCallableStatement(
      final CallableStatement call,
      final List<ProcedureParam> inputs,
      final List<ProcedureParam> outputs) throws SQLException {

    int paramIndex = 1;
    final Map<String, Integer> outParamIndices = new HashMap<>();

    // Set input parameters
    for (final ProcedureParam input : inputs) {
      setParameter(call, paramIndex++, input.getTypedValue());
    }

    // Register output parameters
    for (final ProcedureParam output : outputs) {
      outParamIndices.put(output.name(), paramIndex);
      call.registerOutParameter(paramIndex++, getJdbcType(output.type()));
    }

    // Execute and collect results
    call.execute();
    final Map<String, Object> result = new LinkedHashMap<>();
    for (final Map.Entry<String, Integer> entry : outParamIndices.entrySet()) {
      result.put(entry.getKey(), call.getObject(entry.getValue()));
    }

    return result;
  }

  /**
   * Parses input parameter strings into ProcedureParam objects.
   * 
   * @param inputParams comma-separated input parameter strings
   * @return list of parsed input parameters
   */
  private List<ProcedureParam> parseStringInputParams(final String inputParams) {
    try {
      if (StringUtils.isNullOrBlank(inputParams)) {
        return Collections.emptyList();
      }
      return Arrays.stream(inputParams.split(","))
          .map(String::trim)
          .filter(s -> s.contains(":"))
          .map(ProcedureParam::fromString)
          .collect(Collectors.toList());
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          PARAMETER_PARSING, "parse_input", FAILED, "Failed to parse string input parameters", e);
      throw ExceptionUtils.wrap(e, "Failed to parse string input parameters");
    }
  }

  /**
   * Parses output parameter strings into ProcedureParam objects.
   * 
   * @param outputParams comma-separated output parameter strings
   * @return list of parsed output parameters
   */
  private List<ProcedureParam> parseStringOutputParams(final String outputParams) {
    try {
      if (StringUtils.isNullOrBlank(outputParams)) {
        return Collections.emptyList();
      }
      return Arrays.stream(outputParams.split(","))
          .map(String::trim)
          .filter(s -> s.contains(":"))
          .map(this::createOutputParam)
          .collect(Collectors.toList());
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          PARAMETER_PARSING,
          "parse_output",
          FAILED,
          "Failed to parse string output parameters",
          e);
      throw ExceptionUtils.wrap(e, "Failed to parse string output parameters");
    }
  }

  private ProcedureParam createOutputParam(final String param) {
    final String[] parts = param.split(":");
    return new ProcedureParam(parts[0], parts[1], null);
  }

  /**
   * Sets a parameter value on a CallableStatement with appropriate type handling.
   * Uses Java 21 pattern matching for clean type-based dispatch.
   *
   * @param stmt  the callable statement
   * @param index parameter index (1-based)
   * @param value parameter value
   * @throws SQLException if setting parameter fails
   */
  private void setParameter(final CallableStatement stmt, final int index, final Object value) throws SQLException {
    switch (value) {
      case null -> stmt.setNull(index, Types.NULL);
      case Integer i -> stmt.setInt(index, i);
      case Double d -> stmt.setDouble(index, d);
      case String s -> stmt.setString(index, s);
      case Boolean b -> stmt.setBoolean(index, b);
      default -> stmt.setObject(index, value);
    }
  }

  /**
   * Maps parameter type strings to JDBC Types constants.
   *
   * @param type parameter type string
   * @return corresponding JDBC Types constant
   */
  private int getJdbcType(final String type) {
    final String typeUpper = type.toUpperCase(Locale.ROOT);

    return switch (typeUpper) {
      case "STRING", "VARCHAR", "VARCHAR2" -> Types.VARCHAR;
      case "INTEGER", "INT" -> Types.INTEGER;
      case "DOUBLE", "NUMBER" -> Types.DOUBLE;
      case "DATE" -> Types.DATE;
      case "TIMESTAMP" -> Types.TIMESTAMP;
      case "BOOLEAN" -> Types.BOOLEAN;
      default -> Types.OTHER;
    };
  }
}
