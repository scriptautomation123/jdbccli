-- HR Schema Creation Script for Oracle Database
-- This script creates a complete HR schema with tables, constraints, and sample data

-- Create the HR user/schema
CREATE USER hr IDENTIFIED BY hr_password;

-- Grant necessary privileges
GRANT CREATE SESSION TO hr;
GRANT CREATE TABLE TO hr;
GRANT CREATE SEQUENCE TO hr;
GRANT CREATE PROCEDURE TO hr;
GRANT CREATE VIEW TO hr;
GRANT UNLIMITED TABLESPACE TO hr;

-- Create REGIONS table
CREATE TABLE hr.regions (
    region_id NUMBER PRIMARY KEY,
    region_name VARCHAR2(25)
);

-- Create COUNTRIES table
CREATE TABLE hr.countries (
    country_id CHAR(2) PRIMARY KEY,
    country_name VARCHAR2(40),
    region_id NUMBER,
    CONSTRAINT fk_countries_regions FOREIGN KEY (region_id) REFERENCES hr.regions(region_id)
);

-- Create LOCATIONS table
CREATE TABLE hr.locations (
    location_id NUMBER PRIMARY KEY,
    street_address VARCHAR2(40),
    postal_code VARCHAR2(12),
    city VARCHAR2(30) NOT NULL,
    state_province VARCHAR2(25),
    country_id CHAR(2),
    CONSTRAINT fk_locations_countries FOREIGN KEY (country_id) REFERENCES hr.countries(country_id)
);

-- Create DEPARTMENTS table
CREATE TABLE hr.departments (
    department_id NUMBER PRIMARY KEY,
    department_name VARCHAR2(30) NOT NULL,
    manager_id NUMBER,
    location_id NUMBER,
    CONSTRAINT fk_departments_locations FOREIGN KEY (location_id) REFERENCES hr.locations(location_id)
);

-- Create JOBS table
CREATE TABLE hr.jobs (
    job_id VARCHAR2(10) PRIMARY KEY,
    job_title VARCHAR2(35) NOT NULL,
    min_salary NUMBER(6),
    max_salary NUMBER(6)
);

-- Create EMPLOYEES table
CREATE TABLE hr.employees (
    employee_id NUMBER PRIMARY KEY,
    first_name VARCHAR2(20),
    last_name VARCHAR2(25) NOT NULL,
    email VARCHAR2(25) NOT NULL,
    phone_number VARCHAR2(20),
    hire_date DATE NOT NULL,
    job_id VARCHAR2(10) NOT NULL,
    salary NUMBER(8,2),
    commission_pct NUMBER(2,2),
    manager_id NUMBER,
    department_id NUMBER,
    CONSTRAINT fk_employees_manager FOREIGN KEY (manager_id) REFERENCES hr.employees(employee_id),
    CONSTRAINT fk_employees_departments FOREIGN KEY (department_id) REFERENCES hr.departments(department_id),
    CONSTRAINT fk_employees_jobs FOREIGN KEY (job_id) REFERENCES hr.jobs(job_id),
    CONSTRAINT check_salary CHECK (salary > 0),
    CONSTRAINT check_commission CHECK (commission_pct >= 0 AND commission_pct <= 1)
);

-- Create JOB_HISTORY table
CREATE TABLE hr.job_history (
    employee_id NUMBER,
    start_date DATE,
    end_date DATE,
    job_id VARCHAR2(10),
    department_id NUMBER,
    PRIMARY KEY (employee_id, start_date),
    CONSTRAINT fk_job_history_employees FOREIGN KEY (employee_id) REFERENCES hr.employees(employee_id),
    CONSTRAINT fk_job_history_jobs FOREIGN KEY (job_id) REFERENCES hr.jobs(job_id),
    CONSTRAINT fk_job_history_departments FOREIGN KEY (department_id) REFERENCES hr.departments(department_id)
);

-- Create indexes
CREATE INDEX idx_employees_manager_id ON hr.employees(manager_id);
CREATE INDEX idx_employees_department_id ON hr.employees(department_id);
CREATE INDEX idx_employees_job_id ON hr.employees(job_id);
CREATE INDEX idx_departments_manager_id ON hr.departments(manager_id);
CREATE INDEX idx_job_history_employee_id ON hr.job_history(employee_id);

-- Insert sample data into REGIONS
INSERT INTO hr.regions VALUES (1, 'Europe');
INSERT INTO hr.regions VALUES (2, 'Americas');
INSERT INTO hr.regions VALUES (3, 'Asia');
INSERT INTO hr.regions VALUES (4, 'Middle East and Africa');

-- Insert sample data into COUNTRIES
INSERT INTO hr.countries VALUES ('US', 'United States', 2);
INSERT INTO hr.countries VALUES ('UK', 'United Kingdom', 1);
INSERT INTO hr.countries VALUES ('CA', 'Canada', 2);
INSERT INTO hr.countries VALUES ('AU', 'Australia', 3);
INSERT INTO hr.countries VALUES ('JP', 'Japan', 3);
INSERT INTO hr.countries VALUES ('DE', 'Germany', 1);
INSERT INTO hr.countries VALUES ('IT', 'Italy', 1);
INSERT INTO hr.countries VALUES ('BR', 'Brazil', 2);
INSERT INTO hr.countries VALUES ('HK', 'Hong Kong', 3);
INSERT INTO hr.countries VALUES ('MX', 'Mexico', 2);

-- Insert sample data into LOCATIONS
INSERT INTO hr.locations VALUES (1000, '1297 Via Cola di Bie', '00989', 'Roma', NULL, 'IT');
INSERT INTO hr.locations VALUES (1100, '93091 Calle della Testa', '10934', 'Venice', NULL, 'IT');
INSERT INTO hr.locations VALUES (1200, '2017 Shinn Street', '98109', 'Seattle', 'WA', 'US');
INSERT INTO hr.locations VALUES (1300, '2004 Charade Road', '98199', 'Seattle', 'WA', 'US');
INSERT INTO hr.locations VALUES (1400, '8204 Arthur St', NULL, 'London', NULL, 'UK');
INSERT INTO hr.locations VALUES (1500, 'Magdalen Centre, The Oxford Science Park', 'OX9 9ZB', 'Oxford', 'OX9', 'UK');
INSERT INTO hr.locations VALUES (1600, '9702 Chester Road', '09629850293', 'Stretford', NULL, 'UK');
INSERT INTO hr.locations VALUES (1700, 'Schwanthalerstr. 7031', '80925', 'Munich', 'BY', 'DE');
INSERT INTO hr.locations VALUES (1800, 'Rua Frei Miguelistas 554', '06107-020', 'Sao Paulo', 'SP', 'BR');
INSERT INTO hr.locations VALUES (1900, 'Q5 10A Admiralty', NULL, 'Hong Kong', NULL, 'HK');
INSERT INTO hr.locations VALUES (2000, '12-98 Victoria Street', '2901', 'Sydney', 'NSW', 'AU');
INSERT INTO hr.locations VALUES (2100, '180 Piccadilly', 'M1 1AD', 'Manchester', NULL, 'UK');
INSERT INTO hr.locations VALUES (2200, 'Mariano Escobedo 9991', '11932', 'Mexico City', 'Distrito Federal', 'MX');
INSERT INTO hr.locations VALUES (2400, '1st Street', '10044', 'New York', 'NY', 'US');
INSERT INTO hr.locations VALUES (2500, 'Fountain Court', '4010', 'Sydney', 'NSW', 'AU');
INSERT INTO hr.locations VALUES (2700, 'Tech Parkway', NULL, 'San Francisco', 'CA', 'US');

-- Insert sample data into DEPARTMENTS
INSERT INTO hr.departments VALUES (10, 'Administration', 200, 1700);
INSERT INTO hr.departments VALUES (20, 'Marketing', 201, 1800);
INSERT INTO hr.departments VALUES (30, 'Purchasing', 114, 1700);
INSERT INTO hr.departments VALUES (40, 'Human Resources', 203, 2400);
INSERT INTO hr.departments VALUES (50, 'Shipping', 121, 1500);
INSERT INTO hr.departments VALUES (60, 'IT', 103, 1400);
INSERT INTO hr.departments VALUES (70, 'Public Relations', 204, 2700);
INSERT INTO hr.departments VALUES (80, 'Sales', 145, 2500);
INSERT INTO hr.departments VALUES (90, 'Executive', NULL, 1700);
INSERT INTO hr.departments VALUES (100, 'Finance', 108, 1700);
INSERT INTO hr.departments VALUES (110, 'Accounting', 205, 1700);
INSERT INTO hr.departments VALUES (120, 'Treasury', NULL, 1700);

-- Insert sample data into JOBS
INSERT INTO hr.jobs VALUES ('AD_PRES', 'President', 20000, 40000);
INSERT INTO hr.jobs VALUES ('AD_VP', 'Administration Vice President', 15000, 30000);
INSERT INTO hr.jobs VALUES ('AD_ASST', 'Administration Assistant', 3000, 6000);
INSERT INTO hr.jobs VALUES ('FI_MGR', 'Finance Manager', 8200, 16400);
INSERT INTO hr.jobs VALUES ('FI_ACCOUNT', 'Accountant', 4200, 9000);
INSERT INTO hr.jobs VALUES ('AC_MGR', 'Accounting Manager', 8200, 16400);
INSERT INTO hr.jobs VALUES ('AC_ACCOUNT', 'Public Accountant', 4200, 9000);
INSERT INTO hr.jobs VALUES ('SA_MAN', 'Sales Manager', 10000, 20000);
INSERT INTO hr.jobs VALUES ('SA_REP', 'Sales Representative', 6000, 12000);
INSERT INTO hr.jobs VALUES ('PU_MAN', 'Purchasing Manager', 8000, 15000);
INSERT INTO hr.jobs VALUES ('PU_CLERK', 'Purchasing Clerk', 2500, 5500);
INSERT INTO hr.jobs VALUES ('ST_MAN', 'Stock Manager', 5500, 8500);
INSERT INTO hr.jobs VALUES ('ST_CLERK', 'Stock Clerk', 2000, 5000);
INSERT INTO hr.jobs VALUES ('SH_CLERK', 'Shipping Clerk', 2500, 5500);
INSERT INTO hr.jobs VALUES ('IT_PROG', 'Programmer', 4000, 10000);
INSERT INTO hr.jobs VALUES ('MK_MAN', 'Marketing Manager', 9000, 15000);
INSERT INTO hr.jobs VALUES ('MK_REP', 'Marketing Representative', 4000, 9000);
INSERT INTO hr.jobs VALUES ('HR_REP', 'Human Resources Representative', 4000, 9000);
INSERT INTO hr.jobs VALUES ('PR_REP', 'Public Relations Representative', 4500, 10500);

-- Insert sample data into EMPLOYEES
INSERT INTO hr.employees VALUES (100, 'Steven', 'King', 'sking', '515.123.4567', TO_DATE('17-JUN-1987', 'DD-MON-YYYY'), 'AD_PRES', 24000, NULL, NULL, 90);
INSERT INTO hr.employees VALUES (101, 'Neena', 'Kochhar', 'nkochhar', '515.123.4568', TO_DATE('21-SEP-1989', 'DD-MON-YYYY'), 'AD_VP', 17000, NULL, 100, 90);
INSERT INTO hr.employees VALUES (102, 'Lex', 'De Haan', 'ldehaan', '515.123.4569', TO_DATE('13-JAN-1993', 'DD-MON-YYYY'), 'AD_VP', 17000, NULL, 100, 90);
INSERT INTO hr.employees VALUES (103, 'Alexander', 'Hunold', 'ahunold', '590.423.4567', TO_DATE('03-JAN-1990', 'DD-MON-YYYY'), 'IT_PROG', 9000, NULL, 102, 60);
INSERT INTO hr.employees VALUES (104, 'Bruce', 'Ernst', 'bernst', '590.423.4568', TO_DATE('21-MAY-1991', 'DD-MON-YYYY'), 'IT_PROG', 6000, NULL, 103, 60);
INSERT INTO hr.employees VALUES (105, 'David', 'Austin', 'daustin', '590.423.4569', TO_DATE('25-JUN-1997', 'DD-MON-YYYY'), 'IT_PROG', 4800, NULL, 103, 60);
INSERT INTO hr.employees VALUES (106, 'Valli', 'Pataballa', 'vpatabal', '590.423.4560', TO_DATE('05-FEB-1998', 'DD-MON-YYYY'), 'IT_PROG', 4800, NULL, 103, 60);
INSERT INTO hr.employees VALUES (107, 'Diana', 'Lorentz', 'dlorentz', '590.423.5567', TO_DATE('07-FEB-1999', 'DD-MON-YYYY'), 'IT_PROG', 4200, NULL, 103, 60);
INSERT INTO hr.employees VALUES (108, 'Nancy', 'Greenberg', 'ngreenbe', '515.124.4569', TO_DATE('17-AUG-1994', 'DD-MON-YYYY'), 'FI_MGR', 12000, NULL, 101, 100);
INSERT INTO hr.employees VALUES (109, 'Daniel', 'Faviet', 'dfaviet', '515.124.4169', TO_DATE('16-AUG-1994', 'DD-MON-YYYY'), 'FI_ACCOUNT', 9000, NULL, 108, 100);
INSERT INTO hr.employees VALUES (110, 'John', 'Chen', 'jchen', '515.124.4269', TO_DATE('28-SEP-1997', 'DD-MON-YYYY'), 'FI_ACCOUNT', 8200, NULL, 108, 100);
INSERT INTO hr.employees VALUES (111, 'Ismael', 'Sciarra', 'isciarra', '515.124.4369', TO_DATE('30-SEP-1997', 'DD-MON-YYYY'), 'FI_ACCOUNT', 7700, NULL, 108, 100);
INSERT INTO hr.employees VALUES (112, 'Jose Manuel', 'Urman', 'jmurman', '515.124.4469', TO_DATE('07-MAR-1998', 'DD-MON-YYYY'), 'FI_ACCOUNT', 7800, NULL, 108, 100);
INSERT INTO hr.employees VALUES (113, 'Luis', 'Popp', 'lpopp', '515.124.4567', TO_DATE('07-DEC-1999', 'DD-MON-YYYY'), 'FI_ACCOUNT', 6900, NULL, 108, 100);
INSERT INTO hr.employees VALUES (114, 'Den', 'Raphaely', 'draphaely', '515.127.4561', TO_DATE('07-DEC-1994', 'DD-MON-YYYY'), 'PU_MAN', 11000, NULL, 100, 30);
INSERT INTO hr.employees VALUES (115, 'Alexander', 'Khoo', 'akhoo', '515.127.4562', TO_DATE('18-MAY-1995', 'DD-MON-YYYY'), 'PU_CLERK', 3100, NULL, 114, 30);
INSERT INTO hr.employees VALUES (116, 'Shelli', 'Baida', 'sbaida', '515.127.4563', TO_DATE('24-DEC-1997', 'DD-MON-YYYY'), 'PU_CLERK', 2900, NULL, 114, 30);
INSERT INTO hr.employees VALUES (117, 'Sigal', 'Tobias', 'stobias', '515.127.4564', TO_DATE('24-JUL-1997', 'DD-MON-YYYY'), 'PU_CLERK', 2800, NULL, 114, 30);
INSERT INTO hr.employees VALUES (118, 'Guy', 'Himuro', 'ghimuro', '515.127.4565', TO_DATE('15-NOV-1998', 'DD-MON-YYYY'), 'PU_CLERK', 2600, NULL, 114, 30);
INSERT INTO hr.employees VALUES (119, 'Karen', 'Colmenares', 'kcolmena', '515.127.4566', TO_DATE('10-AUG-1999', 'DD-MON-YYYY'), 'PU_CLERK', 2500, NULL, 114, 30);
INSERT INTO hr.employees VALUES (120, 'Matthew', 'Weiss', 'mweiss', '650.123.1234', TO_DATE('18-JUL-1996', 'DD-MON-YYYY'), 'ST_MAN', 8000, NULL, 100, 50);
INSERT INTO hr.employees VALUES (121, 'Adam', 'Fripp', 'afripp', '650.123.2234', TO_DATE('10-APR-1997', 'DD-MON-YYYY'), 'ST_MAN', 8200, NULL, 100, 50);
INSERT INTO hr.employees VALUES (122, 'Payam', 'Kaufling', 'pkauflin', '650.123.3234', TO_DATE('01-MAY-1995', 'DD-MON-YYYY'), 'ST_MAN', 7900, NULL, 100, 50);
INSERT INTO hr.employees VALUES (123, 'Shanta', 'Vollman', 'svollman', '650.123.4234', TO_DATE('10-OCT-1997', 'DD-MON-YYYY'), 'ST_MAN', 6500, NULL, 100, 50);
INSERT INTO hr.employees VALUES (124, 'Kevin', 'Mourgos', 'kmourgos', '650.123.5234', TO_DATE('16-NOV-1999', 'DD-MON-YYYY'), 'ST_MAN', 5800, NULL, 100, 50);
INSERT INTO hr.employees VALUES (125, 'Julia', 'Nayer', 'jnayer', '650.124.1214', TO_DATE('16-JUL-1997', 'DD-MON-YYYY'), 'ST_CLERK', 3200, NULL, 120, 50);
INSERT INTO hr.employees VALUES (126, 'Irene', 'Mikkilineni', 'imikkili', '650.124.1224', TO_DATE('28-SEP-1998', 'DD-MON-YYYY'), 'ST_CLERK', 2700, NULL, 120, 50);
INSERT INTO hr.employees VALUES (127, 'James', 'Landry', 'jlandry', '650.124.1334', TO_DATE('14-JAN-1999', 'DD-MON-YYYY'), 'ST_CLERK', 2400, NULL, 120, 50);
INSERT INTO hr.employees VALUES (128, 'Steven', 'Markle', 'smarkle', '650.124.1434', TO_DATE('08-MAR-2000', 'DD-MON-YYYY'), 'ST_CLERK', 2200, NULL, 120, 50);
INSERT INTO hr.employees VALUES (129, 'Laura', 'Bissot', 'lbissot', '650.124.1534', TO_DATE('20-AUG-1999', 'DD-MON-YYYY'), 'ST_CLERK', 3300, NULL, 120, 50);
INSERT INTO hr.employees VALUES (130, 'Mozhe', 'Atkinson', 'matkinso', '650.124.6234', TO_DATE('30-OCT-1997', 'DD-MON-YYYY'), 'ST_CLERK', 2800, NULL, 120, 50);
INSERT INTO hr.employees VALUES (131, 'James', 'Marlow', 'jamrlow', '650.124.7234', TO_DATE('16-FEB-1997', 'DD-MON-YYYY'), 'ST_CLERK', 2500, NULL, 120, 50);
INSERT INTO hr.employees VALUES (132, 'TJ', 'Olson', 'tjolson', '650.124.8234', TO_DATE('10-APR-1999', 'DD-MON-YYYY'), 'ST_CLERK', 2100, NULL, 120, 50);
INSERT INTO hr.employees VALUES (133, 'Jason', 'Mallin', 'jmallin', '650.121.2234', TO_DATE('14-JUN-1996', 'DD-MON-YYYY'), 'ST_CLERK', 3300, NULL, 120, 50);
INSERT INTO hr.employees VALUES (134, 'Michael', 'Rogers', 'mrogers', '650.121.1234', TO_DATE('26-AUG-1998', 'DD-MON-YYYY'), 'ST_CLERK', 2900, NULL, 120, 50);
INSERT INTO hr.employees VALUES (135, 'Ki', 'Gee', 'kgee', '650.121.2222', TO_DATE('12-DEC-1999', 'DD-MON-YYYY'), 'ST_CLERK', 2400, NULL, 120, 50);
INSERT INTO hr.employees VALUES (136, 'Hazel', 'Philtanker', 'hphiltan', '650.121.2223', TO_DATE('18-FEB-2000', 'DD-MON-YYYY'), 'ST_CLERK', 2200, NULL, 120, 50);
INSERT INTO hr.employees VALUES (137, 'Renske', 'Ladwig', 'rladwig', '650.121.1234', TO_DATE('14-JUL-1995', 'DD-MON-YYYY'), 'ST_CLERK', 3600, NULL, 120, 50);
INSERT INTO hr.employees VALUES (138, 'Stephen', 'Stiles', 'sstiles', '650.121.2034', TO_DATE('26-OCT-1997', 'DD-MON-YYYY'), 'ST_CLERK', 3200, NULL, 120, 50);
INSERT INTO hr.employees VALUES (139, 'John', 'Seo', 'jseo', '650.121.2019', TO_DATE('12-FEB-1998', 'DD-MON-YYYY'), 'ST_CLERK', 2700, NULL, 120, 50);
INSERT INTO hr.employees VALUES (140, 'Joshua', 'Patel', 'jpatel', '650.121.1834', TO_DATE('06-APR-1998', 'DD-MON-YYYY'), 'ST_CLERK', 2600, NULL, 120, 50);
INSERT INTO hr.employees VALUES (141, 'Trenna', 'Rajs', 'trajs', '650.121.8009', TO_DATE('17-OCT-1995', 'DD-MON-YYYY'), 'SH_CLERK', 3500, NULL, 121, 50);
INSERT INTO hr.employees VALUES (142, 'Curtis', 'Davies', 'cdavies', '650.121.2994', TO_DATE('29-JAN-1997', 'DD-MON-YYYY'), 'SH_CLERK', 3100, NULL, 121, 50);
INSERT INTO hr.employees VALUES (143, 'Randall', 'Matos', 'rmatos', '650.121.2874', TO_DATE('15-MAR-1998', 'DD-MON-YYYY'), 'SH_CLERK', 2600, NULL, 121, 50);
INSERT INTO hr.employees VALUES (144, 'Peter', 'Vargas', 'pvargas', '650.121.2004', TO_DATE('09-JUL-1997', 'DD-MON-YYYY'), 'SH_CLERK', 2500, NULL, 121, 50);
INSERT INTO hr.employees VALUES (145, 'John', 'Russell', 'jrussel', '011.44.1344.429268', TO_DATE('01-OCT-1996', 'DD-MON-YYYY'), 'SA_MAN', 14000, 0.4, 100, 80);
INSERT INTO hr.employees VALUES (146, 'Karen', 'Partners', 'kpartner', '011.44.1344.467268', TO_DATE('05-JAN-1997', 'DD-MON-YYYY'), 'SA_MAN', 13500, 0.3, 100, 80);
INSERT INTO hr.employees VALUES (147, 'Alberto', 'Errazuriz', 'aerrazur', '011.44.1344.429278', TO_DATE('10-MAR-1997', 'DD-MON-YYYY'), 'SA_MAN', 12000, 0.3, 100, 80);
INSERT INTO hr.employees VALUES (148, 'Gerald', 'Cambrault', 'gcambrau', '011.44.1344.619268', TO_DATE('15-OCT-1999', 'DD-MON-YYYY'), 'SA_MAN', 11000, 0.3, 100, 80);
INSERT INTO hr.employees VALUES (149, 'Eleni', 'Zlotkey', 'ezlotkey', '011.44.1344.429018', TO_DATE('29-JAN-2000', 'DD-MON-YYYY'), 'SA_MAN', 10500, 0.2, 100, 80);
INSERT INTO hr.employees VALUES (150, 'Peter', 'Tucker', 'ptucker', '011.44.1344.129268', TO_DATE('30-JAN-1997', 'DD-MON-YYYY'), 'SA_REP', 10000, 0.3, 145, 80);
INSERT INTO hr.employees VALUES (151, 'David', 'Bernstein', 'dbernste', '011.44.1344.345268', TO_DATE('24-MAR-1997', 'DD-MON-YYYY'), 'SA_REP', 9500, 0.25, 145, 80);
INSERT INTO hr.employees VALUES (152, 'Peter', 'Hall', 'phall', '011.44.1344.478968', TO_DATE('20-AUG-1997', 'DD-MON-YYYY'), 'SA_REP', 9000, 0.25, 145, 80);
INSERT INTO hr.employees VALUES (153, 'Christopher', 'Olsen', 'colsen', '011.44.1344.498718', TO_DATE('30-MAR-1998', 'DD-MON-YYYY'), 'SA_REP', 8000, 0.2, 145, 80);
INSERT INTO hr.employees VALUES (154, 'Nanette', 'Cambrault', 'ncambrau', '011.44.1344.987668', TO_DATE('09-DEC-1998', 'DD-MON-YYYY'), 'SA_REP', 7500, 0.2, 145, 80);
INSERT INTO hr.employees VALUES (155, 'Oliver', 'Tuvault', 'otuvault', '011.44.1344.486944', TO_DATE('23-NOV-1999', 'DD-MON-YYYY'), 'SA_REP', 7000, 0.15, 145, 80);
INSERT INTO hr.employees VALUES (156, 'Janette', 'King', 'jking', '011.44.1345.429268', TO_DATE('30-JAN-1996', 'DD-MON-YYYY'), 'SA_REP', 10000, 0.35, 146, 80);
INSERT INTO hr.employees VALUES (157, 'Patrick', 'Sully', 'psully', '011.44.1345.929268', TO_DATE('04-MAR-1997', 'DD-MON-YYYY'), 'SA_REP', 9500, 0.35, 146, 80);
INSERT INTO hr.employees VALUES (158, 'Allan', 'McEwen', 'amcewen', '011.44.1345.829268', TO_DATE('01-AUG-1996', 'DD-MON-YYYY'), 'SA_REP', 9000, 0.35, 146, 80);
INSERT INTO hr.employees VALUES (159, 'Lindsey', 'Smith', 'lsmith', '011.44.1345.729268', TO_DATE('10-MAR-1997', 'DD-MON-YYYY'), 'SA_REP', 8000, 0.3, 146, 80);
INSERT INTO hr.employees VALUES (160, 'Louise', 'Doran', 'ldoran', '011.44.1345.629268', TO_DATE('15-DEC-1997', 'DD-MON-YYYY'), 'SA_REP', 7500, 0.3, 146, 80);
INSERT INTO hr.employees VALUES (161, 'Sarath', 'Sewall', 'ssewall', '011.44.1345.529268', TO_DATE('03-NOV-1998', 'DD-MON-YYYY'), 'SA_REP', 7000, 0.25, 146, 80);
INSERT INTO hr.employees VALUES (162, 'Clara', 'Vishauxo', 'cvishaux', '011.44.1346.129268', TO_DATE('11-NOV-1997', 'DD-MON-YYYY'), 'SA_REP', 10500, 0.25, 147, 80);
INSERT INTO hr.employees VALUES (163, 'Danielle', 'Greene', 'dgreene', '011.44.1346.229268', TO_DATE('19-MAR-1999', 'DD-MON-YYYY'), 'SA_REP', 9500, 0.15, 147, 80);
INSERT INTO hr.employees VALUES (164, 'Mattea', 'Marvins', 'mmarvins', '011.44.1346.329268', TO_DATE('24-JAN-2000', 'DD-MON-YYYY'), 'SA_REP', 7200, 0.1, 147, 80);
INSERT INTO hr.employees VALUES (165, 'David', 'Lee', 'dlee', '011.44.1346.529268', TO_DATE('23-FEB-2000', 'DD-MON-YYYY'), 'SA_REP', 6800, 0.1, 147, 80);
INSERT INTO hr.employees VALUES (166, 'Sundar', 'Ande', 'sande', '011.44.1346.629268', TO_DATE('24-MAR-2000', 'DD-MON-YYYY'), 'SA_REP', 6400, 0.1, 147, 80);
INSERT INTO hr.employees VALUES (167, 'Amit', 'Banda', 'abanda', '011.44.1346.729268', TO_DATE('21-APR-2000', 'DD-MON-YYYY'), 'SA_REP', 6200, 0.1, 147, 80);
INSERT INTO hr.employees VALUES (168, 'Lisa', 'Ozer', 'lozer', '011.44.1343.929268', TO_DATE('11-MAR-1997', 'DD-MON-YYYY'), 'SA_REP', 11500, 0.25, 148, 80);
INSERT INTO hr.employees VALUES (169, 'Harrison', 'Bloom', 'hbloom', '011.44.1343.829268', TO_DATE('23-MAR-1998', 'DD-MON-YYYY'), 'SA_REP', 10000, 0.2, 148, 80);
INSERT INTO hr.employees VALUES (170, 'Tayler', 'Fox', 'tfox', '011.44.1343.729268', TO_DATE('24-JAN-1998', 'DD-MON-YYYY'), 'SA_REP', 9600, 0.2, 148, 80);
INSERT INTO hr.employees VALUES (171, 'William', 'Smith', 'wsmith', '011.44.1343.629268', TO_DATE('23-FEB-1999', 'DD-MON-YYYY'), 'SA_REP', 7400, 0.15, 148, 80);
INSERT INTO hr.employees VALUES (172, 'Elizabeth', 'Bates', 'ebates', '011.44.1343.529268', TO_DATE('24-MAR-1999', 'DD-MON-YYYY'), 'SA_REP', 7300, 0.15, 148, 80);
INSERT INTO hr.employees VALUES (173, 'Sundita', 'Kumar', 'skumar', '011.44.1343.329268', TO_DATE('21-APR-2000', 'DD-MON-YYYY'), 'SA_REP', 6100, 0.1, 148, 80);
INSERT INTO hr.employees VALUES (174, 'Ellen', 'Abel', 'eabel', '011.44.1644.429267', TO_DATE('11-MAY-1996', 'DD-MON-YYYY'), 'SA_REP', 11000, 0.3, 149, 80);
INSERT INTO hr.employees VALUES (175, 'Alyssa', 'Hutton', 'ahutton', '011.44.1644.429266', TO_DATE('19-MAR-1997', 'DD-MON-YYYY'), 'SA_REP', 8800, 0.25, 149, 80);
INSERT INTO hr.employees VALUES (176, 'Jonathon', 'Taylor', 'jtaylor', '011.44.1644.429265', TO_DATE('24-MAR-1998', 'DD-MON-YYYY'), 'SA_REP', 8600, 0.2, 149, 80);
INSERT INTO hr.employees VALUES (177, 'Jack', 'Livingston', 'jlivings', '011.44.1644.429264', TO_DATE('23-APR-1998', 'DD-MON-YYYY'), 'SA_REP', 8400, 0.2, 149, 80);
INSERT INTO hr.employees VALUES (178, 'Kimberlee', 'Martin', 'kmartin', '011.44.1644.429263', TO_DATE('24-MAY-1998', 'DD-MON-YYYY'), 'SA_REP', 7800, 0.15, 149, 80);
INSERT INTO hr.employees VALUES (179, 'Collin', 'Singh', 'csingh', '011.44.1644.429262', TO_DATE('30-JAN-1999', 'DD-MON-YYYY'), 'SA_REP', 6400, 0.1, 149, 80);
INSERT INTO hr.employees VALUES (180, 'Shelley', 'Higgins', 'shiggins', '515.123.8080', TO_DATE('07-JUN-1994', 'DD-MON-YYYY'), 'AC_MGR', 12000, NULL, 101, 110);
INSERT INTO hr.employees VALUES (181, 'William', 'Gietz', 'wgietz', '515.123.8181', TO_DATE('03-JUN-1997', 'DD-MON-YYYY'), 'AC_ACCOUNT', 8300, NULL, 180, 110);
INSERT INTO hr.employees VALUES (200, 'Jennifer', 'Whalen', 'jwhalen', '515.123.4444', TO_DATE('17-SEP-1987', 'DD-MON-YYYY'), 'AD_ASST', 4400, NULL, 101, 10);
INSERT INTO hr.employees VALUES (201, 'Michael', 'Hartstein', 'mhartste', '515.123.5555', TO_DATE('17-FEB-1996', 'DD-MON-YYYY'), 'MK_MAN', 13000, NULL, 100, 20);
INSERT INTO hr.employees VALUES (202, 'Pat', 'Fay', 'pfay', '603.123.6666', TO_DATE('17-AUG-1997', 'DD-MON-YYYY'), 'MK_REP', 6000, NULL, 201, 20);
INSERT INTO hr.employees VALUES (203, 'Susan', 'Mavris', 'smavris', '515.123.7777', TO_DATE('07-JUN-1994', 'DD-MON-YYYY'), 'HR_REP', 6500, NULL, 101, 40);
INSERT INTO hr.employees VALUES (204, 'Hermann', 'Baer', 'hbaer', '515.123.8888', TO_DATE('07-JUN-1994', 'DD-MON-YYYY'), 'PR_REP', 10000, NULL, 101, 70);
INSERT INTO hr.employees VALUES (205, 'Shelley', 'Higgins', 'shiggins', '515.123.8080', TO_DATE('07-JUN-1994', 'DD-MON-YYYY'), 'AC_MGR', 12000, NULL, 101, 110);

-- Create HR Package Specification
CREATE PACKAGE hr.hr_pkg AS
    FUNCTION get_employee_salary(p_employee_id NUMBER) RETURN NUMBER;
    FUNCTION get_department_budget(p_department_id NUMBER) RETURN NUMBER;
    PROCEDURE raise_employee_salary(p_employee_id NUMBER, p_raise_percent NUMBER);
    PROCEDURE hire_employee(p_first_name VARCHAR2, p_last_name VARCHAR2, p_email VARCHAR2, p_job_id VARCHAR2, p_salary NUMBER, p_department_id NUMBER);
    PROCEDURE terminate_employee(p_employee_id NUMBER);
    PROCEDURE update_job_history(p_employee_id NUMBER, p_new_job_id VARCHAR2, p_new_department_id NUMBER);
END hr_pkg;
/

-- Create HR Package Body
CREATE PACKAGE BODY hr.hr_pkg AS

    FUNCTION get_employee_salary(p_employee_id NUMBER) RETURN NUMBER IS
        v_salary NUMBER;
    BEGIN
        SELECT salary INTO v_salary FROM hr.employees WHERE employee_id = p_employee_id;
        RETURN v_salary;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN NULL;
    END get_employee_salary;

    FUNCTION get_department_budget(p_department_id NUMBER) RETURN NUMBER IS
        v_budget NUMBER;
    BEGIN
        SELECT SUM(salary) INTO v_budget FROM hr.employees WHERE department_id = p_department_id;
        RETURN NVL(v_budget, 0);
    END get_department_budget;

    PROCEDURE raise_employee_salary(p_employee_id NUMBER, p_raise_percent NUMBER) IS
    BEGIN
        UPDATE hr.employees 
        SET salary = salary * (1 + p_raise_percent / 100)
        WHERE employee_id = p_employee_id;
        COMMIT;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('Employee ID ' || p_employee_id || ' not found');
    END raise_employee_salary;

    PROCEDURE hire_employee(p_first_name VARCHAR2, p_last_name VARCHAR2, p_email VARCHAR2, p_job_id VARCHAR2, p_salary NUMBER, p_department_id NUMBER) IS
        v_new_emp_id NUMBER;
    BEGIN
        SELECT MAX(employee_id) + 1 INTO v_new_emp_id FROM hr.employees;
        
        INSERT INTO hr.employees (employee_id, first_name, last_name, email, job_id, salary, department_id, hire_date)
        VALUES (v_new_emp_id, p_first_name, p_last_name, p_email, p_job_id, p_salary, p_department_id, SYSDATE);
        
        COMMIT;
        DBMS_OUTPUT.PUT_LINE('Employee hired successfully with ID: ' || v_new_emp_id);
    END hire_employee;

    PROCEDURE terminate_employee(p_employee_id NUMBER) IS
    BEGIN
        -- Delete job history first (foreign key dependency)
        DELETE FROM hr.job_history WHERE employee_id = p_employee_id;
        -- Then delete the employee
        DELETE FROM hr.employees WHERE employee_id = p_employee_id;
        COMMIT;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('Employee ID ' || p_employee_id || ' not found');
    END terminate_employee;

    PROCEDURE update_job_history(p_employee_id NUMBER, p_new_job_id VARCHAR2, p_new_department_id NUMBER) IS
        v_old_job_id VARCHAR2(10);
        v_old_department_id NUMBER;
    BEGIN
        -- Get current job and department
        SELECT job_id, department_id INTO v_old_job_id, v_old_department_id 
        FROM hr.employees WHERE employee_id = p_employee_id;
        
        -- Insert into job history
        INSERT INTO hr.job_history (employee_id, start_date, end_date, job_id, department_id)
        VALUES (p_employee_id, SYSDATE - 1, SYSDATE, v_old_job_id, v_old_department_id);
        
        -- Update employee with new job and department
        UPDATE hr.employees 
        SET job_id = p_new_job_id, department_id = p_new_department_id
        WHERE employee_id = p_employee_id;
        
        COMMIT;
        DBMS_OUTPUT.PUT_LINE('Job history updated for employee ID ' || p_employee_id);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('Employee ID ' || p_employee_id || ' not found');
    END update_job_history;

END hr_pkg;
/

-- Create standalone procedures

-- Procedure to get employee details with OUT parameters
CREATE PROCEDURE hr.get_employee_details(
    p_employee_id IN NUMBER,
    o_first_name OUT VARCHAR2,
    o_last_name OUT VARCHAR2,
    o_email OUT VARCHAR2,
    o_salary OUT NUMBER,
    o_job_id OUT VARCHAR2
) AS
BEGIN
    SELECT first_name, last_name, email, salary, job_id
    INTO o_first_name, o_last_name, o_email, o_salary, o_job_id
    FROM hr.employees
    WHERE employee_id = p_employee_id;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        o_first_name := 'NOT FOUND';
        o_last_name := '';
        o_email := '';
        o_salary := 0;
        o_job_id := '';
END get_employee_details;
/

-- Procedure to get department information with OUT parameters
CREATE PROCEDURE hr.get_department_info(
    p_department_id IN NUMBER,
    o_department_name OUT VARCHAR2,
    o_manager_id OUT NUMBER,
    o_employee_count OUT NUMBER,
    o_total_salary OUT NUMBER
) AS
BEGIN
    SELECT department_name, manager_id
    INTO o_department_name, o_manager_id
    FROM hr.departments
    WHERE department_id = p_department_id;
    
    SELECT COUNT(*), NVL(SUM(salary), 0)
    INTO o_employee_count, o_total_salary
    FROM hr.employees
    WHERE department_id = p_department_id;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        o_department_name := 'NOT FOUND';
        o_manager_id := 0;
        o_employee_count := 0;
        o_total_salary := 0;
END get_department_info;
/

-- Create a function to calculate bonus based on salary
CREATE FUNCTION hr.calculate_bonus(p_salary NUMBER, p_bonus_percent NUMBER) RETURN NUMBER AS
BEGIN
    RETURN ROUND(p_salary * (p_bonus_percent / 100), 2);
END calculate_bonus;
/

-- Commit changes
COMMIT;

-- Display completion message
SELECT 'HR Schema created successfully!' as Status FROM dual;
