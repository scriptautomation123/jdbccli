package com.company.app.service.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import com.company.app.service.auth.PasswordRequest;
import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.database.DatabaseConnectionManager;
import com.company.app.service.service.model.DbRequest;
import com.company.app.service.service.model.ExecutionResult;
import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;

/**
 * Encapsulates the common execution context for database operations.
 * Handles password resolution, connection lifecycle, and execution orchestration.
 * Uses composition and functional interfaces to avoid inheritance coupling.
 */
public final class DatabaseExecutionContext {

    /** Context for password resolution operations */
    private static final String PASSWORD_RESOLUTION = "password_resolution";

    /** Context for database execution operations */
    private static final String DATABASE_EXECUTION = "database_execution";

    /** Status for failed operations */
    private static final String FAILED = "FAILED";

    /** Password resolver for authentication */
    private final PasswordResolver passwordResolver;

    /**
     * Constructs a new DatabaseExecutionContext. 
     * 
     * @param passwordResolver resolver for database passwords
     */
    public DatabaseExecutionContext(final PasswordResolver passwordResolver) {
        if (passwordResolver == null) {
            throw new IllegalArgumentException("PasswordResolver cannot be null");
        }
        this.passwordResolver = passwordResolver;
    }

    /**
     * Executes a database operation with automatic password resolution and 
     * connection management.
     * This is the primary orchestration method that handles the full lifecycle: 
     * 1. Password resolution
     * 2. Connection creation
     * 3. Operation execution
     * 4. Resource cleanup
     * 5. Error handling
     * 
     * @param request database request containing authentication and connection parameters
     * @param executor functional interface that executes the actual database operation
     * @return execution result from the executor
     */
    public ExecutionResult executeWithPasswordResolution(
            final DbRequest request, 
            final ConnectionExecutor executor) {
        
        if (request == null) {
            return ExecutionResult.failure(1, "[ERROR] Database request cannot be null");
        }
        if (executor == null) {
            return ExecutionResult.failure(1, "[ERROR] Executor cannot be null");
        }

        try {
            // Step 1: Resolve password
            final Optional<String> password = resolvePassword(request);
            if (password.isEmpty()) {
                LoggingUtils.logStructuredError(
                    PASSWORD_RESOLUTION,
                    "resolve",
                    FAILED,
                    "Failed to resolve password for user: " + request.user(),
                    null);
                return ExecutionResult.failure(
                    1, "[ERROR] Failed to resolve password for user: " + request.user());
            }

            // Step 2: Create a connection and execute with automatic cleanup
            try (Connection conn = createConnection(request, password.get())) {
                // Step 3: Delegate to the executor
                return executor.execute(conn);
            }

        } catch (SQLException e) {
            LoggingUtils.logStructuredError(
                DATABASE_EXECUTION, 
                "execute", 
                FAILED, 
                "Database operation failed:  " + e.getMessage(), 
                e);
            return ExecutionResult.failure(
                1, "[ERROR] Database operation failed: " + e.getMessage());
                
        } catch (RuntimeException e) {
            return ExceptionUtils.handleExecutionException(e, DATABASE_EXECUTION, "execute");
        }
    }

    /**
     * Resolves database password using the configured password resolver.
     * 
     * @param request database request containing authentication parameters
     * @return optional password if resolution succeeds, empty otherwise
     */
    private Optional<String> resolvePassword(final DbRequest request) {
        try {
            final PasswordRequest passwordRequest = new PasswordRequest(
                request.user(),
                request.database(),
                request.vaultConfig().vaultUrl(),
                request.vaultConfig().roleId(),
                request.vaultConfig().secretId(),
                request.vaultConfig().ait());

            return passwordResolver.resolvePassword(passwordRequest);

        } catch (IllegalArgumentException e) {
            LoggingUtils.logStructuredError(
                PASSWORD_RESOLUTION,
                "validation",
                "INVALID_PARAMETERS",
                "Invalid password request parameters",
                e);
            throw e;
        }
    }

    /**
     * Creates a database connection using request parameters.
     * Delegates to DatabaseConnectionManager for centralized connection handling.
     * 
     * @param request  database request containing connection parameters
     * @param password resolved password for authentication
     * @return database connection
     * @throws SQLException if connection creation fails
     */
    private Connection createConnection(final DbRequest request, final String password)
            throws SQLException {
        return DatabaseConnectionManager.createConnection(
            request.type(), 
            request.database(), 
            request.user(), 
            password);
    }

    /**
     * Functional interface for database operations that execute with a connection.
     * This allows different services to provide their own execution logic without
     * inheritance.
     */
    @FunctionalInterface
    public interface ConnectionExecutor {
        /**
         * Executes a database operation with the provided connection.
         * 
         * @param connection the database connection to use
         * @return execution result
         * @throws SQLException if the operation fails
         */
        ExecutionResult execute(Connection connection) throws SQLException;
    }
}