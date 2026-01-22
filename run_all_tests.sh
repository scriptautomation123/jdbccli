#!/bin/bash

# Exit on first failure
set -e

# Enable inherit_errexit for command substitutions
shopt -s inherit_errexit

# Database connection settings
DB_PASSWORD="hr_password"
DB_USER="hr"
DB_HOST="localhost:1521:xe"
DB_TYPE="oracle"

# Project paths - find script location dynamically
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}"
DIST_DIR="${PROJECT_DIR}/package-helper/target/dist/cli-1.0.0"
DOCKER_DIR="${PROJECT_DIR}/docker"
DOCKER_COMPOSE_FILE="${DOCKER_DIR}/docker-compose.yml"

# ========================================
# Utility Functions
# ========================================

# Function to run spotless formatting and commit
run_spotless() {
	echo "========================================"
	echo "Running Spotless Format & Check"
	echo "========================================"

	cd "${PROJECT_DIR}" || exit 1

	echo "Applying spotless formatting..."
	if ! mvn spotless:apply; then
		echo "✗ Spotless apply failed"
		return 1
	fi

	echo "Checking spotless compliance..."
	if ! mvn spotless:check; then
		echo "✗ Spotless check failed"
		return 1
	fi

	echo "✓ Spotless completed successfully"
	echo ""

	# Check if there are changes to commit
	if [[ -n $(git status --porcelain) ]]; then
		echo "Staging all changes..."
		git add -A

		read -r -p "Enter commit message (or press Enter for default): " commit_msg
		if [[ -z ${commit_msg} ]]; then
			commit_msg="style: apply spotless formatting"
		fi

		git commit -m "${commit_msg}"
		echo "✓ Changes committed"

		read -r -p "Push to remote? (y/N): " push_confirm
		if [[ ${push_confirm} =~ ^[Yy]$ ]]; then
			local branch_name
			branch_name=$(git rev-parse --abbrev-ref HEAD)
			git push -u origin "${branch_name}"
			echo "✓ Pushed to origin/${branch_name}"
		fi
	else
		echo "No changes to commit"
	fi
	echo ""
}

# Function to generate SBOM and run report
generate_sbom_report() {
	echo "========================================"
	echo "Generating SBOM Report"
	echo "========================================"

	cd "${PROJECT_DIR}" || exit 1

	echo "Generating aggregate SBOM..."
	if ! mvn cyclonedx:makeAggregateBom -q; then
		echo "✗ SBOM generation failed"
		return 1
	fi
	echo "✓ SBOM generated at target/sbom.xml"
	echo ""

	echo "Compiling util module..."
	if ! mvn compile -pl util -q; then
		echo "✗ Util module compilation failed"
		return 1
	fi

	echo ""
	echo "Running SBOM Report Generator..."
	echo ""

	java -cp util/target/classes \
		com.company.app.service.util.SbomReportGenerator target/sbom.xml

	echo ""
	echo "✓ SBOM report completed"
}

# Function to migrate package paths (copy, not move)
migrate_package() {
	local old_pkg="${1:-com.company.app}"
	local new_pkg="${2:-}"

	if [[ -z ${new_pkg} ]]; then
		echo "========================================"
		echo "Package Migration Tool"
		echo "========================================"
		echo ""
		read -r -p "Enter OLD package path [${old_pkg}]: " input_old
		old_pkg="${input_old:-${old_pkg}}"
		read -r -p "Enter NEW package path: " new_pkg
		if [[ -z ${new_pkg} ]]; then
			echo "✗ New package path is required"
			return 1
		fi
	fi

	cd "${PROJECT_DIR}" || exit 1

	# Convert package to path (com.company.app -> com/company/app)
	local old_path="${old_pkg//./\/}"
	local new_path="${new_pkg//./\/}"

	echo ""
	echo "Migration: ${old_pkg} → ${new_pkg}"
	echo "Path:      ${old_path} → ${new_path}"
	echo ""

	# Find all modules with src/main/java
	local modules=()
	while IFS= read -r -d '' dir; do
		modules+=("${dir}")
	done < <(find "${PROJECT_DIR}" -type d -name "java" -path "*/src/main/*" -print0 2>/dev/null)

	# Also find test directories
	while IFS= read -r -d '' dir; do
		modules+=("${dir}")
	done < <(find "${PROJECT_DIR}" -type d -name "java" -path "*/src/test/*" -print0 2>/dev/null)

	if [[ ${#modules[@]} -eq 0 ]]; then
		echo "✗ No Java source directories found"
		return 1
	fi

	echo "Found ${#modules[@]} source directories"
	echo ""

	# Track files for summary
	local copied_files=()
	local old_paths_to_delete=()

	# Step 1: Create new package structure and copy files
	echo "Step 1: Creating new package structure and copying files..."
	echo "────────────────────────────────────────────────────────────"

	for java_root in "${modules[@]}"; do
		local old_full_path="${java_root}/${old_path}"

		if [[ -d ${old_full_path} ]]; then
			local new_full_path="${java_root}/${new_path}"

			# Find all Java files under old path
			while IFS= read -r -d '' java_file; do
				# Calculate relative path from old package root
				local rel_path="${java_file#${old_full_path}/}"
				local new_file="${new_full_path}/${rel_path}"
				local new_dir
				new_dir=$(dirname "${new_file}")

				# Create directory and copy
				mkdir -p "${new_dir}"
				cp "${java_file}" "${new_file}"

				copied_files+=("${new_file}")
				old_paths_to_delete+=("${java_file}")

				echo "  ✓ $(basename "${java_file}")"
			done < <(find "${old_full_path}" -name "*.java" -type f -print0 2>/dev/null)
		fi
	done

	echo ""
	echo "Copied ${#copied_files[@]} files"
	echo ""

	# Step 2: Fix package declarations and imports in NEW files
	echo "Step 2: Fixing package declarations and imports..."
	echo "────────────────────────────────────────────────────────────"

	for file in "${copied_files[@]}"; do
		# Fix package declaration
		sed -i "s/package ${old_pkg}/package ${new_pkg}/g" "${file}"

		# Fix imports
		sed -i "s/import ${old_pkg}/import ${new_pkg}/g" "${file}"

		echo "  ✓ $(basename "${file}")"
	done

	echo ""

	# Step 3: Also update imports in original files (so both versions compile)
	echo "Step 3: Summary"
	echo "────────────────────────────────────────────────────────────"
	echo ""
	echo "✓ New package structure created at: ${new_path}"
	echo "✓ ${#copied_files[@]} files copied and updated"
	echo ""

	# List old paths to delete
	if [[ ${#old_paths_to_delete[@]} -gt 0 ]]; then
		echo "┌─────────────────────────────────────────────────────────────────┐"
		echo "│ OLD PATHS TO DELETE (after verification)                        │"
		echo "├─────────────────────────────────────────────────────────────────┤"

		# Group by directory for cleaner output
		local prev_dir=""
		for old_file in "${old_paths_to_delete[@]}"; do
			local dir
			dir=$(dirname "${old_file}")
			dir="${dir#${PROJECT_DIR}/}"
			if [[ ${dir} != "${prev_dir}" ]]; then
				echo "│"
				echo "│ 📁 ${dir}"
				prev_dir="${dir}"
			fi
			echo "│    └── $(basename "${old_file}")"
		done

		echo "│"
		echo "└─────────────────────────────────────────────────────────────────┘"
		echo ""

		# Generate delete command
		echo "To delete old files after verification, run:"
		echo ""
		echo "  # Delete old package directories:"
		for java_root in "${modules[@]}"; do
			local old_full_path="${java_root}/${old_path}"
			if [[ -d ${old_full_path} ]]; then
				local rel_root="${java_root#${PROJECT_DIR}/}"
				echo "  rm -rf ${rel_root}/${old_path}"
			fi
		done
		echo ""
	fi

	echo "⚠️  Remember to:"
	echo "   1. Update pom.xml if it references the old package"
	echo "   2. Update any configuration files (application.yaml, log4j2.xml)"
	echo "   3. Run 'mvn compile' to verify"
	echo "   4. Run tests before deleting old files"
	echo ""
}

# Function to display interactive menu
show_menu() {
	echo ""
	echo "╔══════════════════════════════════════════════════════════════════╗"
	echo "║                    JDBC CLI - Development Menu                   ║"
	echo "╚══════════════════════════════════════════════════════════════════╝"
	echo ""
	echo "  1) Run all tests           - Execute full test suite"
	echo "  2) Run spotless            - Format code & commit"
	echo "  3) Generate SBOM report    - Dependency analysis"
	echo "  4) Build project           - Maven package"
	echo "  5) Refresh Oracle DB       - Reset Docker container"
	echo "  6) Full pipeline           - Build + Test"
	echo "  7) Migrate package         - Copy classes to new package path"
	echo "  q) Quit"
	echo ""
	echo -n "Select option: "
}

# Function to run interactive mode
run_interactive() {
	while true; do
		show_menu
		read -r choice

		case ${choice} in
		1)
			run_all_tests
			;;
		2)
			run_spotless
			;;
		3)
			generate_sbom_report
			;;
		4)
			setup_java_for_build
			build_project
			;;
		5)
			refresh_oracle
			;;
		6)
			setup_java_for_build
			build_project
			unzip_distribution
			run_all_tests
			;;
		7)
			migrate_package
			;;
		q | Q)
			echo "Goodbye!"
			exit 0
			;;
		*)
			echo "Invalid option: ${choice}"
			;;
		esac

		echo ""
		read -r -p "Press Enter to continue..."
	done
}

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

	cd "${DOCKER_DIR}" || exit 1

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

	cd "${PROJECT_DIR}" || exit 1

	echo "✓ Oracle refreshed successfully"
	echo "Waiting 40 seconds for Oracle initialization..."
	sleep 40
	echo ""
}

# Function to find and set Java for compilation
setup_java_for_build() {
	echo "Setting up Java for Maven build..."

	# Check for Java 21 in SDKMAN
	if [[ -d "${HOME}/.sdkman/candidates/java/21-tem" ]]; then
		export JAVA_HOME="${HOME}/.sdkman/candidates/java/21-tem"
		export PATH="${JAVA_HOME}/bin:${PATH}"
		echo "✓ Using Java 21 from SDKMAN: ${JAVA_HOME}"
		return 0
	fi

	# Check for Java 21 in PATH
	if command -v java &>/dev/null; then
		local java_version_output
		local java_version_line
		local java_version_full
		local java_version
		java_version_output=$(java -version 2>&1) || true
		java_version_line=$(printf '%s' "${java_version_output}" | head -n 1) || true
		java_version_full=$(printf '%s' "${java_version_line}" | awk -F '"' '{print $2}') || true
		java_version=$(printf '%s' "${java_version_full}" | cut -d'.' -f1) || true
		if [[ ${java_version} == "21" ]]; then
			local java_path
			local java_real_path
			local java_bin_dir
			java_path=$(command -v java)
			java_real_path=$(readlink -f "${java_path}")
			java_bin_dir=$(dirname "${java_real_path}")
			JAVA_HOME=$(dirname "${java_bin_dir}")
			export JAVA_HOME
			echo "✓ Using Java 21 from PATH: ${JAVA_HOME}"
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

	cd "${PROJECT_DIR}" || exit 1

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

	cd "${PROJECT_DIR}" || exit 1

	# Prefer the dist zip produced by package-helper assembly
	ZIP_PATH="${PROJECT_DIR}/package-helper/target/dist/cli-1.0.0.zip"
	ALT_ZIP_PATH="${PROJECT_DIR}/package-helper/target/cli-1.0.0.zip"

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
		local java_version_output
		local java_version_line
		local java_version_full
		local java_version
		java_version_output=$(java -version 2>&1) || true
		java_version_line=$(printf '%s' "${java_version_output}" | head -n 1) || true
		java_version_full=$(printf '%s' "${java_version_line}" | awk -F '"' '{print $2}') || true
		java_version=$(printf '%s' "${java_version_full}" | cut -d'.' -f1) || true
		if [[ ${java_version} == "21" ]]; then
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
	echo "Test: ${test_name}"
	echo "----------------------------------------"

	# Build arg array safely to preserve quoting and avoid globbing
	local cli_args=("$@" --type "${DB_TYPE}" --database "${DB_HOST}" --user "${DB_USER}")

	if [[ -n ${DB_PASSWORD} ]]; then
		if printf "%s\n" "${DB_PASSWORD}" | "${JAVA_CMD}" \
			-Dlog4j.configurationFile=file:./log4j2.xml \
			-Dvault.config=./vaults.yaml \
			-jar ./cli-1.0.0.jar \
			"${cli_args[@]}"; then
			echo "✓ PASSED"
		else
			echo "✗ FAILED"
			exit 1
		fi
	else
		if "${JAVA_CMD}" \
			-Dlog4j.configurationFile=file:./log4j2.xml \
			-Dvault.config=./vaults.yaml \
			-jar ./cli-1.0.0.jar \
			"${cli_args[@]}"; then
			echo "✓ PASSED"
		else
			echo "✗ FAILED"
			exit 1
		fi
	fi
	echo ""
}

# Function to run all tests
run_all_tests() {
	echo "========================================"
	echo "Running CLI Tests"
	echo "========================================"

	cd "${DIST_DIR}" || {
		echo "✗ Distribution not found. Run build first."
		return 1
	}

	# Find Java runtime
	JAVA_CMD=$(find_java)
	echo "Using Java: ${JAVA_CMD}"
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
}

# ========================================
# Main Execution
# ========================================

show_usage() {
	echo "Usage: $0 [OPTION]"
	echo ""
	echo "Options:"
	echo "  --interactive, -i    Interactive menu mode"
	echo "  --spotless           Run spotless format & commit"
	echo "  --sbom               Generate SBOM dependency report"
	echo "  --build              Build project only"
	echo "  --refresh            Refresh Oracle DB before tests"
	echo "  --migrate-pkg OLD NEW  Migrate package (copy to new path)"
	echo "  --help, -h           Show this help"
	echo ""
	echo "Without options: runs full build + test pipeline"
	echo ""
	echo "Examples:"
	echo "  $0 --migrate-pkg com.company.app com.newcompany.newapp"
}

# Parse command line arguments
case "${1:-}" in
--interactive | -i)
	run_interactive
	;;
--spotless)
	run_spotless
	;;
--sbom)
	generate_sbom_report
	;;
--build)
	setup_java_for_build
	build_project
	unzip_distribution
	;;
--migrate-pkg)
	migrate_package "${2:-}" "${3:-}"
	;;
--help | -h)
	show_usage
	exit 0
	;;
--refresh | "")
	# Default behavior: full pipeline
	verify_docker_compose

	if [[ ${1:-} == "--refresh" ]]; then
		refresh_oracle
	fi

	setup_java_for_build
	build_project
	unzip_distribution
	run_all_tests
	;;
*)
	echo "Unknown option: ${1}"
	show_usage
	exit 1
	;;
esac
