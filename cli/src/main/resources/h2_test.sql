-- H2 Test Script - Creates a sample procedure that behaves like Oracle
-- This script creates a test procedure that can be called with the CLI

-- Create a test table
CREATE TABLE IF NOT EXISTS test_data (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    value DECIMAL(10,2)
);

-- Insert some test data
INSERT INTO test_data (id, name, value) VALUES (1, 'Test Item 1', 100.50);
INSERT INTO test_data (id, name, value) VALUES (2, 'Test Item 2', 200.75);

-- Create a procedure that behaves like Oracle
-- H2 supports Java stored procedures
CREATE ALIAS IF NOT EXISTS TEST_PROC AS 'String testProc(String inputName, Integer inputValue) { return "Processed: " + inputName + " with value " + inputValue; }';

-- Create another procedure that returns multiple values
CREATE ALIAS IF NOT EXISTS GET_DATA AS 'ResultSet getData(Integer id) throws SQLException { Connection conn = DriverManager.getConnection("jdbc:h2:mem:testdb"); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM test_data WHERE id = ?"); stmt.setInt(1, id); return stmt.executeQuery(); }';

-- Create a procedure that accepts and returns parameters like Oracle
CREATE ALIAS IF NOT EXISTS CALCULATE AS 'Double calculate(Double input1, Double input2, String operation) { switch (operation.toLowerCase()) { case "add": return input1 + input2; case "subtract": return input1 - input2; case "multiply": return input1 * input2; case "divide": return input2 != 0 ? input1 / input2 : null; default: return null; } }'; 