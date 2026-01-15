#!/bin/bash

# Exit on first failure
set -e

# Database connection settings
DB_PASSWORD="hr_password"
DB_USER="hr"
DB_HOST="localhost:1521:xe"
DB_TYPE="oracle"

# Project paths - find script location dynamically
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "${SCRIPT_DIR}"
PROJECT_DIR="${SCRIPT_DIR}"
DIST_DIR="${PROJECT_DIR}/package-helper/target/dist/cliutil-1.0.0"
DOCKER_DIR="${PROJECT_DIR}/docker"
DOCKER_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"

# Function to verify docker-compose.yml exists
verify_docker_compose() {
	if [[ ! -f ${DOCKER_COMPOSE_FILE} ]]; then
		echo "✗ ERROR: docker-compose.yml not found at ${DOCKER_COMPOSE_FILE}"
		exit 1
	fi
}

# Function to refresh Oracle database
refresh_oracle() {
	echo "========================================"
	echo "Refreshing Oracle Database"
	echo "========================================"

	verify_docker_compose

	cd "$DOCKER_DIR" || exit 1

	echo "Removing containers and volumes..."
	if ! docker compose down -v; then
		echo "✗ Docker down failed"
		exit 1
	fi

	echo "Starting fresh Oracle instance..."
	if ! docker compose up -d; then
		echo "✗ Docker up failed"
		exit 1
	fi

	cd "$PROJECT_DIR" || exit 1

	echo "✓ Oracle refreshed successfully"
	echo "Waiting 40 seconds for Oracle initialization..."
	sleep 40
	echo ""
}

# Function to find and set Java for compilation
setup_java_for_build() {
	echo "Setting up Java for Maven build..."

	# Check for Java 21 in SDKMAN
	if [[ -d "$HOME/.sdkman/candidates/java/21-tem" ]]; then
		export JAVA_HOME="$HOME/.sdkman/candidates/java/21-tem"
		export PATH="$JAVA_HOME/bin:$PATH"
		echo "✓ Using Java 21 from SDKMAN: $JAVA_HOME"
		return 0
	fi

	# Check for Java 21 in PATH
	if command -v java &>/dev/null; then
		local java_version
		java_version=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
		if [[ $java_version == "21" ]]; then
			local java_path
			java_path=$(command -v java)
			JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$java_path")")")"
			export JAVA_HOME
			echo "✓ Using Java 21 from PATH: $JAVA_HOME"
			return 0
		fi
	fi

	echo "✗ ERROR: Java 21 not found. Please install Java 21."
	exit 1
}

# Function to build and package the project
build_project() {
	echo "========================================"
	echo "Building Project"
	echo "========================================"

	cd "$PROJECT_DIR" || exit 1

	echo "Running: mvn clean package -DskipTests -pl package-helper -am -Pbundle-jre"
	if ! mvn clean package -DskipTests -pl package-helper -am -Pbundle-jre; then
		echo "✗ Build failed"
		exit 1
	fi

	echo "✓ Build successful"
	echo ""
}

# Function to unzip distribution
unzip_distribution() {
	echo "========================================"
	echo "Extracting Distribution"
	echo "========================================"

	cd "$PROJECT_DIR" || exit 1

	# Prefer the dist zip produced by package-helper assembly
	ZIP_PATH="${PROJECT_DIR}/package-helper/target/dist/cliutil-1.0.0.zip"
	ALT_ZIP_PATH="${PROJECT_DIR}/package-helper/target/cliutil-1.0.0.zip"

	if [[ -f ${ZIP_PATH} ]]; then
		echo "Unzipping: ${ZIP_PATH}"
		ZIP_TO_USE="${ZIP_PATH}"
	elif [[ -f ${ALT_ZIP_PATH} ]]; then
		echo "Unzipping: ${ALT_ZIP_PATH}"
		ZIP_TO_USE="${ALT_ZIP_PATH}"
	else
		echo "✗ ERROR: build artifact zip not found (looked for ${ZIP_PATH} and ${ALT_ZIP_PATH})"
		exit 1
	fi

	OUTPUT_DIR="${PROJECT_DIR}/package-helper/target/dist"
	mkdir -p "${OUTPUT_DIR}"

	if ! unzip -o "${ZIP_TO_USE}" -d "${OUTPUT_DIR}"; then
		echo "✗ Unzip failed"
		exit 1
	fi

	echo "✓ Distribution extracted"
	echo ""
}

# Function to find Java runtime
find_java() {
	set -e
	# First, try bundled JRE
	if [[ -f "./jre/bin/java" ]]; then
		echo "./jre/bin/java"
		return 0
	fi

	# Check for Java 21 in PATH
	if command -v java &>/dev/null; then
		local java_version
		java_version=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
		if [[ $java_version == "21" ]]; then
			echo "java"
			return 0
		fi
	fi

	echo "✗ ERROR: Java runtime not found. Neither bundled JRE nor Java 21 in PATH."
	exit 1
}

# Helper function to run commands
run_command() {
	local test_name=$1
	shift

	echo "----------------------------------------"
	echo "Test: $test_name"
	echo "----------------------------------------"

	# Build arg array safely to preserve quoting and avoid globbing
	local cli_args=("$@" --type "$DB_TYPE" --database "$DB_HOST" --user "$DB_USER")

	if [[ -n $DB_PASSWORD ]]; then
		if printf "%s\n" "$DB_PASSWORD" | "$JAVA_CMD" \
			-Dlog4j.configurationFile=file:./log4j2.xml \
			-Dvault.config=./vaults.yaml \
			-jar ./cliutil-1.0.0.jar \
			"${cli_args[@]}"; then
			echo "✓ PASSED"
		else
			echo "✗ FAILED"
			exit 1
		fi
	else
		if "$JAVA_CMD" \
			-Dlog4j.configurationFile=file:./log4j2.xml \
			-Dvault.config=./vaults.yaml \
			-jar ./cliutil-1.0.0.jar \
			"${cli_args[@]}"; then
			echo "✓ PASSED"
		else
			echo "✗ FAILED"
			exit 1
		fi
	fi
	echo ""
}

# ========================================
# Main Execution
# ========================================

# Check for --refresh flag
REFRESH_DB=false
if [[ $1 == "--refresh" ]]; then
	REFRESH_DB=true
fi

# Verify docker-compose.yml exists
verify_docker_compose

# Optionally refresh Oracle
if [[ ${REFRESH_DB} == true ]]; then
	refresh_oracle
fi

# Step 1: Setup Java for build
setup_java_for_build

# Step 2: Build project
build_project

# Step 3: Unzip distribution
unzip_distribution

# Step 4: Change to distribution directory
cd "$DIST_DIR" || exit 1

# Step 5: Find Java runtime
JAVA_CMD=$(find_java)
echo "Using Java: ${JAVA_CMD}"
echo ""

echo "========================================"
echo "Running CLI Tests"
echo "========================================"
echo ""

# 1. Execute basic SQL query
run_command "Execute SQL - List Employees" \
	exec-sql "SELECT * FROM hr.employees WHERE rownum <= 5"

# 2. Get employee salary (SQL function)
run_command "Get Employee Salary" \
	exec-sql "SELECT hr.hr_pkg.get_employee_salary(100) as salary FROM dual"

# 3. Get department budget (SQL function)
run_command "Get Department Budget" \
	exec-sql "SELECT hr.hr_pkg.get_department_budget(80) as budget FROM dual"

# 4. Calculate bonus (SQL function)
run_command "Calculate Bonus" \
	exec-sql "SELECT hr.calculate_bonus(10000, 15) as bonus FROM dual"

# 5. Get employee details (procedure)
run_command "Get Employee Details" \
	exec-proc hr.get_employee_details \
	--input "p_employee_id:NUMBER:100" \
	--output "o_first_name:VARCHAR2,o_last_name:VARCHAR2,o_email:VARCHAR2,o_salary:NUMBER,o_job_id:VARCHAR2"

# 6. Get department info (procedure)
run_command "Get Department Info" \
	exec-proc hr.get_department_info \
	--input "p_department_id:NUMBER:80" \
	--output "o_department_name:VARCHAR2,o_manager_id:NUMBER,o_employee_count:NUMBER,o_total_salary:NUMBER"

# 7. Raise employee salary (package procedure)
run_command "Raise Employee Salary" \
	exec-proc hr.hr_pkg.raise_employee_salary --input "p_employee_id:NUMBER:100,p_raise_percent:NUMBER:10"

# 8. Hire new employee (package procedure)
run_command "Hire New Employee" \
	exec-proc hr.hr_pkg.hire_employee --input "p_first_name:VARCHAR2:John,p_last_name:VARCHAR2:Doe,p_email:VARCHAR2:jdoe@example.com,p_job_id:VARCHAR2:IT_PROG,p_salary:NUMBER:8000,p_department_id:NUMBER:60"

# 9. Update job history (package procedure)
run_command "Update Job History" \
	exec-proc hr.hr_pkg.update_job_history --input "p_employee_id:NUMBER:100,p_new_job_id:VARCHAR2:AD_VP,p_new_department_id:NUMBER:90"

echo "========================================"
echo "All tests completed"
echo "========================================"
