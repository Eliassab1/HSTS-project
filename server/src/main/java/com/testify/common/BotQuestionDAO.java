package com.testify.common;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * The log of questions students have asked their course bots.
 *
 * Two read paths with deliberately different shapes:
 * {@link #getHistoryForStudent(int, int)} is a student reading their own
 * history (spec 14.2), and {@link #getAnonymousHistoryForBot(int)} is a
 * teacher reading the general history <b>without user identification</b>
 * (spec 14.3).
 */
public class BotQuestionDAO {

    private final Connection connection;

    public BotQuestionDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    /**
     * Creates the question-log table if it is missing. Safe on every startup.
     *
     * @throws SQLException if the statement fails
     */
    public void createBotQuestionsTable() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS bot_questions (
                id            INT AUTO_INCREMENT PRIMARY KEY,
                bot_id        INT NOT NULL,
                student_id    INT NOT NULL,
                question_text TEXT NOT NULL,
                answer_text   TEXT NOT NULL,
                engine_used   VARCHAR(20) NOT NULL,
                asked_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (bot_id)     REFERENCES course_bots(id),
                FOREIGN KEY (student_id) REFERENCES users(id)
            )
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Records one exchange and returns it with its ID and timestamp filled in.
     *
     * @param exchange the question, the answer, and which engine produced it
     * @return the stored row
     * @throws SQLException if the insert fails
     */
    public BotQuestion logQuestion(BotQuestion exchange) throws SQLException {
        String sql = """
            INSERT INTO bot_questions (bot_id, student_id, question_text, answer_text, engine_used)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, exchange.getBotId());
            stmt.setInt(2, exchange.getStudentId());
            stmt.setString(3, exchange.getQuestionText());
            stmt.setString(4, exchange.getAnswerText());
            stmt.setString(5, exchange.getEngineUsed());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return getById(keys.getInt(1));
                }
            }
        }
        throw new SQLException("No generated key returned when logging a bot question.");
    }

    /**
     * One student's own history with one bot, newest first (spec 14.2).
     *
     * @param studentId the asking student, from the session
     * @param botId the bot they are looking at
     * @return their exchanges, newest first
     * @throws SQLException if the query fails
     */
    public List<BotQuestion> getHistoryForStudent(int studentId, int botId) throws SQLException {
        String sql = """
            SELECT id, bot_id, student_id, question_text, answer_text, engine_used, asked_at
            FROM bot_questions
            WHERE student_id = ? AND bot_id = ?
            ORDER BY asked_at DESC, id DESC
        """;
        List<BotQuestion> history = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, studentId);
            stmt.setInt(2, botId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapRow(rs, true));
                }
            }
        }
        return history;
    }

    /**
     * The general question history for one bot, with <b>no identification of
     * who asked</b> (spec 14.3).
     *
     * The SELECT list is the requirement, not the UI. {@code student_id} is
     * not selected and {@code users} is not joined, so the identity never
     * leaves the database — it is not fetched and then hidden on screen,
     * because a hidden column still crosses the wire and still sits in the
     * client's memory where a screenshot or a debugger would find it. The
     * returned rows carry {@code studentId == 0}.
     *
     * One residual exposure is worth stating rather than claiming away: in a
     * small cohort a timestamp can still narrow a question down to a person.
     * That is inherent to a class of six, not a defect in this query.
     *
     * @param botId the bot whose history is wanted
     * @return anonymised exchanges, newest first
     * @throws SQLException if the query fails
     */
    public List<BotQuestion> getAnonymousHistoryForBot(int botId) throws SQLException {
        String sql = """
            SELECT id, bot_id, question_text, answer_text, engine_used, asked_at
            FROM bot_questions
            WHERE bot_id = ?
            ORDER BY asked_at DESC, id DESC
        """;
        List<BotQuestion> history = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, botId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    history.add(mapRow(rs, false));
                }
            }
        }
        return history;
    }

    /** One exchange by ID, used to echo back a freshly logged row. */
    private BotQuestion getById(int id) throws SQLException {
        String sql = """
            SELECT id, bot_id, student_id, question_text, answer_text, engine_used, asked_at
            FROM bot_questions WHERE id = ?
        """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return mapRow(rs, true);
            }
        }
        return null;
    }

    /**
     * Maps one exchange.
     *
     * @param withStudent whether the result set carries {@code student_id} at
     *                    all; false for the anonymised query, where the
     *                    column is absent and reading it would throw
     */
    private BotQuestion mapRow(ResultSet rs, boolean withStudent) throws SQLException {
        BotQuestion q = new BotQuestion();
        q.setId(rs.getInt("id"));
        q.setBotId(rs.getInt("bot_id"));
        q.setStudentId(withStudent ? rs.getInt("student_id") : 0);
        q.setQuestionText(rs.getString("question_text"));
        q.setAnswerText(rs.getString("answer_text"));
        q.setEngineUsed(rs.getString("engine_used"));
        q.setAskedAt(rs.getTimestamp("asked_at") != null
                ? rs.getTimestamp("asked_at").toString()
                : "");
        return q;
    }
}
