package com.testify.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class test_questionsDAO {

    private Connection connection;

    // Fix: Empty constructor with DatabaseConnection
    public test_questionsDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    public void createTestQuestionsTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS test_questions (
            test_id INT NOT NULL,
            question_id INT NOT NULL,
            points_worth DECIMAL(5,2) DEFAULT 1.00,
            PRIMARY KEY (test_id, question_id),
            FOREIGN KEY (test_id) REFERENCES tests(id),
            FOREIGN KEY (question_id) REFERENCES questions(id)
        )
    """;
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(sql);
    }

    public void addQuestionToTest(int test_id, int question_id, double points_worth) throws SQLException {
        String sql = """
        INSERT INTO test_questions (test_id, question_id, points_worth)
        VALUES (?, ?, ?)
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, test_id);
        stmt.setInt(2, question_id);
        stmt.setDouble(3, points_worth);
        stmt.executeUpdate();
    }

    public void toggleTestStatus(int testId) throws SQLException {
        String sql = """
        UPDATE tests SET is_active = NOT is_active WHERE id = ?
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, testId);
        stmt.executeUpdate();
    }

    public void removeQuestionFromTest(int testId, int questionId) throws SQLException {
        String sql = """
        DELETE FROM test_questions WHERE test_id = ? AND question_id = ?
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, testId);
        stmt.setInt(2, questionId);
        stmt.executeUpdate();
    }

    public void updateQuestionPoints(int testId, int questionId, double newPoints) throws SQLException {
        String sql = """
        UPDATE test_questions SET points_worth = ? WHERE test_id = ? AND question_id = ?
    """;
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setDouble(1, newPoints);
        stmt.setInt(2, testId);
        stmt.setInt(3, questionId);
        stmt.executeUpdate();
    }

    public List<TestQuestion> getQuestionsOfTest(int testId) throws SQLException {
        String sql = "SELECT * FROM test_questions WHERE test_id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, testId);
        ResultSet rs = stmt.executeQuery();
        List<TestQuestion> list = new ArrayList<>();

        while(rs.next()) {
            list.add(new TestQuestion(
                    rs.getInt("test_id"),
                    rs.getInt("question_id"),
                    rs.getDouble("points_worth")
            ));
        }
        return list;
    }

    public void removeAllQuestionsFromTest(int testId) throws SQLException {
        String sql = "DELETE FROM test_questions WHERE test_id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, testId);
        stmt.executeUpdate();
    }

    public boolean isTestValid(int testId) throws SQLException {
        String sql = "SELECT SUM(points_worth) FROM test_questions WHERE test_id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, testId);
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
            double totalPoints = rs.getDouble(1);
            return totalPoints <= 100;
        }
        return false;
    }
}
