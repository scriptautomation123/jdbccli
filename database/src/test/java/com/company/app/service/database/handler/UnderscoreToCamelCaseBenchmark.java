package com.company.app.service.database.handler;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.company.app.service.database.typedapi.UnderscoreToCamelCase;

/**
 * JMH Benchmark comparing optimized string conversion against naive approaches.
 *
 * <p>Demonstrates the performance difference between:
 *
 * <ul>
 *   <li>Optimized: Single-pass char array manipulation
 *   <li>Naive: Regex + StringBuilder
 *   <li>Naive: String.split() + StringBuilder
 * </ul>
 *
 * <p><strong>Run with:</strong>
 *
 * <pre>
 * mvn clean test-compile exec:java \
 *   -Dexec.mainClass=org.openjdk.jmh.Main \
 *   -Dexec.classpathScope=test \
 *   -Dexec.args="UnderscoreToCamelCaseBenchmark -f 1"
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class UnderscoreToCamelCaseBenchmark {

  private static final String[] TEST_STRINGS = {
    "user_name",
    "first_name",
    "last_name",
    "created_at",
    "updated_at",
    "is_active",
    "USER_ID",
    "FIRST_NAME_LAST",
    "email_address_primary"
  };

  /** Baseline: Naive approach using regex. This is what many developers write first. */
  @Benchmark
  public void baselineRegexBased(Blackhole bh) {
    for (String s : TEST_STRINGS) {
      bh.consume(naiveRegexConvert(s));
    }
  }

  /** Alternative naive: Using String.split() and StringBuilder. */
  @Benchmark
  public void naiveSplitBased(Blackhole bh) {
    for (String s : TEST_STRINGS) {
      bh.consume(naiveSplitConvert(s));
    }
  }

  /** Optimized: Current implementation using single-pass char array. */
  @Benchmark
  public void optimizedCharArray(Blackhole bh) {
    for (String s : TEST_STRINGS) {
      bh.consume(UnderscoreToCamelCase.convert(s));
    }
  }

  // ==================== Naive Implementations ====================

  /** Naive conversion using regex (very slow). */
  private static String naiveRegexConvert(String input) {
    if (input == null || input.isEmpty()) {
      return "";
    }

    // Multiple passes over the string
    String result = input.toLowerCase(java.util.Locale.ENGLISH);

    // Regex compilation on every call!
    String[] parts = result.split("_");

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i == 0) {
        sb.append(parts[i]);
      } else if (!parts[i].isEmpty()) {
        sb.append(Character.toUpperCase(parts[i].charAt(0)));
        sb.append(parts[i].substring(1));
      }
    }

    return sb.toString();
  }

  /**
   * Naive conversion using String.split() and StringBuilder. Better than regex but still does
   * multiple passes.
   */
  private static String naiveSplitConvert(String input) {
    if (input == null || input.isEmpty()) {
      return "";
    }

    String[] parts = input.toLowerCase(java.util.Locale.ENGLISH).split("_");
    StringBuilder sb = new StringBuilder(input.length());

    for (int i = 0; i < parts.length; i++) {
      if (parts[i].isEmpty()) {
        continue;
      }

      if (i == 0) {
        sb.append(parts[i]);
      } else {
        sb.append(Character.toUpperCase(parts[i].charAt(0)));
        if (parts[i].length() > 1) {
          sb.append(parts[i].substring(1));
        }
      }
    }

    return sb.toString();
  }
}
