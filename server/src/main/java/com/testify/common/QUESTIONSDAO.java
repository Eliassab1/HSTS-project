package com.testify.common;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QUESTIONSDAO {

    private Connection connection;

    public QUESTIONSDAO() throws SQLException {
        // Matches the Singleton pattern from your DatabaseConnection class
        this.connection = DatabaseConnection.getInstance().getConnection();
    }

    public void createQuestionsTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS questions (
            id INT AUTO_INCREMENT PRIMARY KEY,
            question_code VARCHAR(5) NOT NULL UNIQUE,
            course_id INT NOT NULL,
            question_text TEXT NOT NULL,
            option_a TEXT NOT NULL,
            option_b TEXT NOT NULL,
            option_c TEXT NOT NULL,
            option_d TEXT NOT NULL,
            correct_option CHAR(1) NOT NULL,
            image_url VARCHAR(255),
            teacher_id INT NULL,
            difficulty_level VARCHAR(10) NULL,
            topic VARCHAR(100) NULL,
            FOREIGN KEY (course_id) REFERENCES courses(id),
            CHECK (correct_option IN ('A','B','C','D'))
        )
    """;
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(sql);
        ensureImageUrlColumn();
        ensureTeacherIdColumn();
        ensureDifficultyLevelColumn();
        ensureTopicColumn();
    }

    /**
     * Creates the question version-history table. Idempotent, so it is safe
     * on every startup.
     *
     * Editing a question has to leave the previous version in the bank
     * (spec 2.2), but the live row cannot simply be superseded by a new one:
     * both {@code test_questions.question_id} and
     * {@code student_answers.question_id} point at {@code questions.id}, so
     * flipping which row is "current" would silently change what an
     * already-sat exam was asking. Versions therefore live in this side
     * table, which nothing else references.
     *
     * There is deliberately no foreign key back to {@code questions}:
     * archived versions must outlive a deleted question rather than be
     * cascaded away with it.
     *
     * @throws SQLException if the statement fails
     */
    public void createQuestionsHistoryTable() throws SQLException {
        String sql = """
        CREATE TABLE IF NOT EXISTS questions_history (
            history_id INT AUTO_INCREMENT PRIMARY KEY,
            question_id INT NOT NULL,
            question_code VARCHAR(5),
            course_id INT,
            question_text TEXT,
            option_a TEXT,
            option_b TEXT,
            option_c TEXT,
            option_d TEXT,
            correct_option CHAR(1),
            image_url VARCHAR(255),
            teacher_id INT NULL,
            difficulty_level VARCHAR(10) NULL,
            topic VARCHAR(100) NULL,
            archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
        ensureHistoryClassificationColumns();
    }

    /**
     * Adds difficulty_level and topic to a questions_history table created
     * before questions were classified. Without them the archive INSERT ...
     * SELECT in {@link #updateQuestion(Question)} would fail on an existing
     * database. Idempotent: MySQL error 1060 is swallowed.
     */
    private void ensureHistoryClassificationColumns() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE questions_history ADD COLUMN difficulty_level VARCHAR(10) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add difficulty_level column to questions_history: " + e.getMessage());
            }
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE questions_history ADD COLUMN topic VARCHAR(100) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add topic column to questions_history: " + e.getMessage());
            }
        }
    }

    /**
     * The archived earlier versions of one question, newest first.
     *
     * @param questionId the live question whose history is wanted
     * @return prior versions, newest first; empty if it was never edited
     * @throws SQLException if the query fails
     */
    public List<Question> getQuestionHistory(int questionId) throws SQLException {
        // question_id is aliased to id so the rows map through the same
        // mapper as live questions - each one is a past state of that id.
        String sql = """
        SELECT question_id AS id, course_id, question_text,
               option_a, option_b, option_c, option_d,
               correct_option, image_url, teacher_id,
               difficulty_level, topic
        FROM questions_history
        WHERE question_id = ?
        ORDER BY archived_at DESC, history_id DESC
    """;
        List<Question> versions = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, questionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    versions.add(mapRowToQuestion(rs));
                }
            }
        }
        return versions;
    }

    /**
     * Adds the teacher_id column to a questions table that was created
     * before question authorship was tracked. Safe to call on every
     * startup: if the column already exists, MySQL raises error 1060
     * (duplicate column), which is swallowed here.
     */
    private void ensureTeacherIdColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE questions ADD COLUMN teacher_id INT NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add teacher_id column to questions: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the image_url column to a questions table that was created
     * before visual aids were supported. Safe to call on every startup:
     * MySQL error 1060 (duplicate column) is silently ignored.
     */
    private void ensureImageUrlColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE questions ADD COLUMN image_url VARCHAR(255) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add image_url column to questions: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the difficulty_level column to a questions table created before
     * questions were classified. Idempotent: MySQL error 1060 (duplicate
     * column) is swallowed.
     */
    private void ensureDifficultyLevelColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE questions ADD COLUMN difficulty_level VARCHAR(10) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add difficulty_level column to questions: " + e.getMessage());
            }
        }
    }

    /**
     * Adds the topic column to a questions table created before questions
     * were classified. Idempotent: MySQL error 1060 is swallowed.
     */
    private void ensureTopicColumn() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE questions ADD COLUMN topic VARCHAR(100) NULL");
        } catch (SQLException e) {
            if (e.getErrorCode() != 1060) {
                System.err.println("Could not add topic column to questions: " + e.getMessage());
            }
        }
    }

    public void createquestion(int courseId, int questioncode, int questionId, String questiontext, String optionA, String optionB, String optionC, String optionD, char correctanswer) throws SQLException {
        // Perfectly matches your Question.java 9-argument constructor
        Question q = new Question(
                questionId,
                questiontext,
                optionA,
                optionB,
                optionC,
                optionD,
                String.valueOf(correctanswer),
                (String) null,
                courseId
        );
        insertQuestion(q);
    }

    public int insertQuestion(Question q) throws SQLException {
        String sql = """
        INSERT INTO questions
        (question_code, course_id, question_text, option_a, option_b, option_c, option_d, correct_option, image_url, teacher_id, difficulty_level, topic)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;
        PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        String genCode = "Q" + (int)(Math.random() * 9000 + 1000);

        stmt.setString(1, genCode);
        stmt.setInt(2, q.getCourseId());
        stmt.setString(3, q.getQuestionText());
        stmt.setString(4, q.getOptionA());
        stmt.setString(5, q.getOptionB());
        stmt.setString(6, q.getOptionC());
        stmt.setString(7, q.getOptionD());
        stmt.setString(8, q.getCorrectAnswer());
        stmt.setString(9, q.getVisualAidUrl());
        if (q.getTeacherId() > 0) {
            stmt.setInt(10, q.getTeacherId());
        } else {
            stmt.setNull(10, Types.INTEGER);
        }
        stmt.setString(11, q.getDifficultyLevel());
        stmt.setString(12, q.getTopic());
        stmt.executeUpdate();

        ResultSet keys = stmt.getGeneratedKeys();
        if (!keys.next()) {
            throw new SQLException("Insert succeeded but no generated ID was returned.");
        }
        int questionId = keys.getInt(1);

        // Keep teacher_statistics.questions_count in sync so the
        // administrator's teacher-activity report reflects real data.
        if (q.getTeacherId() > 0) {
            String upsert = """
            INSERT INTO teacher_statistics (teacher_id, tests_count, questions_count)
            VALUES (?, 0, 1)
            ON DUPLICATE KEY UPDATE questions_count = questions_count + 1
        """;
            PreparedStatement us = connection.prepareStatement(upsert);
            us.setInt(1, q.getTeacherId());
            us.executeUpdate();
        }

        return questionId;
    }

    public void deleteQuestionById(int id) throws SQLException {
        String sql = "DELETE FROM questions WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        stmt.executeUpdate();
    }

    /**
     * Applies an edit to a question, keeping the pre-edit version in
     * {@code questions_history} (spec 2.2).
     *
     * The archive INSERT and the UPDATE run as one transaction on a
     * connection this method owns, so a version can neither be lost (edit
     * applied, nothing archived) nor orphaned (version archived, edit
     * failed). See {@link DatabaseConnection#openDedicatedConnection()} for
     * why the shared connection is not used here.
     *
     * @param q the question carrying the new values, identified by its id
     * @throws SQLException if either statement fails; the edit is rolled back
     */
    public void updateQuestion(Question q) throws SQLException {

        String archiveSql = """
        INSERT INTO questions_history
        (question_id, question_code, course_id, question_text,
         option_a, option_b, option_c, option_d,
         correct_option, image_url, teacher_id, difficulty_level, topic)
        SELECT id, question_code, course_id, question_text,
               option_a, option_b, option_c, option_d,
               correct_option, image_url, teacher_id, difficulty_level, topic
        FROM questions WHERE id = ?
    """;

        String updateSql = """
        UPDATE questions
        SET course_id = ?, question_text = ?, option_a = ?, option_b = ?,
            option_c = ?, option_d = ?, correct_option = ?, image_url = ?,
            difficulty_level = ?, topic = ?
        WHERE id = ?
    """;

        try (Connection txn = DatabaseConnection.openDedicatedConnection()) {
            txn.setAutoCommit(false);
            try (PreparedStatement archive = txn.prepareStatement(archiveSql);
                 PreparedStatement update = txn.prepareStatement(updateSql)) {

                archive.setInt(1, q.getId());
                archive.executeUpdate();

                update.setInt(1, q.getCourseId());
                update.setString(2, q.getQuestionText());
                update.setString(3, q.getOptionA());
                update.setString(4, q.getOptionB());
                update.setString(5, q.getOptionC());
                update.setString(6, q.getOptionD());
                update.setString(7, q.getCorrectAnswer());
                update.setString(8, q.getVisualAidUrl());
                update.setString(9, q.getDifficultyLevel());
                update.setString(10, q.getTopic());
                update.setInt(11, q.getId());
                update.executeUpdate();

                txn.commit();

            } catch (SQLException failure) {
                txn.rollback();
                throw failure;
            }
        }
    }

    private Question mapRowToQuestion(ResultSet rs) throws SQLException {
        // Perfectly matches your Question.java 9-argument constructor
        Question q = new Question(
                rs.getInt("id"),
                rs.getString("question_text"),
                rs.getString("option_a"),
                rs.getString("option_b"),
                rs.getString("option_c"),
                rs.getString("option_d"),
                rs.getString("correct_option"),
                rs.getString("image_url"),
                rs.getInt("course_id")
        );
        // rs.getInt returns 0 for a SQL NULL teacher_id, which matches
        // Question's "unattributed" sentinel value.
        q.setTeacherId(rs.getInt("teacher_id"));
        q.setDifficultyLevel(rs.getString("difficulty_level"));
        q.setTopic(rs.getString("topic"));
        return q;
    }

    public List<Question> getAllQuestions() throws SQLException {
        String sql = "SELECT * FROM questions ORDER BY id";
        Statement stmt = connection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);
        List<Question> questions = new ArrayList<>();
        while (rs.next()) {
            questions.add(mapRowToQuestion(rs));
        }
        return questions;
    }

    public Question getQuestionById(int id) throws SQLException {
        String sql = "SELECT * FROM questions WHERE id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, id);
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) return mapRowToQuestion(rs);
        return null;
    }

    public List<Question> getQuestionsByCourse(int courseId) throws SQLException {
        String sql = "SELECT * FROM questions WHERE course_id = ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, courseId);
        ResultSet rs = stmt.executeQuery();
        List<Question> questions = new ArrayList<>();
        while (rs.next()) {
            questions.add(mapRowToQuestion(rs));
        }
        return questions;
    }

    public List<Question> getQuestionsPage(int pageNumber) throws SQLException {
        int pageSize = 6;
        int offset = pageNumber * pageSize;
        String sql = "SELECT * FROM questions ORDER BY id LIMIT ? OFFSET ?";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setInt(1, pageSize);
        stmt.setInt(2, offset);
        ResultSet rs = stmt.executeQuery();
        List<Question> questions = new ArrayList<>();
        while (rs.next()) {
            questions.add(mapRowToQuestion(rs));
        }
        return questions;
    }
}
