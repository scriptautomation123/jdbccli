package com.company.app.service.service.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQL execution request backed by connection metadata and optional SQL payloads.
 */
public record SqlRequest(
        DatabaseRequest connection,
        Optional<String> sql,
        Optional<String> script,
        List<Object> params) implements DbRequest {

    public SqlRequest {
        connection = Objects.requireNonNull(connection, "Database connection details are required");
        sql = normalize(sql);
        script = normalize(script);
        params = params == null ? List.of() : List.copyOf(params);

        if (sql.isPresent() && script.isPresent()) {
            throw new IllegalArgumentException("Provide either SQL or --script, not both");
        }
    }

    @Override
    public String type() {
        return connection.type();
    }

    @Override
    public String database() {
        return connection.database();
    }

    @Override
    public String user() {
        return connection.user();
    }

    @Override
    public VaultConfig vaultConfig() {
        return connection.vaultConfig();
    }

    public boolean isScriptMode() {
        return script.isPresent();
    }

    public boolean isSqlMode() {
        return sql.isPresent();
    }

    public boolean isPasswordOnlyMode() {
        return sql.isEmpty() && script.isEmpty();
    }

    private static Optional<String> normalize(final Optional<String> value) {
        return value == null ? Optional.empty() : value.filter(v -> !v.isBlank());
    }
}