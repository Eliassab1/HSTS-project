package com.testify.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class student_answersDAO {

    private Connection connection;

    public student_answersDAO(Connection connection) {
        this.connection = connection;
    }

    public void createStudentAnswersTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS student_answers (
                id INT AUTO_INCREMENT PRIMARY KEY,
                submission_id INT NOT NULL,
                question_id INT NOT NULL,
                student_answer VARCHAR(1) NOT NULL,
                FOREIGN KEY (submission_id) REFERENCES test_submissions(id),
                FOREIGN KEY (question_id) REFERENCES questions(id)
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    public void insertAnswer(int submissionId, int questionId, String studentAnswer) throws SQLException {
        String sql = """
            INSERT INTO student_answers (submission_id, question_id, student_answer)
            VALUES (?, ?, ?)
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, submissionId);
            stmt.setInt(2, questionId);
            stmt.setString(3, studentAnswer);
            stmt.executeUpdate();
        }
    }

    /**
     * Loads every answer a student gave in one submission — the read side of
     * this table, used to build the checked exam form the student reviews.
     *
     * @param submissionId identifier of the submission
     * @return the answers, never null
     * @throws SQLException if the query fails
     */
    public List<StudentAnswer> getAnswersForSubmission(int submissionId) throws SQLException {
        String sql = """
            SELECT question_id, student_answer
            FROM student_answers
            WHERE submission_id = ?
            ORDER BY id
        """;
        List<StudentAnswer> answers = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, submissionId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                answers.add(new StudentAnswer(
                        rs.getInt("question_id"),
                        rs.getString("student_answer")
                ));
            }
        }
        return answers;
    }
}
