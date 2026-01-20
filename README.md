# run all tests

```bash
cd docker && docker compose down -v && docker compose up -d && cd ..
./run_all_tests.sh
```
## Generate aggregate SBOM for all modules
mvn cyclonedx:makeAggregateBom

## Or generate SBOM for individual modules
mvn cyclonedx:makeBom

## 0. Execute basic SQL query

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-sql "SELECT * FROM hr.employees WHERE rownum <= 5" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 1. Get employee salary (use SQL, not procedure)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-sql "SELECT hr.hr_pkg.get_employee_salary(100) as salary FROM dual" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 2. Get department budget (use SQL)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-sql "SELECT hr.hr_pkg.get_department_budget(80) as budget FROM dual" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 3. Calculate bonus (use SQL)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-sql "SELECT hr.calculate_bonus(10000, 15) as bonus FROM dual" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 4. Get employee details (procedure with input parameter)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-proc hr.get_employee_details \
--input "p_employee_id:NUMBER:100" \
--output "o_first_name:VARCHAR2,o_last_name:VARCHAR2,o_email:VARCHAR2,o_salary:NUMBER,o_job_id:VARCHAR2" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 5. Get department info (procedure with input parameter)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-proc hr.get_department_info \
--input "p_department_id:NUMBER:80" \
--output "o_department_name:VARCHAR2,o_manager_id:NUMBER,o_employee_count:NUMBER,o_total_salary:NUMBER" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 6. Raise employee salary (package procedure with multiple inputs)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.raise_employee_salary \
--input "p_employee_id:NUMBER:100,p_raise_percent:NUMBER:10" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 7. Hire new employee (package procedure with 6 input parameters)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.hire_employee \
--input "p_first_name:VARCHAR2:John,p_last_name:VARCHAR2:Doe,p_email:VARCHAR2:jdoe@example.com,p_job_id:VARCHAR2:IT_PROG,p_salary:NUMBER:8000,p_department_id:NUMBER:60" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 8. Update job history (package procedure with 3 input parameters)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.update_job_history \
--input "p_employee_id:NUMBER:100,p_new_job_id:VARCHAR2:AD_VP,p_new_department_id:NUMBER:90" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 9. Terminate employee (package procedure with 1 input parameter)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.terminate_employee \
--input "p_employee_id:NUMBER:100" \
--type oracle \
--database localhost:1521:xe \
--user hr
```
