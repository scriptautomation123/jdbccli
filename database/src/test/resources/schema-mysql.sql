-- MySQL schema for integration testing
CREATE TABLE employees (
  id INT AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  department VARCHAR(50),
  salary DECIMAL(10, 2),
  hire_date DATE,
  is_active TINYINT(1) DEFAULT TRUE
);

CREATE TABLE departments (
  dept_id INT AUTO_INCREMENT PRIMARY KEY,
  dept_name VARCHAR(50) NOT NULL,
  location VARCHAR(100)
);

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date, is_active) VALUES
('Alice', 'Smith', 'alice@example.com', 'Engineering', 95000.00, '2020-01-15', 1),
('Bob', 'Jones', 'bob@example.com', 'Sales', 75000.00, '2021-03-20', 1),
('Charlie', 'Brown', 'charlie@example.com', 'Engineering', 110000.00, '2019-11-10', 1),
('Diana', 'Prince', 'diana@example.com', 'HR', 82000.00, '2022-05-01', 0),
('Eve', 'Davis', 'eve@example.com', 'Engineering', 105000.00, '2020-08-15', 1);

INSERT INTO departments (dept_name, location) VALUES
('Engineering', 'Building A'),
('Sales', 'Building B'),
('HR', 'Building C');
