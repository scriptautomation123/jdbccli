#!/bin/bash

set -e

BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JRE_DIR="$BUNDLE_DIR/jre"

error_exit() {
    echo "❌ Error: $1" >&2
    exit 1
}

find_jar_file() {
    local dir="$1"
    find "$dir" -maxdepth 1 -name "aieutil-*.jar" 2>/dev/null | head -1
}

setup_java_environment() {
    if [ -d "$JRE_DIR" ] && [ -x "$JRE_DIR/bin/java" ]; then
        export JAVA_HOME="${BUNDLE_DIR}/jre"
        export PATH="${JAVA_HOME}/bin:$PATH"
        echo "✅ Using JRE ${BUNDLE_DIR}/jre"
    fi
    if [ -z "$JAVA_HOME" ]; then
        error_exit "JAVA_HOME is not set"
    fi
    if [ ! -x "$JAVA_HOME/bin/java" ]; then
        error_exit "Java executable not found at $JAVA_HOME/bin/java"
    fi
    if ! java -version > /dev/null 2>&1; then
        error_exit "Java is not accessible in PATH"
    fi
}

setup_java_options() {
    export JAVA_OPTS="-Dfile.encoding=UTF-8"
    if [ -f "$BUNDLE_DIR/log4j2.xml" ]; then
        JAVA_OPTS="$JAVA_OPTS -Dlog4j.configurationFile=$BUNDLE_DIR/log4j2.xml"
    fi
}

validate_jar_file() {
    JAR_FILE="$(find_jar_file "$BUNDLE_DIR")"
    if [ ! -f "$JAR_FILE" ]; then
        echo "Error: No aieutil-*.jar found in bundle directory ${BUNDLE_DIR}"
        exit 1
    fi
}

validate_vault_config() {
    if [ "$1" != "--vault-config" ]; then
        echo "❌ Error: --vault-config /path/to/vault.yaml parameter is required as first argument"
        exit 1
    fi

    local vault_config="$2"
    if [ ! -f "$vault_config" ]; then
        echo "❌ Error: Vault config file not found: $vault_config"
        exit 1
    fi

    JAVA_OPTS="$JAVA_OPTS -Dvault.config=$vault_config"
    shift 2
}

run_jar() {
    exec java $JAVA_OPTS -jar "$JAR_FILE" "$@"
}

main() {
    validate_jar_file
    setup_java_environment
    setup_java_options
    validate_vault_config "$@"
    run_jar "$@"
}

main "$@"
