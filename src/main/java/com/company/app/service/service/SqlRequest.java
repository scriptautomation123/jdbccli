package com.company.app.service.service;

import java.util.Collections;
import java.util.List;

/**
 * Immutable request object for SQL execution operations.
 * Extends DatabaseRequest to include SQL-specific parameters like
 * SQL statement, script file, and parameters.
 */
public final class SqlRequest extends DatabaseRequest {

    /** SQL statement to execute */
    private final String sql;

    /** SQL script file path */
    private final String script;

    /** SQL parameters list */
    private final List<Object> params;

    /**
     * Constructs a new SqlRequest from builder parameters.
     * 
     * @param builder builder containing SQL request parameters
     */
    private SqlRequest(final Builder builder) {
        super(builder);
        this.sql = builder.sqlField;
        this.script = builder.scriptField;
        this.params = builder.paramsField != null ? builder.paramsField : Collections.emptyList();
    }

    /**
     * Gets the SQL statement.
     * 
     * @return SQL statement
     */
    public String getSql() {
        return sql;
    }

    /**
     * Gets the SQL script file path.
     * 
     * @return script file path
     */
    public String getScript() {
        return script;
    }

    /**
     * Gets the SQL parameters list.
     * 
     * @return parameters list
     */
    public List<Object> getParams() {
        return params;
    }

    /**
     * Checks if this request is in script mode (script file specified).
     * 
     * @return true if script mode
     */
    public boolean isScriptMode() {
        return !isNullOrBlank(script);
    }

    /**
     * Checks if this request is in SQL mode (SQL statement specified).
     * 
     * @return true if SQL mode
     */
    public boolean isSqlMode() {
        return !isNullOrBlank(sql);
    }

    /**
     * Checks if this request is in password-only mode (no SQL or script specified).
     * 
     * @return true if password-only mode
     */
    public boolean isPasswordOnlyMode() {
        return isNullOrBlank(sql) && isNullOrBlank(script);
    }

    /**
     * Creates a new builder instance.
     * 
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SqlRequest objects supporting fluent API.
     */
    public static class Builder extends DatabaseRequest.Builder<Builder> {

        /** SQL statement field */
        private String sqlField;

        /** SQL script file field */
        private String scriptField;

        /** SQL parameters field */
        private List<Object> paramsField;

        /**
         * Sets the SQL statement.
         * 
         * @param sql SQL statement to set
         * @return this builder for method chaining
         */
        public Builder sql(final String sql) {
            this.sqlField = sql;
            return this;
        }

        /**
         * Sets the SQL script file path.
         * 
         * @param script script file path to set
         * @return this builder for method chaining
         */
        public Builder script(final String script) {
            this.scriptField = script;
            return this;
        }

        /**
         * Sets the SQL parameters list.
         * 
         * @param params parameters list to set
         * @return this builder for method chaining
         */
        public Builder params(final List<Object> params) {
            this.paramsField = params;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public SqlRequest build() {
            return new SqlRequest(this);
        }
    }

    /**
     * Checks if a string is null or contains only whitespace.
     * 
     * @param value string to check
     * @return true if null or blank
     */
    private static boolean isNullOrBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}