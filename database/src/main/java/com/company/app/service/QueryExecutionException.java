package com.company.app.service;

public class QueryExecutionException extends RuntimeException {

  public QueryExecutionException(final String message) {
    super(message);
  }

  public QueryExecutionException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
