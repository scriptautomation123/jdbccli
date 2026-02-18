-- Oracle schema for integration testing
CREATE SEQUENCE employees_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE departments_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE employees (
  id NUMBER PRIMARY KEY,
  first_name VARCHAR2(50) NOT NULL,
  last_name VARCHAR2(50) NOT NULL,
  email VARCHAR2(100) UNIQUE NOT NULL,
  department VARCHAR2(50),
  salary NUMBER(10, 2),
  hire_date DATE,
  is_active CHAR(1) DEFAULT 'Y'
);

CREATE TABLE departments (
  dept_id NUMBER PRIMARY KEY,
  dept_name VARCHAR2(50) NOT NULL,
  location VARCHAR2(100)
);

CREATE TRIGGER employees_bi
BEFORE INSERT ON employees
FOR EACH ROW
BEGIN
  SELECT employees_seq.NEXTVAL INTO :new.id FROM DUAL;
END;
/

CREATE TRIGGER departments_bi
BEFORE INSERT ON departments
FOR EACH ROW
BEGIN
  SELECT departments_seq.NEXTVAL INTO :new.dept_id FROM DUAL;
END;
/

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date, is_active) VALUES
('Alice', 'Smith', 'alice@example.com', 'Engineering', 95000.00, TO_DATE('2020-01-15', 'YYYY-MM-DD'), 'Y');

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date, is_active) VALUES
('Bob', 'Jones', 'bob@example.com', 'Sales', 75000.00, TO_DATE('2021-03-20', 'YYYY-MM-DD'), 'Y');

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date, is_active) VALUES
('Charlie', 'Brown', 'charlie@example.com', 'Engineering', 110000.00, TO_DATE('2019-11-10', 'YYYY-MM-DD'), 'Y');

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date, is_active) VALUES
('Diana', 'Prince', 'diana@example.com', 'HR', 82000.00, TO_DATE('2022-05-01', 'YYYY-MM-DD'), 'N');

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date, is_active) VALUES
('Eve', 'Davis', 'eve@example.com', 'Engineering', 105000.00, TO_DATE('2020-08-15', 'YYYY-MM-DD'), 'Y');

INSERT INTO departments (dept_name, location) VALUES
('Engineering', 'Building A');

INSERT INTO departments (dept_name, location) VALUES
('Sales', 'Building B');

INSERT INTO departments (dept_name, location) VALUES
('HR', 'Building C');

COMMIT;
