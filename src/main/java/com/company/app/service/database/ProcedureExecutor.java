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
import java.util.stream.Collectors;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;

/**
 * Executes database stored procedures with parameter parsing and type
 * conversion.
 * Supports both input and output parameters with automatic JDBC type mapping.
 */
public class ProcedureExecutor {

  /** Error status for failed operations */
  private static final String FAILED = "FAILED";

  /** Expected parameter format parts count */
  private static final int EXPECTED_PARAM_PARTS = 3;

  /** Context for parameter parsing operations */
  private static final String PARAMETER_PARSING = "parameter_parsing";

  /**
   * Immutable parameter object representing a stored procedure parameter.
   * Contains name, type, and value information for both input and output
   * parameters.
   */
  public static class ProcedureParam {

    /** Parameter name */
    private final String paramName;

    /** Parameter data type */
    private final String paramType;

    /** Parameter value (null for output parameters) */
    private final Object paramValue;

    /**
     * Constructs a new ProcedureParam.
     * 
     * @param name  parameter name
     * @param type  parameter data type
     * @param value parameter value
     */
    public ProcedureParam(final String name, final String type, final Object value) {
      this.paramName = name;
      this.paramType = type;
      this.paramValue = value;
    }

    /**
     * Gets the parameter name.
     * 
     * @return the parameter name
     */
    public String name() {
      return paramName;
    }

    /**
     * Gets the parameter type.
     * 
     * @return the parameter type
     */
    public String type() {
      return paramType;
    }

    /**
     * Gets the parameter value.
     * 
     * @return the parameter value
     */
    public Object value() {
      return paramValue;
    }

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
      final String typeUpper = paramType.toUpperCase(Locale.ROOT);
      Object result = null;

      switch (typeUpper) {
        case "NUMBER":
        case "INTEGER":
        case "INT":
          result = Integer.parseInt(paramValue.toString());
          break;
        case "DOUBLE":
          result = Double.parseDouble(paramValue.toString());
          break;
        case "BOOLEAN":
          result = Boolean.parseBoolean(paramValue.toString());
          break;
        default:
          result = paramValue;
          break;
      }

      return result;
    }
  }

  /**
   * Builds a callable statement string for the given procedure and parameter
   * counts.
   * 
   * @param procedureName name of the procedure to call
   * @param inputCount    number of input parameters
   * @param outputCount   number of output parameters
   * @return formatted call string
   */
  private String buildCallString(final String procedureName, final int inputCount, final int outputCount) {
    final int totalParams = inputCount + outputCount;
    final StringJoiner placeholders = new StringJoiner(",", "(", ")");
    for (int i = 0; i < totalParams; i++) {
      placeholders.add("?");
    }
    return "{call " + procedureName + placeholders + "}";
  }

  /**
   * Executes a stored procedure using string-based parameter parsing.
   * 
   * @param conn         database connection
   * @param procFullName full procedure name
   * @param inputParams  comma-separated input parameters in format
   *                     "name:type:value"
   * @param outputParams comma-separated output parameters in format "name:type"
   * @return map of output parameter names to values
   * @throws SQLException if database operation fails
   */
  public Map<String, Object> executeProcedureWithStrings(
      final Connection conn,
      final String procFullName,
      final String inputParams,
      final String outputParams)
      throws SQLException {
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
      if (isNullOrEmpty(inputParams)) {
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
      if (isNullOrEmpty(outputParams)) {
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

  private static boolean isNullOrEmpty(final String value) {
    return value == null || value.trim().isEmpty();
  }

  /**
   * Sets a parameter value on a CallableStatement with appropriate type handling.
   * 
   * @param stmt  the callable statement
   * @param index parameter index (1-based)
   * @param value parameter value
   * @throws SQLException if setting parameter fails
   */
  private void setParameter(final CallableStatement stmt, final int index, final Object value) throws SQLException {
    if (value == null) {
      stmt.setNull(index, Types.NULL);
    } else if (value instanceof Integer) {
      stmt.setInt(index, (Integer) value);
    } else if (value instanceof Double) {
      stmt.setDouble(index, (Double) value);
    } else if (value instanceof String) {
      stmt.setString(index, (String) value);
    } else if (value instanceof Boolean) {
      stmt.setBoolean(index, (Boolean) value);
    } else {
      stmt.setObject(index, value);
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

    switch (typeUpper) {
      case "STRING":
      case "VARCHAR":
      case "VARCHAR2":
        return Types.VARCHAR;
      case "INTEGER":
      case "INT":
        return Types.INTEGER;
      case "DOUBLE":
      case "NUMBER":
        return Types.DOUBLE;
      case "DATE":
        return Types.DATE;
      case "TIMESTAMP":
        return Types.TIMESTAMP;
      case "BOOLEAN":
        return Types.BOOLEAN;
      default:
        return Types.OTHER;
    }
  }
}
