package com.testify.common;

import java.sql.*;

/**
 * DAO for the `images` table.
 *
 * Not wired into the rest of the app yet — questions currently store their
 * image URL directly in questions.image_url (see Question.visualAidUrl) — but
 * createTableIfNotExists() is called at server startup so the table exists
 * once something starts using it.
 */
public class ImagesDAO {

    private Connection connection;

    public ImagesDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    public void createTableIfNotExists() throws SQLException {
        // No FK to questions(id) here on purpose: this table is created before
        // the questions table during startup init, and MySQL rejects a FK to a
        // table that doesn't exist yet.
        String sql = """
        CREATE TABLE IF NOT EXISTS images (
            id INT AUTO_INCREMENT PRIMARY KEY,
            image_url VARCHAR(255) NOT NULL,
            question_id INT NULL,
            uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """;
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(sql);
    }

    public int insertImage(String imageUrl, Integer questionId) throws SQLException {
        String sql = "INSERT INTO images (image_url, question_id) VALUES (?, ?)";
        PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        stmt.setString(1, imageUrl);
        if (questionId != null) {
            stmt.setInt(2, questionId);
        } else {
            stmt.setNull(2, Types.INTEGER);
        }
        stmt.executeUpdate();

        ResultSet keys = stmt.getGeneratedKeys();
        if (keys.next()) {
            return keys.getInt(1);
        }
        throw new SQLException("Insert succeeded but no generated ID was returned.");
    }

    public void deleteImageById(int id) throws SQLException {
        String sql = "DELETE FROM images WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }
}
